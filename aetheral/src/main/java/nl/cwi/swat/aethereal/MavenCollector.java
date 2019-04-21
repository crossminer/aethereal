package nl.cwi.swat.aethereal;

import java.util.List;

import org.eclipse.aether.artifact.Artifact;

public interface MavenCollector {
	/**
	 * Collect all available versions of the given artifact on the remote repository
	 * 
	 * @param coordinates Version-free coordinates, i.e.
	 *                    &lt;groupId&gt;:&lt;artifactId&gt;
	 */
	public List<Artifact> collectAvailableVersions(String coordinates);

	/**
	 * Collect all clients of the given artifact on the remote repository
	 */
	public List<Artifact> collectClientsOf(Artifact artifact);
}
