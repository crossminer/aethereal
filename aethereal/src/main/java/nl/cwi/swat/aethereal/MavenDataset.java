package nl.cwi.swat.aethereal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

import io.usethesource.vallang.IValue;
import nl.cwi.swat.aethereal.rascal.RascalM3;

public class MavenDataset {
	private String coordinates;
	private String datasetPath;
	private MavenCollector collector;
	private AetherDownloader downloader;
	private RascalM3 m3 = new RascalM3();

	Set<String> clients = new HashSet<>();
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
	public MavenDataset(String path, MavenCollector collector) {
		this.collector = collector;
		this.datasetPath = path;
	}
	
	public List<MigrationInfo> getVersions(String libV1, String libV2){
		DefaultArtifact libv1 = new DefaultArtifact(libV1);
		DefaultArtifact libv2 = new DefaultArtifact(libV2);
		libraries.add(libv1);
		libraries.add(libv2);
		collector.collectClientsOf(libv1).forEach(z -> links.put(libv1, z));
		collector.collectClientsOf(libv2).forEach(z -> links.put(libv2, z));
		unversionedClients = links.values().stream().map(Aether::toUnversionedCoordinates).collect(Collectors.toSet());
		versionMatrix = computeVersionMatrix();
		return computeMigratedVersions();
		
	}
	public List<MigrationInfo> getVersions(String libV1){
		libraries = collector.collectAvailableVersions(libV1);
		for (Artifact artifact : libraries) {
			links.putAll(artifact, collector.collectClientsOf(artifact));
		}
		unversionedClients = links.values().stream().map(Aether::toUnversionedCoordinates).collect(Collectors.toSet());
		versionMatrix = computeVersionMatrix();
		candidates = computeMigratedVersions();
		return candidates;
	}
	public List<MigrationInfo> getVersions(String libV1, int n){
		libraries = collector.collectAvailableVersions(libV1);
		for (Artifact artifact : libraries) {
			links.putAll(artifact, collector.collectClientsOf(artifact));
		}
		unversionedClients = links.values().stream().map(Aether::toUnversionedCoordinates).collect(Collectors.toSet());
		versionMatrix = computeVersionMatrix();
		candidates = computeMigratedVersions();
		List<MigrationInfo> sortedList = candidates.stream()
					.sorted(Comparator.comparingInt(MigrationInfo::getCount))
					.limit(20)
					.collect(Collectors.toList());
		return sortedList;
	}
	public List<Artifact> getClients(String libV1) {
		DefaultArtifact libv1 = new DefaultArtifact(libV1);
		return collector.collectClientsOf(libv1);
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
		candidates = computeMigratedVersions();
		writeVersionMatrix();
		logger.info("Computed the plugin version matrix");
	}

	public void build(String v1, String v2) throws IOException {
		logger.info("Building dataset for {}", coordinates);

		logger.info("Creating output folder {}", datasetPath);
		Files.createDirectories(Paths.get(datasetPath));

		if (collector.checkArtifact(String.format("%s:%s", coordinates, v1)))
			libraries.add(new DefaultArtifact(String.format("%s:%s", coordinates, v1)));
		if (collector.checkArtifact(String.format("%s:%s", coordinates, v2)))
			libraries.add(new DefaultArtifact(String.format("%s:%s", coordinates, v2)));
		logger.info("Found {} versions", libraries.size());

		collector.collectClientsOf(libraries.get(0)).forEach(z -> links.put(libraries.get(0), z));
		collector.collectClientsOf(libraries.get(1)).forEach(z -> links.put(libraries.get(1), z));
		unversionedClients = links.values().stream().map(Aether::toUnversionedCoordinates).collect(Collectors.toSet());

		logger.info("Found {} clients for all versions", links.size());
		versionMatrix = computeVersionMatrix();
		candidates = computeMigratedVersions();
		writeVersionMatrix();
		logger.info("Computed the plugin version matrix");
	}

