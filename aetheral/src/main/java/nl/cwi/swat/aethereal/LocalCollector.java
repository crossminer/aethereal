package nl.cwi.swat.aethereal;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * The Local collector uses a pre-computed dependency graph of Maven Central
 * available at https://zenodo.org/record/1489120 to gather all information
 * about artifacts locally.
 */
public class LocalCollector implements MavenCollector {
	private static final String DATASET_PATH = "dataset/";
	private static final String DATASET_FILE = DATASET_PATH + "links_all.csv";
	private static final String REMOTE_DATASET = "https://zenodo.org/record/1489120/files/maven-data.csv.tar.xz";

	private static final Logger logger = LogManager.getLogger(LocalCollector.class);

	public LocalCollector() {
		retrieveDataset();
	}

	@Override
	public List<Artifact> collectAvailableVersions(String coordinates) {
		List<Artifact> ret = new ArrayList<Artifact>();

		try (LineIterator it = FileUtils.lineIterator(Paths.get(DATASET_FILE).toFile(), "UTF-8")) {
			while (it.hasNext()) {
				// Each line in the form "source","target","scope"
				String line = it.nextLine();
				String[] fields = line.split(",");
				String source = fields[0].replaceAll("\"", "");

				if (source.startsWith(coordinates)) {
					Artifact found = new DefaultArtifact(source);

					if (!ret.contains(found))
						ret.add(found);
				}
			}

			return ret;
		} catch (IOException e) {
			logger.error("Couldn't read dataset", e);
			return null;
		}
	}

	@Override
	public List<Artifact> collectClientsOf(Artifact artifact) {
		List<Artifact> ret = new ArrayList<Artifact>();

		try (LineIterator it = FileUtils.lineIterator(Paths.get(DATASET_FILE).toFile(), "UTF-8")) {
			while (it.hasNext()) {
				// Each line in the form "source","target","scope"
				String line = it.nextLine();
				String[] fields = line.split(",");
				String source = fields[0].replaceAll("\"", "");
				String target = fields[1].replaceAll("\"", "");

				if (target.equals(toCoordinates(artifact))) {
					ret.add(new DefaultArtifact(source));
				}
			}

			return ret;
		} catch (IOException e) {
			logger.error("Couldn't read dataset", e);
			return null;
		}
	}
	
	private String toCoordinates(Artifact artifact) {
		return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
	}

	private void retrieveDataset() {
		if (datasetExists()) {
			logger.info("Local dataset found. Skipping download.");
			return;
		}

		logger.warn("Couldn't find local dataset. I will download and extract it for you (~1.1GB).");

		try {
			logger.warn("Downloading archive from {} to {}", REMOTE_DATASET, DATASET_PATH);
			FileUtils.copyURLToFile(new URL(REMOTE_DATASET), new File(DATASET_PATH + "archive.tar.xz"), 5000, 5000);

			logger.warn("Extracting archive locally");
			try (InputStream fi = Files.newInputStream(Paths.get(DATASET_PATH + "archive.tar.xz"));
					InputStream bi = new BufferedInputStream(fi);
					InputStream xzi = new XZCompressorInputStream(bi);
					ArchiveInputStream arch = new TarArchiveInputStream(xzi)) {

				ArchiveEntry entry = null;
				while ((entry = arch.getNextEntry()) != null) {
					if (entry.getName().equals("maven-data.csv/links_all.csv")) {
						try (OutputStream o = Files.newOutputStream(Paths.get(DATASET_FILE))) {
							IOUtils.copy(arch, o);
						} catch (IOException e) {
							logger.error("Couldn't open destination file", e);
						}
					}
				}
			} catch (IOException e) {
				logger.error("Couldn't extract dataset archive", e);
			}
		} catch (IOException e) {
			logger.error("Couldn't extract dataset archive", e);
		}
	}

	private boolean datasetExists() {
		return Files.exists(Paths.get(DATASET_FILE));
	}

	public static void main(String[] args) {
		LocalCollector collector = new LocalCollector();

		List<Artifact> res = collector.collectAvailableVersions("org.sonarsource.sonarqube:sonar-plugin-api");
		System.out.println(res);
		System.out.println(res.size());

		List<Artifact> res2 = collector
				.collectClientsOf(new DefaultArtifact("org.sonarsource.sonarqube:sonar-plugin-api:6.3"));
		System.out.println(res2);
		System.out.println(res2.size());
	}
}
