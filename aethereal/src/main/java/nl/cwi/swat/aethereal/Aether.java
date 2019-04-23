package nl.cwi.swat.aethereal;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

public class Aether {
	public static final String LOCAL_REPO = "local-repo";
	public static final String REMOTE_URL = "http://repo1.maven.org/maven2/";

	private Aether() {

	}

	public static RepositorySystem newRepositorySystem() {
		DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterFactory.class, FileTransporterFactory.class);
		locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

		return locator.getService(RepositorySystem.class);
	}

	public static RepositorySystemSession newSession(RepositorySystem system, String repositoryPath) {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

		LocalRepository repository = new LocalRepository(repositoryPath);
		session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, repository));

		return session;
	}

	public static RepositorySystemSession newSession(RepositorySystem system) {
		return newSession(system, LOCAL_REPO);
	}

	public static RemoteRepository newRemoteRepository() {
		return new RemoteRepository.Builder("central", "default", REMOTE_URL).build();
	}

	public static String toCoordinates(Artifact artifact) {
		return String.format("%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
	}

	public static String toUnversionedCoordinates(Artifact artifact) {
		return String.format("%s:%s", artifact.getGroupId(), artifact.getArtifactId());
	}
}