	public void build(String v1) throws IOException {
		logger.info("Building dataset for {}", coordinates);

		logger.info("Creating output folder {}", datasetPath);
		Files.createDirectories(Paths.get(datasetPath));

		if (collector.checkArtifact(String.format("%s:%s", coordinates, v1)))
			libraries.add(new DefaultArtifact(String.format("%s:%s", coordinates, v1)));

		logger.info("Found {} versions", libraries.size());
		collector.collectClientsOf(libraries.get(0)).forEach(z -> links.put(libraries.get(0), z));
		unversionedClients = links.values().stream().map(Aether::toUnversionedCoordinates).collect(Collectors.toSet());
		logger.info("Found {} clients for all versions", links.size());
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
		Map<Artifact,Integer> artifactUsage = links.keys().stream().collect(Collectors.toSet()).stream().collect(Collectors.toMap(z -> z, z -> links.get(z).size()));
		//.map(z -> links.get(z).stream().filter(k -> k !=null).count());
		Map<Artifact,Integer> orderedSrtifactUsage = artifactUsage.entrySet()
		        .stream()
		        .sorted(Map.Entry.<Artifact,Integer>comparingByValue().reversed())
		        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		orderedSrtifactUsage.entrySet().forEach(z -> logger.info("{} occurs {} times",z.getKey().toString(),z.getValue()));

		if (candidates != null) {
			logger.info("Migrated libraries:");
			candidates.stream().limit(5)
					.forEach(c -> logger.info("{} migrations between {} and {}", c.count, c.libv1, c.libv2));

		}
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
				List<Artifact> downloadV1 = new ArrayList<>();
				List<Artifact> downloadV2 = new ArrayList<>();
				for (String client : intersection) {
					if (!versionMatrix.get(libraries.get(i), client)
							.equals(versionMatrix.get(libraries.get(j), client))) {
						downloadV1.add(new DefaultArtifact(
								String.format("%s:%s", client, versionMatrix.get(libraries.get(i), client))));
						downloadV2.add(new DefaultArtifact(
								String.format("%s:%s", client, versionMatrix.get(libraries.get(j), client))));
						currentVal++;
					}
				}

				candidates
						.add(new MigrationInfo(libraries.get(i), libraries.get(j), currentVal, downloadV1, downloadV2));
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
		downloader.downloadArtifactTo(candidates.get(0).libv1, datasetPath + "/libraries");
		downloader.downloadArtifactTo(candidates.get(0).libv2, datasetPath + "/libraries");
		// Download clients
		downloader.downloadAllArtifactsTo(candidates.get(0).clientsV1, Paths.get(datasetPath, candidates.get(0).libv1.getArtifactId() + candidates.get(0).libv1.getVersion()).toString());
		downloader.downloadAllArtifactsTo(candidates.get(0).clientsV2, Paths.get(datasetPath, candidates.get(0).libv1.getArtifactId() + candidates.get(0).libv2.getVersion()).toString());

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
		logger.info("#Candidates: {}", candidates.size());
		for (MigrationInfo artifact : candidates) {
			logger.info("Download clients of {} {}", artifact.libv1.getVersion(),  artifact.libv2.getVersion());
			downloader.downloadAllArtifactsTo(artifact.clientsV1, Paths.get(datasetPath, artifact.libv1.getArtifactId() + artifact.libv1.getVersion()).toString());
			downloader.downloadAllArtifactsTo(artifact.clientsV2, Paths.get(datasetPath, artifact.libv1.getArtifactId() + artifact.libv2.getVersion()).toString());
			
		}
		// Download clients
		

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
		AtomicInteger count = new AtomicInteger(0);

		try (Stream<Path> paths = Files.walk(Paths.get(datasetPath))) {
			paths.filter(p -> p.toFile().isFile() && p.toString().endsWith(".jar")).forEach(p -> {
				String jar = p.toAbsolutePath().toString();
				String dest = p.toAbsolutePath().toString() + ".m3";
				logger.info("Building {} M3 model for {}", count.incrementAndGet(), jar);

				IValue m3model = null;
				try {
					if (Paths.get(jar + ".m3").toFile().exists())
						m3model = m3.deserializeM3(jar);
					else {
						m3model = m3.createM3FromJarFile(jar);
						m3.writeM3ModelFile(m3model, dest);
					}
				} catch (IOException e) {
					logger.error(e);
				}

				writeFocusFiles(p, m3model);
			});
		} catch (IOException e) {
			logger.error(e);
		}
	}

	private void writeFocusFiles(Path p, IValue m3model) {
		String focus = p.toAbsolutePath().toString() + ".focus";
		String mdFocus = p.toAbsolutePath().toString() + ".md.focus";
		Multimap<String, String> md_mi = m3.extractMethodInvocations(m3model);
		Set<String> KOI = Sets.newHashSet();
		md_mi.keys().forEach(key -> {
			if (md_mi.get(key).stream().filter(z -> !md_mi.containsKey(z)).count() > 8)
				KOI.add(key);
		});
		Path focusFile = Paths.get(focus);
		Path mdFocusFile = Paths.get(mdFocus);
		try (BufferedWriter writerMD = Files.newBufferedWriter(mdFocusFile);
				BufferedWriter writer = Files.newBufferedWriter(focusFile);) {
			for (String md : KOI) {
				writerMD.write(
						md.replace("|", "").replace("java+constructor:///", "").replace("java+method:///", "") + "\n");
				for (String mi : md_mi.get(md)) {

					writer.write(String.format("%s#%s%n",
							md.replace("|", "").replace("java+constructor:///", "").replace("java+method:///", ""),
							mi.replace("|", "").replace("java+constructor:///", "").replace("java+method:///", "")));
				}
			}
		} catch (IOException e) {
			logger.error(e);
		}
	}


	public void downloadLibrary() {
		downloader.downloadArtifactTo(libraries.get(0), datasetPath + "/libraries");
		downloader.downloadAllArtifactsTo(links.get(libraries.get(0)), datasetPath + "/clients");
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
}
