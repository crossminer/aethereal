package nl.cwi.swat.aethereal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.collect.Table;

import nl.cwi.swat.aethereal.rascal.RascalM3;

public class MavenDataset {
	private String coordinates;
	private String datasetPath;
	private MavenCollector collector;
	private AetherDownloader downloader;
	private RascalM3 m3 = new RascalM3();

	Set<String> clients = new HashSet<String>();
	private List<Artifact> libraries = new ArrayList<>();
	private Set<String> unversionedClients = new HashSet<>();
	private Multimap<Artifact, Artifact> links = ArrayListMultimap.create();
	private Table<Artifact, String, String> versionMatrix = HashBasedTable.create();
	private static final Logger logger = LogManager.getLogger(MavenDataset.class);
	private List<MigrationInfo> candidates;
	public MavenDataset(String coordinates, String path, MavenCollector collector, AetherDownloader downloader) {
		this.coordinates = coordinates;
		this.collector = collector;
		this.datasetPath = path;
		this.downloader = downloader;
	}

	public void build() throws IOException {
		logger.info("Building dataset for {}", coordinates);

		logger.info("Creating output folder {}", datasetPath);
		Files.createDirectories(Paths.get(datasetPath));

		libraries = collector.collectAvailableVersions(coordinates);
		logger.info("Found {} versions", libraries.size());

		links = collector.collectClientsOf(coordinates);
		unversionedClients = links.values().stream().map(Aether::toUnversionedCoordinates).collect(Collectors.toSet());
		logger.info("Found {} clients for all versions", links.size());

		versionMatrix = computeVersionMatrix();
		logger.info("Computed the plugin version matrix");
	}
	public void build(String v1, String v2) throws IOException {
		logger.info("Building dataset for {}", coordinates);

		logger.info("Creating output folder {}", datasetPath);
		Files.createDirectories(Paths.get(datasetPath));

		if(collector.checkArtifact(String.format("%s:%s", coordinates, v1)))
			libraries.add(new DefaultArtifact(String.format("%s:%s", coordinates,v1)));
		if(collector.checkArtifact(String.format("%s:%s", coordinates, v2)))
			libraries.add(new DefaultArtifact(String.format("%s:%s", coordinates,v2)));
		logger.info("Found {} versions", libraries.size());

		links = collector.collectClientsOf(coordinates, v1, v2);
		unversionedClients = links.values().stream().map(Aether::toUnversionedCoordinates).collect(Collectors.toSet());
		logger.info("Found {} clients for all versions", links.size());


		versionMatrix = computeVersionMatrix();
		logger.info("Computed the plugin version matrix");
	}

	public void printStats() {
		logger.info("For library {}", coordinates);
		logger.info("Number of versions: {} [min: {}, max: {}]", libraries.size(), libraries.get(0).getVersion(),
				libraries.get(libraries.size() - 1).getVersion());

		Set<Artifact> clients = links.values().stream().collect(Collectors.toSet());
		logger.info("Number of clients: {}", clients.size());
		logger.info("Number of links: {}", links.size());

		List<Artifact> orphans = libraries.stream().filter(a -> links.get(a).isEmpty()).collect(Collectors.toList());
		logger.info("Orphan libraries: {} [{}]", orphans.size(), orphans);

		int cells = libraries.size() * clients.size();
		int taken = links.size();
		logger.info("Matrix density: {}", (double) taken / cells);

		double avg = libraries.stream().mapToInt(a -> links.get(a).size()).average().getAsDouble();
		int min = libraries.stream().mapToInt(a -> links.get(a).size()).min().getAsInt();
		int max = libraries.stream().mapToInt(a -> links.get(a).size()).max().getAsInt();
		logger.info("Clients per library: [avg: {}, min: {}, max: {}]", avg, min, max);

		candidates = computeMigratedVersions();
		logger.info("5 most migrated libraries:");
		candidates.stream().limit(5)
				.forEach(c -> logger.info("{} migrations between {} and {}", c.count, c.libv1, c.libv2));
	}

	public List<MigrationInfo> computeMigratedVersions() {
		List<Set<String>> loaded = new ArrayList<>();
		for (Artifact library : libraries) {
			Set<String> clients = new HashSet<>();
			for (String client : unversionedClients)
				if (versionMatrix.get(library, client) != null)
					clients.add(client);
			loaded.add(clients);
		}

		List<MigrationInfo> candidates = new ArrayList<>();
		for (int i = 0; i < loaded.size() - 1; i++) {
			for (int j = i + 1; j < loaded.size(); j++) {
				SetView<String> intersection = Sets.intersection(loaded.get(i), loaded.get(j));
				
				int currentVal = 0;
				List<Artifact> downloadV1 = new ArrayList<Artifact>();
				List<Artifact> downloadV2 = new ArrayList<Artifact>();
				for (String client : intersection) {
					if (!versionMatrix.get(libraries.get(i), client)
							.equals(versionMatrix.get(libraries.get(j), client))){

						downloadV1.add(new DefaultArtifact(client + ":" + versionMatrix.get(libraries.get(i), client)));
						downloadV2.add(new DefaultArtifact(client + ":" + versionMatrix.get(libraries.get(j), client)));
						currentVal++;
					}
				}
				
				candidates.add(new MigrationInfo(libraries.get(i),
						libraries.get(j), currentVal, downloadV1, downloadV2));
			}
		}
		candidates.sort((m1, m2) -> m2.count.compareTo(m1.count));
		return candidates;
	}

