package nl.cwi.swat.aethereal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import io.usethesource.vallang.IValue;
import nl.cwi.swat.aethereal.rascal.RascalM3;

public class LibraryMigration {
	private RascalM3 extractorM3 = new RascalM3();

	private AetherDownloader dowloader = new AetherDownloader(4);
	private static final String TEMP_MVN = "temp";

	public LibraryMigration() {
	}

	private Artifact getArtifact(String coordinates) {
		String[] coords = coordinates.split(":");
		DefaultArtifact art = new DefaultArtifact(coords[0], coords[1], null, "jar", coords[2]);
		return dowloader.downloadArtifactTo(art, TEMP_MVN);
	}

	private Multimap<String, String> getMDMIFromCoordinates(String coordinates) throws IOException {
		Artifact c1Art = getArtifact(coordinates);
		IValue c1M3 = getM3Model(c1Art.getFile().toString());
		// List<String> packs = getPackages(m3);
		return getMDMI(c1M3);
	}

	public void run() {
		try (LineIterator it = FileUtils.lineIterator(Paths.get("result_client").toFile(), "UTF-8")) {
			while (it.hasNext()) {
				// Each line in the form "source","target"
				String line = it.nextLine();
				String[] fields = line.split(",");
				String c1 = fields[0];
				String c2 = fields[1];
				List<String> libRemoved = Arrays.asList(fields[2].split(" "));
				List<String> libAdded = Arrays.asList(fields[3].split(" "));
				Set<String> libAddedNoVers = libAdded.stream().map(z -> z.substring(0, z.lastIndexOf(":")))
						.collect(Collectors.toSet());
				Set<String> libRemovedNoVers = libRemoved.stream().map(z -> z.substring(0, z.lastIndexOf(":")))
						.collect(Collectors.toSet());
				Set<String> intersection = new HashSet<String>(libAddedNoVers);
				intersection.retainAll(libRemovedNoVers);
				List<String> libR = libRemoved.stream().filter(z -> !intersection.contains(z.substring(0, z.lastIndexOf(":")))).collect(Collectors.toList());
				List<String> libA = libAdded.stream().filter(z -> !intersection.contains(z.substring(0, z.lastIndexOf(":")))).collect(Collectors.toList());
				List<String> libU = libAdded.stream().filter(z -> intersection.contains(z.substring(0, z.lastIndexOf(":")))).collect(Collectors.toList());
				List<MigrationTuple> migrationTuples = mineLib(
						libR,
						c2, c1, ChangeType.REMOVED);
				migrationTuples.addAll(mineLib(
						libA,
						c1, c2, ChangeType.ADDED));
				migrationTuples.addAll(mineLib(
						libU,
						c1, c2, ChangeType.UPDATED));
				
				
				migrationTuples.forEach(z -> System.out.println(z));
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private List<MigrationTuple> mineLib(List<String> libs, String c1, String c2, ChangeType VER)
			throws IOException {
		Multimap<String, String> c1MD_MI = getMDMIFromCoordinates(c1);
		Multimap<String, String> c2MD_MI = getMDMIFromCoordinates(c2);
		List<MigrationTuple> mts = new ArrayList<MigrationTuple>();
		for (String libString : libs) {
			Multimap<String, String> libMD_MI = getMDMIFromCoordinates(libString);
			Set<String> libtMD = libMD_MI.keySet();
			Set<String> usedMI = new HashSet<String>(c2MD_MI.values()); // use the copy constructor
			usedMI.retainAll(libtMD);
			Map<String, String> callers = getMDsContainingMIs(c2MD_MI, usedMI);
			for (Entry<String, String> call : callers.entrySet()) {
				ArrayList<String> c2APIList = new ArrayList<String>(c2MD_MI.get(call.getKey()));
				ArrayList<String> c1APIList = new ArrayList<String>(c1MD_MI.get(call.getKey()));
				if (!c2APIList.equals(c1APIList)) {
					MigrationTuple mt = new MigrationTuple();
					mt.setAffectedLib(libString);
					mt.setCalled(call.getValue());
					mt.setCaller(call.getKey());
					mt.setV1Body(c1APIList);
					mt.setCoordinatesV1(c1);
					mt.setCoordinatesV2(c2);
					mt.setChange(VER);
					if (c2MD_MI.containsKey(call.getKey()))
						mt.setV2Body(c2APIList);
					else
						mt.setV1Body(new ArrayList<String>());
					mts.add(mt);
				}
			}
		}
		return mts;
	}

	public static void main(String[] args) {
		LibraryMigration lm = new LibraryMigration();
		lm.run();
	}

	public Map<String, String> getMDsContainingMIs(Multimap<String, String> libMD_MI, Set<String> usedMI) {
		Map<String, String> methodDeclarations = new HashMap();
		for (String md : usedMI) {
			for (Entry<String, String> entry : libMD_MI.entries()) {
				if (entry.getValue().equals(md))
					methodDeclarations.put(entry.getKey(), md);
			}
		}
		return methodDeclarations;
	}

	public IValue getM3Model(String filePath) throws IOException {
		AtomicInteger count = new AtomicInteger(0);
		Path p = Paths.get(filePath);
		if (p.toFile().isFile() && p.toString().endsWith(".jar") && !p.toString().contains("-sources")) {
			// && !Paths.get(p.toAbsolutePath().toString() + ".m3").toFile().exists()) {
			String jar = p.toAbsolutePath().toString();
			String dest = p.toAbsolutePath().toString() + ".m3";
//			System.out.println("Building M3 model for" + count.incrementAndGet() + jar);
			IValue m3model = null;
			if (Paths.get(jar + ".m3").toFile().exists())
				m3model = extractorM3.deserializeM3(jar);
			else {
				m3model = extractorM3.createM3FromJarFile(jar);
				extractorM3.writeM3ModelFile(m3model, dest);
			}
			return m3model;
			// QUI CI VA LA MIA LOGICA
			// writeFocusFiles(p, m3model);
		} else
			throw new IOException("File not Found");
	}

	private List<String> getPackages(IValue m3model) {
		Multimap<String, String> dec = extractorM3.extractDeclarations(m3model);
		List<String> temp = dec.keySet().stream().filter(z -> z.contains("java+package")).collect(Collectors.toList());
		return temp;
	}

	private List<String> getMethodDeclarations(IValue m3model) {
		Multimap<String, String> dec = extractorM3.extractDeclarations(m3model);
		List<String> temp = dec.keySet().stream()
				.filter(z -> z.contains("java+method") || z.contains("java+constructor")).collect(Collectors.toList());
		return temp;
	}

	private Multimap<String, String> getMDMI(IValue m3model) {
		return extractorM3.extractMethodInvocations(m3model);
	}

	private void writeFocusFiles(Path p, IValue m3model) {
		String focus = p.toAbsolutePath().toString() + ".focus";
		String mdFocus = p.toAbsolutePath().toString() + ".md.focus";
		Multimap<String, String> md_mi = extractorM3.extractMethodInvocations(m3model);
		Multimap<String, String> dec = extractorM3.extractDeclarations(m3model);
		List<String> temp = dec.keySet().stream().filter(z -> z.contains("java+package")).collect(Collectors.toList());
		Set<String> KOI = Sets.newHashSet();
		md_mi.keys().forEach(key -> {
//			if (md_mi.get(key).stream().filter(z -> !md_mi.containsKey(z)).count() > 8)
			KOI.add(key);
		});
		Path focusFile = Paths.get(focus);
		Path mdFocusFile = Paths.get(mdFocus);
		try (BufferedWriter writerMD = Files.newBufferedWriter(mdFocusFile);
				BufferedWriter writer = Files.newBufferedWriter(focusFile);) {
			for (String md : KOI) {
				writerMD.write(
						md.replace("|", "").replace("java+constructor:///", "").replace("java+method:///", "") + "\n");
				for (String mi : md_mi.get(md)) {

					writer.write(String.format("%s#%s%n",
							md.replace("|", "").replace("java+constructor:///", "").replace("java+method:///", ""),
							mi.replace("|", "").replace("java+constructor:///", "").replace("java+method:///", "")));
				}
			}
		} catch (IOException e) {
			System.err.println(e);
		}
	}
}