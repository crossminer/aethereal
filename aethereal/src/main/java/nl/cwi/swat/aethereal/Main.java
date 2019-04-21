package nl.cwi.swat.aethereal;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
	private static final Logger logger = LogManager.getLogger(Main.class);

	public void run(String[] args) {
		HelpFormatter formatter = new HelpFormatter();

		OptionGroup method = new OptionGroup()
			.addOption(Option
				.builder("remote")
				.desc("Fetch artifact information from Maven Central / mvnrepository.com")
				.build())
			.addOption(Option
				.builder("local")
				.desc("Fetch artifact information from a local copy of the Maven Dependency Graph")
				.build());
		method.setRequired(true);

		Options opts = new Options()
			.addOption(Option
				.builder("groupId")
				.desc("groupId of the artifact to be analyzed")
				.hasArg()
				.argName("groupId")
				.required()
				.build())
			.addOption(Option
				.builder("artifactId")
				.desc("artifactId of the artifact to be analyzed")
				.hasArg()
				.argName("artifactId")
				.required()
				.build())
			.addOption(Option
				.builder("download")
				.desc("Set this option to download all JARs locally")
				.build())
			.addOption(Option
				.builder("downloadPath")
				.hasArg()
				.argName("path")
				.desc("Relative path to where the dataset should be downloaded")
				.build())
			.addOptionGroup(method);

		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse(opts, args);

			MavenCollector collector = cmd.hasOption("remote") ? new AetherCollector() : new LocalCollector();
			String coordinates = String.format("%s:%s", cmd.getOptionValue("groupId"),
					cmd.getOptionValue("artifactId"));

			MavenDataset dt = new MavenDataset(coordinates, collector);
			dt.build();
			dt.printStats();

			if (cmd.hasOption("download")) {
				String path = cmd.getOptionValue("downloadPath", "download");
				dt.download(path);
			}
		} catch (ParseException e) {
			logger.error(e.getMessage());
			formatter.printHelp("aethereal", opts);
		}
	}

	public static void main(String[] args) {
		Main main = new Main();
		main.run(args);
	}
}
