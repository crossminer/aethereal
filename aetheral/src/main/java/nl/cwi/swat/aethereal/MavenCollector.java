package nl.cwi.swat.aethereal;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public interface MavenCollector {
	/**
	 * Collect all available versions of the given
	 * artifact on the remote repository
	 * 
	 * @param coordinates Version-free coordinates, i.e.
	 *                    &lt;groupId&gt;:&lt;artifactId&gt;
	 */
	public List<Artifact> collectAvailableVersions(String coordinates);

	/**
	 * Download the given artifact from the remote repository
	 */
	public Artifact downloadArtifact(Artifact artifact);
	
	/**
	 * Collect all clients of the given artifact
	 * on the remote repository
	 */
	public List<Artifact> collectClientsOf(Artifact artifact);

	default public Artifact downloadArtifact(String coordinates) {
		return downloadArtifact(new DefaultArtifact(coordinates));
	}

	default public List<Artifact> downloadAllArtifacts(List<Artifact> list) {
		return list.stream().map(a -> downloadArtifact(a)).collect(Collectors.toList());
	}
}
