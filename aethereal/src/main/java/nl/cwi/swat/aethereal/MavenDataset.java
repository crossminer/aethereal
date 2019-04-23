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
	private AetherDownloader downloader = new AetherDownloader();
	private RascalM3 m3 = new RascalM3();

	private List<Artifact> libraries = new ArrayList<>();
	private Set<String> unversionedClients = new HashSet<>();
	private Multimap<Artifact, Artifact> links = ArrayListMultimap.create();
	private Table<Artifact, String, String> versionMatrix;
	private static final Logger logger = LogManager.getLogger(MavenDataset.class);

	public MavenDataset(String coordinates, MavenCollector collector, String path) {
		this.coordinates = coordinates;
		this.collector = collector;
		this.datasetPath = path;
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

		printMostMigratedVersions();
	}

	private void printMostMigratedVersions() {
		List<Set<String>> loaded = new ArrayList<>();
		for (Artifact library : libraries) {
			Set<String> s = new HashSet<>();
			for (String client : unversionedClients)
				if (versionMatrix.get(library, client) != null)
					s.add(client);
			loaded.add(s);
		}

		int value = 0;
		String v1 = "";
		String v2 = "";
		for (int i = 0; i < loaded.size(); i++)
			for (int j = i + 1; j < loaded.size() - 1; j++) {
				SetView<String> intersection = Sets.intersection(loaded.get(i), loaded.get(j));
				int currentVal = 0;

				for (String client : intersection) {
					if (!versionMatrix.get(libraries.get(i), client)
							.equals(versionMatrix.get(libraries.get(j), client)))
						currentVal++;
				}

				if (currentVal > value) {
					value = currentVal;
					v1 = Aether.toCoordinates(libraries.get(i));
					v2 = Aether.toCoordinates(libraries.get(j));
				}
			}
		logger.info("{} migrations between {} and {}", value, v1, v2);
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

}
