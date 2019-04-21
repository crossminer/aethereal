package nl.cwi.swat.aethereal;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

import com.google.common.collect.Multimap;

/**
 * The Aether collector relies on Eclipse's Aether to gather information
 * remotely from Maven Central directly.
 */
public class AetherCollector implements MavenCollector {
	private RepositorySystem system;
	private RepositorySystemSession session;
	private RemoteRepository repository;

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
		throw new UnsupportedOperationException("Aether cannot be used (yet!) for that");
	}

	@Override
	public Multimap<Artifact, Artifact> collectClientsOf(String coordinates) {
		throw new UnsupportedOperationException("Aether cannot be used (yet!) for that");
	}

	public static void main(String[] args) throws Exception {
		MavenCollector collector = new AetherCollector();
		AetherDownloader downloader = new AetherDownloader();

		List<Artifact> allApis = collector.collectAvailableVersions("org.sonarsource.sonarqube:sonar-plugin-api");
		downloader.downloadAllArtifacts(allApis);
	}
}
