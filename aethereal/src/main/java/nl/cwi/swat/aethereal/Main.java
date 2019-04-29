package nl.cwi.swat.aethereal;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

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
				.addOption(Option.builder("remote")
						.desc("Fetch artifact information from Maven Central / mvnrepository.com").build())
				.addOption(Option.builder("local")
						.desc("Fetch artifact information from a local copy of the Maven Dependency Graph").build());
		method.setRequired(true);

		Options opts = new Options()
				.addOption(Option.builder("groupId").desc("groupId of the artifact to be analyzed").hasArg()
						.argName("groupId").required().build())
				.addOption(Option.builder("artifactId").desc("artifactId of the artifact to be analyzed").hasArg()
						.argName("artifactId").required().build())
				.addOption(Option.builder("download").desc("Download JARs locally").build())
				.addOption(Option.builder("datasetPath").hasArg().argName("path")
						.desc("Relative path to where the dataset should be stored (default is 'dataset')").build())
				.addOption(Option.builder("m3").desc("Serialize the M3 models of all JARs").build())
				.addOptionGroup(method)
				.addOption(
						Option.builder("v1").hasArg().argName("libV1").desc("Initial version of the library").build())
				.addOption(
						Option.builder("v2").hasArg().argName("libV2").desc("Evolved version of the library").build());

		try (FileInputStream fis = new FileInputStream("aethereal.properties")) {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse(opts, args);
			Properties props = new Properties();
			props.load(fis);

			if (props.containsKey("remote")) {
				Aether.REMOTE_URL = props.getProperty("remote");
			}

			int aetherQps = Integer.parseInt(props.getProperty("aether.qps", "4"));
			int jsoupQps = Integer.parseInt(props.getProperty("jsoup.qps", "4"));

			MavenCollector collector = cmd.hasOption("remote") ? new AetherCollector(aetherQps, jsoupQps)
					: new LocalCollector();
			AetherDownloader downloader = new AetherDownloader(aetherQps);
			String coordinates = String.format("%s:%s", cmd.getOptionValue("groupId"),
					cmd.getOptionValue("artifactId"));

			String path = cmd.getOptionValue("datasetPath", "dataset");
			MavenDataset dt = new MavenDataset(coordinates, path, collector, downloader);
			boolean pair = cmd.hasOption("v1") && cmd.hasOption("v2");
			boolean single = cmd.hasOption("v1") && !cmd.hasOption("v2");
			if (pair) {
				String v1 = cmd.getOptionValue("v1", "libV1");
				String v2 = cmd.getOptionValue("v2", "libV2");
				dt.build(v1, v2);
			}
			if (single) {
				String v1 = cmd.getOptionValue("v1", "libV1");
				dt.build(v1);
			}
			if (!single && !pair) {
				dt.build();
			}
			dt.printStats();

			if (cmd.hasOption("download")) {
				if (!pair & !single)
					dt.downloadAll();
				if (single & !pair)
					dt.downloadLibrary();
				if (pair)
					dt.download();
			}
			if (cmd.hasOption("m3"))
				dt.writeM3s();

		} catch (ParseException e) {
			logger.error(e.getMessage());
			formatter.printHelp("aethereal", opts);
		} catch (IOException e) {
			logger.error(e);
		}
	}

	public static void main(String[] args) {
		Main main = new Main();
		main.run(args);
	}
}
