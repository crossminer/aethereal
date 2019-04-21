package nl.cwi.swat.aethereal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * The Aether collector relies on Eclipse's Aether to gather information
 * remotely from Maven Central directly.
 */
public class AetherCollector implements MavenCollector {
	private RepositorySystem system;
	private RepositorySystemSession session;
	private RemoteRepository repository;

	private final String MVN_REPOSITORY_USAGE_PAGE = "https://mvnrepository.com/artifact/%s/%s/%s/usages?p=%d";

	private static final Logger logger = LogManager.getLogger(AetherCollector.class);

	public AetherCollector() {
		system = Aether.newRepositorySystem();
		session = Aether.newSession(system);
		repository = Aether.newRemoteRepository();
	}

	@Override
	public List<Artifact> collectAvailableVersions(String coordinates) {
		VersionRangeRequest rangeRequest = new VersionRangeRequest();
		rangeRequest.setArtifact(new DefaultArtifact(coordinates + ":[0,)"));
		rangeRequest.addRepository(repository);

		try {
			VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest);
			return rangeResult.getVersions().stream().map(v -> new DefaultArtifact(coordinates + ":" + v))
					.collect(Collectors.toList());
		} catch (VersionRangeResolutionException e) {
			logger.error("Couldn't resolve version range", e);
			return null;
		}
	}

	@Override
	public List<Artifact> collectClientsOf(Artifact artifact) {
		// Don't rely too much on those results :)
		List<Artifact> res = new ArrayList<>();

		// Don't pollute the local repo with all the POMs we'll download, use a
		// temporary one and delete it after
		RepositorySystemSession tmpSession = Aether.newSession(system, "tmp-repo");

		try {
			// First, scrap usage info from mvnrepository.com (version not included)
			logger.info("Scrapping HTML usage pages from mvnrepository.com...");
			List<String> parsed = parseUsagePage(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
					1);

			// Then, look for available versions via Aether
			for (String coordinates : parsed) {
				logger.info("Looking for matching client versions for {}", coordinates);
				List<Artifact> allVersions = collectAvailableVersions(coordinates);

				// For every version, lookup its direct dependencies and find a match
				for (Artifact client : allVersions) {
					ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
					descriptorRequest.setArtifact(client);
					descriptorRequest.addRepository(repository);

					ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(tmpSession,
							descriptorRequest);

					// If 'client' has 'artifact' as a direct dependency, we have a match
					for (Dependency dependency : descriptorResult.getDependencies()) {
						if (artifact.toString().equals(dependency.getArtifact().toString())) {
							res.add(client);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error collecting usage information", e);
		} finally {
			try {
				FileUtils.deleteDirectory(tmpSession.getLocalRepository().getBasedir());
			} catch (IOException e) {
				logger.error("Couldn't remove temporary repository tmp-repo", e);
			}
		}

		return res;
	}

	@Override
	public Multimap<Artifact, Artifact> collectClientsOf(String coordinates) {
		Multimap<Artifact, Artifact> res = ArrayListMultimap.create();
		List<Artifact> allVersions = collectAvailableVersions(coordinates);

		for (Artifact artifact : allVersions) {
			logger.info("Retrieving all clients of " + artifact);
			res.putAll(artifact, collectClientsOf(artifact));
		}

		return res;
	}

	private List<String> parseUsagePage(String groupId, String artifactId, String version, int page) {
		List<String> res = new ArrayList<>();

		try {
			Document doc = Jsoup.connect(String.format(MVN_REPOSITORY_USAGE_PAGE, groupId, artifactId, version, page))
					.get();

			// One div.im block per user of the library; p.im-subtitle contains coordinates
			Elements ims = doc.select("p.im-subtitle");

			if (!ims.isEmpty()) {
				ims.forEach(im -> {
					// Two <a/> per .im-subtitle: first is groupId, second is artifactId
					Elements as = im.select("a");

					if (as.size() == 2) {
						String parsedGroupId = as.get(0).text();
						String parsedArtifactId = as.get(1).text();

						res.add(String.format("%s:%s", parsedGroupId, parsedArtifactId));
					}
				});

				// Continue on next usage page
				res.addAll(parseUsagePage(groupId, artifactId, version, page + 1));
			}
		} catch (IOException e) {
			logger.error("Error fetching mvnrepository.com usage page", e);
		}

		return res;
	}

	public static void main(String[] args) throws Exception {
		MavenCollector collector = new AetherCollector();
//		AetherDownloader downloader = new AetherDownloader();
//
//		List<Artifact> allApis = collector.collectAvailableVersions("org.sonarsource.sonarqube:sonar-plugin-api");
//		downloader.downloadAllArtifacts(allApis);
//		List<Artifact> allClients = collector
//				.collectClientsOf(new DefaultArtifact("org.sonarsource.sonarqube:sonar-plugin-api:7.4"));
		Multimap<Artifact, Artifact> allClients = collector
				.collectClientsOf("org.sonarsource.sonarqube:sonar-plugin-api");
		System.out.println("Found " + allClients.size() + " clients: " + allClients);
	}
}
