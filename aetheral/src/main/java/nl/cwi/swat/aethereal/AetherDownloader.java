package nl.cwi.swat.aethereal;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

public class AetherDownloader {
	private RepositorySystem system;
	private RepositorySystemSession session;
	private RemoteRepository repository;

	private static final Logger logger = LogManager.getLogger(AetherDownloader.class);

	public AetherDownloader() {
		system = Aether.newRepositorySystem();
		session = Aether.newSession(system);
		repository = Aether.newRemoteRepository();
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

	public List<Artifact> downloadAllArtifacts(List<Artifact> list) {
		return list.stream()
				.map(a -> downloadArtifact(a))
				.collect(Collectors.toList());
	}
}
