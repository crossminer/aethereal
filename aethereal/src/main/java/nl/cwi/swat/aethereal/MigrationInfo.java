package nl.cwi.swat.aethereal;

import java.util.List;

import org.eclipse.aether.artifact.Artifact;

public class MigrationInfo {
	public Artifact libv1;
	public Artifact libv2;
	public Integer count;
	public Integer getCount() {
		return count;
	}

	public void setCount(Integer count) {
		this.count = count;
	}

	public List<Artifact> clientsV1;
	public List<Artifact> clientsV2;

	MigrationInfo(Artifact l1, Artifact l2, int c, List<Artifact> clientsV1, List<Artifact> clientsV2) {
		libv1 = l1;
		libv2 = l2;
		count = c;
		this.clientsV1 = clientsV1;
		this.clientsV2 = clientsV2;
	}

	@Override
	public String toString() {
		return String.format("[%s] %s -> %s", count, libv1.getArtifactId(), libv2.getArtifactId());
	}
}