	public void writeVersionMatrix() {
		if (versionMatrix == null)
			versionMatrix = computeVersionMatrix();

		Path path = Paths.get(datasetPath + "/versionMatrix.csv");
		try (BufferedWriter writer = Files.newBufferedWriter(path)) {
			// Header in the form ", client1, client2, ..."
			writer.write(",");
			writer.write(unversionedClients.stream().collect(Collectors.joining(",")));
			writer.newLine();

			// Rows in the form "libversion, client1version, client2version, ..."
			for (Artifact library : libraries) {
				writer.write(library.getVersion() + ",");
				writer.write(unversionedClients.stream()
						.map(c -> versionMatrix.contains(library, c) ? versionMatrix.get(library, c) : "")
						.collect(Collectors.joining(",")));
				writer.newLine();
			}
		} catch (IOException e) {
			logger.error("Couldn't write version matrix", e);
		}
	}

	public void download() {
		// Download libraries
		MigrationInfo migrationInfo = candidates.get(0);
		downloader.downloadArtifactTo(migrationInfo.libv1, datasetPath + "/libraries");
		downloader.downloadArtifactTo(migrationInfo.libv2, datasetPath + "/libraries");
		// Download clients
		downloader.downloadAllArtifactsTo(migrationInfo.clientsV1, datasetPath + "/clients");
		downloader.downloadAllArtifactsTo(migrationInfo.clientsV2, datasetPath + "/clients");

		// Serialize a simple CSV with links between libraries and clients
		Path csv = Paths.get(datasetPath + "/links.csv");
		try (BufferedWriter writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			for (Artifact library : links.keySet())
				for (Artifact client : links.get(library))
					writer.write(String.format("%s,%s%n", library, client));
		} catch (IOException e) {
			logger.error("Couldn't write CSV", e);
		}
	}
	public void downloadAll() {
		// Download libraries
		downloader.downloadAllArtifactsTo(libraries, datasetPath + "/libraries");

		// Download clients
		downloader.downloadAllArtifactsTo(links.values(), datasetPath + "/clients");

		// Serialize a simple CSV with links between libraries and clients
		Path csv = Paths.get(datasetPath + "/links.csv");
		try (BufferedWriter writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
			for (Artifact library : links.keySet())
				for (Artifact client : links.get(library))
					writer.write(String.format("%s,%s%n", library, client));
		} catch (IOException e) {
			logger.error("Couldn't write CSV", e);
		}
	}

	private Table<Artifact, String, String> computeVersionMatrix() {
		Table<Artifact, String, String> result = HashBasedTable.create();

		for (Artifact library : libraries) {
			for (Artifact client : links.get(library)) {
				String unversionedClient = Aether.toUnversionedCoordinates(client);
				result.put(library, unversionedClient, client.getVersion());
			}
		}

		return result;
	}


	public void writeM3s() {
		try (Stream<Path> paths = Files.walk(Paths.get(datasetPath))) {
			paths.filter(p -> p.toFile().isFile() && p.toString().endsWith(".jar")).forEach(p -> {
				try {
					String jar = p.toAbsolutePath().toString();
					String dest = p.toAbsolutePath().toString() + ".m3";

					logger.info("Building M3 model for {}", jar);
					m3.writeM3ForJarFile(jar, dest);
				} catch (IOException e) {
					logger.error(e);
				}
			});
		} catch (IOException e) {
			logger.error(e);
		}
	}


	// Whatever..
	final class MigrationInfo {
		public Artifact libv1;
		public Artifact libv2;
		public Integer count;
		public List<Artifact> clientsV1;
		public List<Artifact> clientsV2;

		MigrationInfo(Artifact l1, Artifact l2, int c, List<Artifact> clientsV1, List<Artifact> clientsV2) {
			libv1 = l1;
			libv2 = l2;
			count = c;
			this.clientsV1 = clientsV1;
			this.clientsV2 = clientsV2;
		}

		@Override
		public String toString() {
			return String.format("[%s] %s -> %s", count, libv1.getArtifactId(), libv2.getArtifactId());
		}
	}

}
