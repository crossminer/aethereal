package nl.cwi.swat;

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
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

public class Aethereal {
	public static final String LOCAL_REPO = "local-repo";
	public static final String REMOTE_URL = "http://repo1.maven.org/maven2/";

	private RepositorySystem system;
	private RepositorySystemSession session;
	private RemoteRepository repository;

	private static final Logger logger = LogManager.getLogger(Aethereal.class);

	public Aethereal() {
		system = newRepositorySystem();
		session = newSession(system);
		repository = newRemoteRepository();
	}

	public List<Artifact> collectAvailableVersions(String coordinates)
			throws VersionRangeResolutionException, ArtifactResolutionException {
		Artifact artifact = new DefaultArtifact(coordinates + ":[0,)");

		VersionRangeRequest rangeRequest = new VersionRangeRequest();
		rangeRequest.setArtifact(artifact);
		rangeRequest.addRepository(repository);

		VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest);

		return rangeResult.getVersions().stream()
				.map(v -> new DefaultArtifact(coordinates + ":" + v))
				.collect(Collectors.toList());
	}
	
	public Artifact downloadArtifact(String coordinates) {
		return downloadArtifact(new DefaultArtifact(coordinates));
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
			logger.error("Couldn't download {}", artifact);
			return null;
		}
	}

	public List<Artifact> downloadAllArtifacts(List<Artifact> list) {
		return list.stream()
				.map(a -> downloadArtifact(a))
				.collect(Collectors.toList());
	}

	public void displayDependencies(String group) throws DependencyCollectionException, DependencyResolutionException {
		Dependency dependency = new Dependency(new DefaultArtifact("org.apache.maven:maven-profile:2.2.1"), "compile");

		CollectRequest collectRequest = new CollectRequest();
		collectRequest.setRoot(dependency);
		collectRequest.addRepository(repository);
		DependencyNode node = system.collectDependencies(session, collectRequest).getRoot();

		DependencyRequest dependencyRequest = new DependencyRequest();
		dependencyRequest.setRoot(node);

		system.resolveDependencies(session, dependencyRequest);

		PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
		node.accept(nlg);
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
		return new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();
	}

	public static void main(String[] args) throws Exception {
		Aethereal a = new Aethereal();
		
		List<Artifact> allApis = a.collectAvailableVersions("org.sonarsource.sonarqube:sonar-plugin-api");
		a.downloadAllArtifacts(allApis);
	}
}
