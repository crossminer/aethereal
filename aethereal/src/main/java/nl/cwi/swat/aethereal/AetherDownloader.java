package nl.cwi.swat.aethereal;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import com.google.common.util.concurrent.RateLimiter;

public class AetherDownloader {
	private RepositorySystem system;
	private RepositorySystemSession session;
	private RemoteRepository repository;

	private RateLimiter aetherLimiter = RateLimiter.create(2.5); // Black magic

	private static final Logger logger = LogManager.getLogger(AetherDownloader.class);

	public AetherDownloader() {
		system = Aether.newRepositorySystem();
		session = Aether.newSession(system);
		repository = Aether.newRemoteRepository();
	}

	public Artifact downloadArtifactTo(Artifact artifact, String repositoryPath) {
		ArtifactRequest request = new ArtifactRequest();
		request.setArtifact(artifact);
		request.addRepository(repository);

		logger.info("Downloading {}...", artifact);

		// Don't kick me senpai
		ArtifactResult artifactResult = null;
		while (artifactResult == null) {
			try {
				// Limit to 1 call / second
				aetherLimiter.acquire();
				if (repositoryPath != null)
					artifactResult = system.resolveArtifact(Aether.newSession(system, repositoryPath), request);
				else
					artifactResult = system.resolveArtifact(session, request);
			} catch (Exception e) {
				// We got kicked probably
				logger.error("We got kicked from Maven Central. Waiting 10s");
				try {
					Thread.sleep(10000);
				} catch (InterruptedException ee) {
					logger.error(ee);
					Thread.currentThread().interrupt();
				}
			}
		}

		return artifactResult.getArtifact();
	}

	public Artifact downloadArtifact(Artifact artifact) {
		return downloadArtifactTo(artifact, null);
	}

	public List<Artifact> downloadAllArtifacts(Collection<Artifact> list) {
		return list.stream().map(this::downloadArtifact).collect(Collectors.toList());
	}

	public List<Artifact> downloadAllArtifactsTo(Collection<Artifact> list, String repositoryPath) {
		return list.stream().map(a -> downloadArtifactTo(a, repositoryPath)).collect(Collectors.toList());
	}
}
