package nl.cwi.swat.aethereal;

import java.util.List;

import org.eclipse.aether.artifact.Artifact;

import com.google.common.collect.Multimap;

public interface MavenCollector {
	/**
	 * Collect all available versions of the given artifact
	 * 
	 * @param coordinates Version-free coordinates, i.e.
	 *                    &lt;groupId&gt;:&lt;artifactId&gt;
	 */
	public List<Artifact> collectAvailableVersions(String coordinates);

	/**
	 * Collect all versions of the given artifact within the range
	 * [lowerBound, upperBound)
	 * 
	 * @param coordinates Version-free coordinates, i.e.
	 *                    &lt;groupId&gt;:&lt;artifactId&gt;
	 */
	public List<Artifact> collectAvailableVersions(String coordinates, String lowerBound, String upperBound);

	/**
	 * Collect all clients of the given artifact
	 */
	public List<Artifact> collectClientsOf(Artifact artifact);

	/**
	 * Collect all clients of the given unversioned coordinate (i.e. all clients of
	 * any version of the supplied artifact)
	 * 
	 * @param coordinates Version-free coordinates, i.e.
	 *                    &lt;groupId&gt;:&lt;artifactId&gt;
	 */
	public Multimap<Artifact, Artifact> collectClientsOf(String coordinates);

	/**
	 * Collect libraries that match the given query (number of clients, size of the
	 * JAR, number of versions, etc.)
	 */
	public List<Artifact> collectLibrariesMatching(MavenCollectorQuery query);
	/**
	 * Check if the coordinate points to a real arifact
	 */
	public boolean checkArtifact(String coordinate);
	
}
