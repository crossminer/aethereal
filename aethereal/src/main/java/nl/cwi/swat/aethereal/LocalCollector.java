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
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

/**
 * The Local collector uses a pre-computed dependency graph of Maven Central
 * available at https://zenodo.org/record/1489120 to gather all information
 * about artifacts locally.
 */
public class LocalCollector implements MavenCollector {

	private static final String DATASET_PATH = "dependency-graph/";
	private static final String VERSIONS_FILE = DATASET_PATH + "next_all.csv";
	private static final String LINKS_FILE = DATASET_PATH + "links_all.csv";
	private static final String RELEASE_FILE = DATASET_PATH + "release_all.csv";
	private static final String REMOTE_DATASET = "https://zenodo.org/record/1489120/files/maven-data.csv.tar.xz";


	public LocalCollector() {
		retrieveDataset();
	}

	@Override
	public List<Artifact> collectAvailableVersions(String coordinates) {
		List<Artifact> ret = new ArrayList<>();

		System.out.println("Looking for {} versions in {}"+ coordinates+ VERSIONS_FILE);
		try (LineIterator it = FileUtils.lineIterator(Paths.get(VERSIONS_FILE).toFile(), "UTF-8");
				LineIterator it2 = FileUtils.lineIterator(Paths.get(RELEASE_FILE).toFile(), "UTF-8");) {
			while (it.hasNext()) {
				// Each line in the form "source","target"
				String line = it.nextLine();
				String[] fields = line.split(",");
				String source = fields[0].replaceAll("\"", "");
				String target = fields[1].replaceAll("\"", "");
				// ':' included to avoid matching source[-more-text]
				if (source.startsWith(coordinates + ":")) {
					Artifact older = new DefaultArtifact(source);
					Artifact newer = new DefaultArtifact(target);
					if (!ret.contains(older))
						ret.add(older);
					if (!ret.contains(newer))
						ret.add(newer);
				}
			}
//			while(it2.hasNext()) {
//				String line = it2.nextLine();
//				Optional<Artifact> release = ret.stream().filter(z -> 
//					line.split(",")[0].replaceAll("\"", "").equals(String.format("%s:%s:%s", z.getGroupId(), z.getArtifactId(), z.getVersion())
//						)
//					).findAny();
//				if (release.isPresent()) {
//					Map<String, String> propr = Maps.newHashMap();
//					propr.put("releaseDate", line.split(",")[2]);
//					release.get().setProperties(propr);
//				}
//			}
			return ret;
		} catch (IOException e) {
			System.err.println("Couldn't read {}"+VERSIONS_FILE+ e);
			return Lists.newArrayList();
		}
	}

	@Override
	public List<Artifact> collectClientsOf(Artifact artifact) {
		List<Artifact> ret = new ArrayList<>();

		System.out.println("Looking for clients of {} in {}"+artifact+ LINKS_FILE);
		try (LineIterator it = FileUtils.lineIterator(Paths.get(LINKS_FILE).toFile(), "UTF-8");
				LineIterator it2 = FileUtils.lineIterator(Paths.get(RELEASE_FILE).toFile(), "UTF-8");) {
			while (it.hasNext()) {
				// Each line in the form "source","target","scope"
				String line = it.nextLine();
				String[] fields = line.split(",");
				String source = fields[0].replaceAll("\"", "");
				String target = fields[1].replaceAll("\"", "");
				String scope = fields[2].replaceAll("\"", "");

				if (target.equals(Aether.toCoordinates(artifact)) && scope.equals("Compile")) {
					ret.add(new DefaultArtifact(source));
				}
			}
//			while(it2.hasNext()) {
//				String line = it2.nextLine();
//				Optional<Artifact> release = ret.stream().filter(z -> line.equals(String.format("%s:%s:%s", z.getGroupId(), z.getArtifactId(), z.getVersion()))).findAny();
//				if (release.isPresent()) {
//					Map<String, String> propr = Maps.newHashMap();
//					propr.put("releaseDate", line.split(",")[2]);
//					release.get().setProperties(propr);
//				}
//			}

			return ret;
		} catch (IOException e) {
			System.err.println("Couldn't read {}"+ LINKS_FILE+ e);
			return Lists.newArrayList();
		}
	}

