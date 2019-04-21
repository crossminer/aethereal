package nl.cwi.swat.aethereal;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.artifact.Artifact;

import com.google.common.collect.Multimap;

public class MavenDataset {
	private String coordinates;
	private LocalCollector collector = new LocalCollector();
	private List<Artifact> libraries;
	private Multimap<Artifact, Artifact> links;

	private static final Logger logger = LogManager.getLogger(MavenDataset.class);

	public MavenDataset(String coordinates) {
		this.coordinates = coordinates;
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

		List<Artifact> orphans = libraries.stream().filter(a -> links.get(a).size() == 0).collect(Collectors.toList());
		logger.info("Orphan libraries: {} [{}]", orphans.size(), orphans);

		int cells = libraries.size() * clients.size();
		int taken = links.size();
		logger.info("Matrix density: {}", (double) taken / cells);

		double avg = libraries.stream().mapToInt(a -> links.get(a).size()).average().getAsDouble();
		int min = libraries.stream().mapToInt(a -> links.get(a).size()).min().getAsInt();
		int max = libraries.stream().mapToInt(a -> links.get(a).size()).max().getAsInt();
		logger.info("Clients per library: [avg: {}, min: {}, max: {}]", avg, min, max);
	}

	public static void main(String[] args) {
		MavenDataset dt = new MavenDataset("org.sonarsource.sonarqube:sonar-plugin-api");
		dt.build();
		dt.printStats();
	}
}
