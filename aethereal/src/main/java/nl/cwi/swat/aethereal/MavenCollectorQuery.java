package nl.cwi.swat.aethereal;

public class MavenCollectorQuery {
	private final int clients;
	private final int size;
	private final int versions;

	public MavenCollectorQuery(int clients, int size, int versions) {
		this.clients = clients;
		this.size = size;
		this.versions = versions;
	}

	public int getClients() {
		return clients;
	}

	public int getSize() {
		return size;
	}

	public int getVersions() {
		return versions;
	}

	public static Builder builder() {
		return new Builder();
	}

	static class Builder {
		private int clients;
		private int size;
		private int versions;

		public Builder clients(int n) {
			this.clients = n;
			return this;
		}

		public Builder size(int n) {
			this.size = n;
			return this;
		}

		public Builder versions(int n) {
			this.versions = n;
			return this;
		}

		public MavenCollectorQuery build() {
			return new MavenCollectorQuery(clients, size, versions);
		}
	}
}
