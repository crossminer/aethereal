package nl.cwi.swat.aethereal;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;

import io.usethesource.vallang.IValue;
import nl.cwi.swat.aethereal.rascal.RascalM3;

public class LibraryMigration {
	private RascalM3 extractorM3 = new RascalM3();

	private AetherDownloader dowloader = new AetherDownloader(4);
	private static String MAVEN_FOLDER = "temp";
	private static String OUTPUT_FOLDER = "generate_strings";
	
	public LibraryMigration(String mavenFolder, String outPutFolder) {
		MAVEN_FOLDER = mavenFolder;
		OUTPUT_FOLDER = outPutFolder;
	}

	private Artifact getArtifact(String coordinates) {
		String[] coords = coordinates.split(":");
		DefaultArtifact art = new DefaultArtifact(coords[0], coords[1], null, "jar", coords[2]);
		return dowloader.downloadArtifactTo(art, MAVEN_FOLDER);
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
				try {
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
					List<String> libR = libRemoved.stream()
							.filter(z -> !intersection.contains(z.substring(0, z.lastIndexOf(":"))))
							.collect(Collectors.toList());
					List<String> libA = libAdded.stream()
							.filter(z -> !intersection.contains(z.substring(0, z.lastIndexOf(":"))))
							.collect(Collectors.toList());
					List<String> libU = libAdded.stream()
							.filter(z -> intersection.contains(z.substring(0, z.lastIndexOf(":"))))
							.collect(Collectors.toList());
					List<MigrationTuple> migrationTuples = mineLib(libR, c2, c1, ChangeType.REMOVED);
					migrationTuples.addAll(mineLib(libA, c1, c2, ChangeType.ADDED));
					migrationTuples.addAll(mineLib(libU, c1, c2, ChangeType.UPDATED));
					serialize(migrationTuples, c1, c2);
					System.out.println(String.format("%s vs %s", c1, c2));
				} catch (Exception e) {

				}
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void serialize(List<MigrationTuple> migrationTuples, String c1, String c2)
			throws JsonIOException, IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String s = gson.toJson(migrationTuples);
		try (BufferedWriter bf = new BufferedWriter(
				new FileWriter(Paths.get(OUTPUT_FOLDER, String.format("%s_MYSEP_%s.json", c1, c2)).toFile()))) {
			bf.write(s);
			bf.close();
		}
	}

	private List<MigrationTuple> mineLib(List<String> libs, String c1, String c2, ChangeType VER) throws IOException {
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
				if (!c2APIList.equals(c1APIList) && c2APIList.size() > 0 && c1APIList.size() > 0) {
					MigrationTuple mt = new MigrationTuple();
					mt.setAffectedLib(libString);
					mt.setCalled(call.getValue());
					mt.setCaller(call.getKey());
					mt.setCoordinatesV1(c1);
					mt.setCoordinatesV2(c2);
					mt.setChange(VER);
					if (VER == ChangeType.ADDED || VER == ChangeType.UPDATED) {
						mt.setV1Body(c1APIList);
						mt.setV2Body(c2APIList);
					}
					if (VER == ChangeType.REMOVED) {
						mt.setV1Body(c2APIList);
						mt.setV2Body(c1APIList);
					}
					mts.add(mt);
				}
			}
		}
		return mts;
	}

	public static void main(String[] args) {
		
		HelpFormatter formatter = new HelpFormatter();

		Options opts = new Options()
				.addOption(Option.builder("maven_folder").desc("A temporary folder to store the maven repository").hasArg()
						.argName("maven_folder").build())
				.addOption(Option.builder("output_folder").desc("An existing folder to store the json file coming fron the analysis").hasArg()
						.argName("output_folder").build());
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		try {
			cmd = parser.parse(opts, args);
			LibraryMigration lm = new LibraryMigration(cmd.getOptionValue("maven_folder", MAVEN_FOLDER), cmd.getOptionValue("output_folder", OUTPUT_FOLDER));
			lm.run();
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			formatter.printHelp("aethereal", opts);
		}
		
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
}
