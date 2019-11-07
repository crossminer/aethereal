package nl.cwi.swat.aethereal;

import java.net.NoRouteToHostException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;

import com.google.common.util.concurrent.RateLimiter;

public class AetherDownloader {
	private RepositorySystem system;
	private RepositorySystemSession session;
	private RemoteRepository repository;

	private RateLimiter aetherLimiter;

	private static final Logger logger = LogManager.getLogger(AetherDownloader.class);

	public AetherDownloader(int aetherQps) {
		system = Aether.newRepositorySystem();
		session = Aether.newSession(system);
		repository = Aether.newRemoteRepository();
		aetherLimiter = RateLimiter.create(aetherQps);
	}

	public Artifact downloadArtifactTo(Artifact artifact, String repositoryPath) {
		ArtifactRequest request = new ArtifactRequest();
		request.setArtifact(artifact);
		request.addRepository(repository);

		logger.info("Downloading {}", artifact);

		// Don't kick me senpai
		ArtifactResult artifactResult = null;
		while (artifactResult == null) {
			try {
				// Throttle connections with Maven Central
				aetherLimiter.acquire();
				if (repositoryPath != null)
					artifactResult = system.resolveArtifact(Aether.newSession(system, repositoryPath), request);
				else
					artifactResult = system.resolveArtifact(session, request);
			} catch (ArtifactResolutionException e) {
				Throwable root = ExceptionUtils.getRootCause(e);

				// Either the artifact doesn't exist on Central, or we got kicked
				if (root instanceof ArtifactNotFoundException) {
					logger.warn("Artifact {} not found on Maven Central.", artifact);
					// We won't get it ever
					break;
				} else if (root instanceof MetadataNotFoundException) {
					logger.warn("Couldn't resolve local metadata for {}.", artifact);
					// We won't get it ever
					break;
				} else if (root instanceof NoRouteToHostException || root instanceof ArtifactTransferException) {
					logger.warn("We probably got kicked from Maven Central. Waiting 30s.", e);
					try {
						Thread.sleep(1000 * 30);
					} catch (InterruptedException ee) {
						logger.error(ee);
						Thread.currentThread().interrupt();
					}
				} else logger.error("Unknown error: ", e.getCause());
			}
		}

		return artifactResult != null ? artifactResult.getArtifact() : null;
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