	@Override
	public Multimap<Artifact, Artifact> collectClientsOf(String coordinates) {
		Multimap<Artifact, Artifact> ret = ArrayListMultimap.create();

		System.out.println("Looking for clients of any version of {} in {}"+ coordinates+ LINKS_FILE);
		try (LineIterator it = FileUtils.lineIterator(Paths.get(LINKS_FILE).toFile(), "UTF-8")) {
			while (it.hasNext()) {
				// Each line in the form "source","target","scope"
				String line = it.nextLine();
				String[] fields = line.split(",");
				String source = fields[0].replaceAll("\"", "");
				String target = fields[1].replaceAll("\"", "");
				String scope = fields[2].replaceAll("\"", "");

				if (target.startsWith(coordinates + ":") && scope.equals("Compile")) {
					ret.put(new DefaultArtifact(target), new DefaultArtifact(source));
				}
			}

			return ret;
		} catch (IOException e) {
			System.err.println("Couldn't read {}"+ LINKS_FILE+ e);
			return null;
		}
	}

	@Override
	public List<Artifact> collectLibrariesMatching(MavenCollectorQuery query) {
		throw new UnsupportedOperationException("Not yet");
	}

	private void retrieveDataset() {
		if (datasetExists()) {
			System.out.println("Maven Dependency Graph found. Skipping download.");
			return;
		}

		System.err.println("Couldn't find the Maven Dependency Graph. I will download and extract it for you (~1.1GB).");

		try {
//			System.err.println("Downloading archive from {} to {}"+ REMOTE_DATASET+ DATASET_PATH);
			FileUtils.copyURLToFile(new URL(REMOTE_DATASET), new File(DATASET_PATH + "archive.tar.xz"), 5000, 5000);

			System.err.println("Extracting archive locally");
			try (InputStream fi = Files.newInputStream(Paths.get(DATASET_PATH + "archive.tar.xz"));
					InputStream bi = new BufferedInputStream(fi);
					InputStream xzi = new XZCompressorInputStream(bi);
					ArchiveInputStream arch = new TarArchiveInputStream(xzi)) {

				ArchiveEntry entry = null;
				while ((entry = arch.getNextEntry()) != null) {
					if (entry.getName().equals("maven-data.csv/links_all.csv")) {
						try (OutputStream o = Files.newOutputStream(Paths.get(LINKS_FILE))) {
							IOUtils.copy(arch, o);
						} catch (IOException e) {
							System.err.println("Couldn't write destination file"+ e);
						}
					} else if (entry.getName().equals("maven-data.csv/next_all.csv")) {
						try (OutputStream o = Files.newOutputStream(Paths.get(VERSIONS_FILE))) {
							IOUtils.copy(arch, o);
						} catch (IOException e) {
							System.err.println("Couldn't write destination file"+ e);
						}
					}
				}
			} catch (IOException e) {
				System.err.println("Couldn't extract dataset archive"+ e);
			}
		} catch (IOException e) {
			System.err.println("Couldn't extract dataset archive"+ e);
		}
	}

	private boolean datasetExists() {
		return Paths.get(LINKS_FILE).toFile().exists() && Paths.get(VERSIONS_FILE).toFile().exists();
	}

	@Override
	public boolean checkArtifact(String coordinate) {
		System.out.println("Looking for {} version in {}"+ coordinate+ VERSIONS_FILE);
		try (LineIterator it = FileUtils.lineIterator(Paths.get(VERSIONS_FILE).toFile(), "UTF-8")) {
			while (it.hasNext()) {
				// Each line in the form "source","target"
				String line = it.nextLine();
				String[] fields = line.split(",");
				String source = fields[0].replaceAll("\"", "");

				if (source.equals(coordinate))
					return true;
			}
			return false;
		} catch (IOException e) {
			System.err.println("Couldn't read {}"+ VERSIONS_FILE+ e);
			return false;
		}
	}
}
