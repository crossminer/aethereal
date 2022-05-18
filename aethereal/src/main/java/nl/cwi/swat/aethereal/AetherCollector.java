package nl.cwi.swat.aethereal;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.RateLimiter;

/**
 * The Aether collector relies on Eclipse's Aether to gather information
 * remotely from Maven Central directly.
 */
public class AetherCollector implements MavenCollector {
	private RepositorySystem system = Aether.newRepositorySystem();
	private RepositorySystemSession session = Aether.newSession(system);
	private RemoteRepository repository = Aether.newRemoteRepository();

	private RateLimiter aetherLimiter;
	private RateLimiter jsoupLimiter;

	private static final String MVN_REPOSITORY_USAGE_PAGE = "https://mvnrepository.com/artifact/%s/%s/%s/usages?p=%d";

//	private static final Logger logger = LogManager.getLogger(AetherCollector.class);

	public AetherCollector(int aetherQps, int jsoupQps) {
		this.aetherLimiter = RateLimiter.create(aetherQps);
		this.jsoupLimiter = RateLimiter.create(jsoupQps);
	}

	@Override
	public List<Artifact> collectAvailableVersions(String coordinates) {
		return collectAvailableVersions(coordinates, "0", "");
	}

	@Override
	public List<Artifact> collectAvailableVersions(String coordinates, String lowerBound, String upperBound) {
		VersionRangeRequest rangeRequest = new VersionRangeRequest();
		String rangeQuery = String.format("%s:[%s,%s)", coordinates, lowerBound, upperBound);
		rangeRequest.setArtifact(new DefaultArtifact(rangeQuery));
		rangeRequest.addRepository(repository);

		try {
			VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest);
			return rangeResult.getVersions().stream().map(v -> new DefaultArtifact(coordinates + ":" + v))
					.collect(Collectors.toList());
		} catch (VersionRangeResolutionException e) {
			System.err.println("Couldn't resolve version range " + e);
			return Lists.newArrayList();
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
			System.out.println("Scrapping HTML usage pages from mvnrepository.com...");
			List<String> parsed = parseUsagePage(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),
					1);

			// Then, look for available versions via Aether
			// Keep in mind that artifacts on mvnrepository.com may be != artifacts on Maven
			// Central
			for (String coordinates : parsed) {
				System.out.println("Looking for matching client versions for " + coordinates);
				List<Artifact> allVersions = collectAvailableVersions(coordinates);

				// For every version, lookup its direct dependencies and find a match
				for (Artifact client : allVersions) {
					// If 'client' has 'artifact' as a direct dependency, we have a match
					List<Dependency> dependencies = getDependencies(client, tmpSession);
					for (Dependency dependency : dependencies) {
						if (artifact.toString().equals(dependency.getArtifact().toString())
								&& dependency.getScope().equals("compile")) {
							System.out.println("{} does match " + client);
							res.add(client);
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error collecting usage information" + e);
		} finally {
			try {
				FileUtils.deleteDirectory(tmpSession.getLocalRepository().getBasedir());
			} catch (IOException e) {
				System.err.println("Couldn't remove temporary repository tmp-repo" + e);
			}
		}

		return res;
	}

	@Override
	public Multimap<Artifact, Artifact> collectClientsOf(String coordinates) {
		Multimap<Artifact, Artifact> res = ArrayListMultimap.create();
		List<Artifact> allVersions = collectAvailableVersions(coordinates);

		for (Artifact artifact : allVersions) {
			System.out.println("Retrieving all clients of " + artifact);
			res.putAll(artifact, collectClientsOf(artifact));
		}

		return res;
	}

	@Override
	public List<Artifact> collectLibrariesMatching(MavenCollectorQuery query) {
		throw new UnsupportedOperationException("Not yet");
	}

	private List<Dependency> getDependencies(Artifact client, RepositorySystemSession tmpSession) {
		ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
		descriptorRequest.setArtifact(client);
		descriptorRequest.addRepository(repository);

		ArtifactDescriptorResult descriptorResult = null;
		while (descriptorResult == null) {
			try {
				// Throttle connections with Maven Central
				aetherLimiter.acquire();
				descriptorResult = system.readArtifactDescriptor(tmpSession, descriptorRequest);
			} catch (ArtifactDescriptorException e) {
				Throwable root = ExceptionUtils.getRootCause(e);

				// Either the artifact doesn't exist on Central, or we got kicked
				if (root instanceof ArtifactNotFoundException) {
					System.err.println("Artifact {} retrieved from mvnrepository.com doesn't exist on Maven Central." + client);
					// We won't get it ever
					break;
				} else if (root instanceof NoRouteToHostException) {
					System.err.println("We got kicked from Maven Central. Waiting 30s." + e);
					try {
						Thread.sleep(1000 * 30);
					} catch (InterruptedException ee) {
						System.err.println(ee);
						Thread.currentThread().interrupt();
					}
				}
			}
		}

		return descriptorResult != null ? descriptorResult.getDependencies() : Collections.emptyList();
	}

	private List<String> parseUsagePage(String groupId, String artifactId, String version, int page) {
		List<String> res = new ArrayList<>();

		Document doc = null;
		while (doc == null) {
			try {
				// Probably useless, but costless
				jsoupLimiter.acquire();
				doc = Jsoup.connect(String.format(MVN_REPOSITORY_USAGE_PAGE, groupId, artifactId, version, page))
						.timeout(10000).get();
			} catch (Exception e) {
				System.err.println("We got kicked from mvnrepository.com. Waiting 30s.");
				try {
					Thread.sleep(1000 * 30);
				} catch (InterruptedException ee) {
					System.err.println(ee);
					Thread.currentThread().interrupt();
				}
			}

		}

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

		return res;
	}

	@Override
	public boolean checkArtifact(String coordinate) {
		throw new UnsupportedOperationException("Not implemented, yet");
	}
}
