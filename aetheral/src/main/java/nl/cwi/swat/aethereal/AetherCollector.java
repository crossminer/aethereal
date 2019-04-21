package nl.cwi.swat.aethereal;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

public class AetherCollector implements MavenCollector {
	public static final String LOCAL_REPO = "local-repo";
	public static final String REMOTE_URL = "http://repo1.maven.org/maven2/";

	private RepositorySystem system;
	private RepositorySystemSession session;
	private RemoteRepository repository;

	private static final Logger logger = LogManager.getLogger(AetherCollector.class);

	public AetherCollector() {
		system = newRepositorySystem();
		session = newSession(system);
		repository = newRemoteRepository();
	}

	@Override
	public List<Artifact> collectAvailableVersions(String coordinates) {
		VersionRangeRequest rangeRequest = new VersionRangeRequest();
		rangeRequest.setArtifact(new DefaultArtifact(coordinates + ":[0,)"));
		rangeRequest.addRepository(repository);

		try {
			VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest);
			return rangeResult.getVersions().stream()
					.map(v -> new DefaultArtifact(coordinates + ":" + v))
					.collect(Collectors.toList());
		} catch (VersionRangeResolutionException e) {
			logger.error("Couldn't resolve version range", e);
			return null;
		}
	}

	@Override
	public List<Artifact> collectClientsOf(Artifact artifact) {
		throw new UnsupportedOperationException("Aether cannot be used for that");
	}

	public Artifact downloadArtifact(Artifact artifact) {
		ArtifactRequest request = new ArtifactRequest();
		request.setArtifact(artifact);
		request.addRepository(repository);

		try {
			logger.info("Downloading {}...", artifact);
			ArtifactResult artifactResult = system.resolveArtifact(session, request);
			return artifactResult.getArtifact();
		} catch (ArtifactResolutionException e) {
			logger.error("Couldn't download {}", artifact, e);
			return null;
		}
	}

	private RepositorySystem newRepositorySystem() {
		DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterFactory.class, FileTransporterFactory.class);
		locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

		return locator.getService(RepositorySystem.class);
	}

	private RepositorySystemSession newSession(RepositorySystem system) {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

		LocalRepository localRepo = new LocalRepository(LOCAL_REPO);
		session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

		return session;
	}

	private RemoteRepository newRemoteRepository() {
		return new RemoteRepository.Builder("central", "default", REMOTE_URL).build();
	}

	public static void main(String[] args) throws Exception {
		MavenCollector collector = new AetherCollector();

		List<Artifact> allApis = collector.collectAvailableVersions("org.sonarsource.sonarqube:sonar-plugin-api");
		collector.downloadAllArtifacts(allApis);
	}
}
