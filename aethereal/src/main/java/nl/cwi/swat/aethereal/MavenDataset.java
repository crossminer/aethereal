package nl.cwi.swat.aethereal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.artifact.Artifact;

import com.google.common.collect.Multimap;

import nl.cwi.swat.aethereal.rascal.RascalM3;

public class MavenDataset {
	private String coordinates;
	private String downloadPath;
	private MavenCollector collector;
	private AetherDownloader downloader = new AetherDownloader();
	private RascalM3 m3 = new RascalM3();
	private List<Artifact> libraries;
	private Multimap<Artifact, Artifact> links;

	private static final Logger logger = LogManager.getLogger(MavenDataset.class);

	public MavenDataset(String coordinates, MavenCollector collector, String path) {
		this.coordinates = coordinates;
		this.collector = collector;
		this.downloadPath = path;
	}

	public void build() {
		logger.info("Building dataset for {}", coordinates);

		libraries = collector.collectAvailableVersions(coordinates);
		logger.info("Found {} versions", libraries.size());

		links = collector.collectClientsOf(coordinates);
		logger.info("Found {} clients for all versions", links.size());
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
	}

	public void download() {
		// Download libraries
		downloader.downloadAllArtifactsTo(libraries, downloadPath + "/libraries");

		// Download clients
		downloader.downloadAllArtifactsTo(links.values(), downloadPath + "/clients");

		// Serialize a simple csv with links between libraries and clients
		Path csv = Paths.get(downloadPath + "/links.csv");
		try {
			Files.createDirectories(csv.getParent());

			try (BufferedWriter writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
				for (Artifact library : links.keySet())
					for (Artifact client : links.get(library))
						writer.write(String.format("%s,%s%n", library, client));
			} catch (IOException e) {
				logger.error("Couldn't write CSV", e);
			}
		} catch (IOException e) {
			logger.error(e);
		}
	}

	public void writeM3s() {
		try (Stream<Path> paths = Files.walk(Paths.get(downloadPath))) {
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
