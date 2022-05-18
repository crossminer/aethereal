package nl.cwi.swat.aethereal;

import static org.eclipse.aether.repository.RepositoryPolicy.CHECKSUM_POLICY_FAIL;

import java.net.NoRouteToHostException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import com.google.common.util.concurrent.RateLimiter;

public class AetherDownloader {
	private RepositorySystem system;
	private RepositorySystemSession session;
	private RemoteRepository repository;

	private RateLimiter aetherLimiter;


	public AetherDownloader(int aetherQps) {
		system = Aether.newRepositorySystem();
		session = Aether.newSession(system);
		repository = toRemoteRepository("https://repo1.maven.org/maven2/", Optional.empty(), Optional.empty());;
		aetherLimiter = RateLimiter.create(aetherQps);
	}

	public static RemoteRepository toRemoteRepository(
		      String repoUrl, Optional<String> username, Optional<String> password) {
		    RemoteRepository.Builder repo =
		        new RemoteRepository.Builder(null, "default", repoUrl)
		            .setPolicy(new RepositoryPolicy(true, null, CHECKSUM_POLICY_FAIL));

		    if (username.isPresent() && password.isPresent()) {
		      Authentication authentication =
		          new AuthenticationBuilder()
		              .addUsername(username.get())
		              .addPassword(password.get())
		              .build();
		      repo.setAuthentication(authentication);
		    }

		    return repo.build();
		  }

	public Artifact downloadArtifactTo(Artifact artifact, String repositoryPath) {
		//if (!artifact.getClassifier().equals("sources")) {
		//	Artifact art = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "sources", "jar", artifact.getVersion());
		//	downloadArtifactTo(art, repositoryPath);
		//}
		ArtifactRequest request = new ArtifactRequest();
		request.setArtifact(artifact);
		request.addRepository(repository);

//		System.out.println("Downloading "+ artifact);
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
					System.err.println("Artifact {} not found on Maven Central."+ artifact);
					// We won't get it ever
					break;
				} if (root instanceof NoRouteToHostException) {
					System.err.println("We got kicked from Maven Central. Waiting 30s."+ e);
				} else if (root instanceof MetadataNotFoundException) {
					System.err.println("Couldn't resolve local metadata for {}."+ artifact);
					// We won't get it ever
					break;
				} else if (root instanceof NoRouteToHostException || root instanceof ArtifactTransferException) {
					System.err.println("We probably got kicked from Maven Central. Waiting 30s."+ e);
					try {
						Thread.sleep(1000 * 30);
					} catch (InterruptedException ee) {
						System.err.println(ee);
						Thread.currentThread().interrupt();
					}
				} else {
					System.err.println("Artifact {} not found on Maven Central."+ artifact);
					break;
				}
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
