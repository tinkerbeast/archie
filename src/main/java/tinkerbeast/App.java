package tinkerbeast;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import tinkerbeast.generators.ArchetypeGenerator;
import tinkerbeast.generators.Config;


import picocli.CommandLine;

@SpringBootApplication
public class App implements CommandLineRunner, ExitCodeGenerator {

	static void run_cli(String[] args) throws IOException {
		Map<String, String> options = ArgumentParser.parseArguments(args);
		if (options != null) {
			ArchetypeGenerator generator = new ArchetypeGenerator(
					options.get("provider"), options.get("archetype"), options.get("version"));
			generator.generateArchetype(
					options.get("namespace"), options.get("project"), Paths.get(options.get("output")));
		}
	}

	static void run_predefined() throws IOException {
		Map<String, String> options = new HashMap<>();
		options.put("archetype", "cpp-cmake-simple");
		options.put("provider", Config.ARCHETYPE_DEFAULT_PROVIDER);
		options.put("namespace", "org.example");
		options.put("project", "myProject");
		options.put("output", System.getProperty("user.dir"));
		options.put("version", Config.ARCHETYPE_DEFAULT_VERSION);

		if (options != null) {
			ArchetypeGenerator generator = new ArchetypeGenerator(
					options.get("provider"), options.get("archetype"), options.get("version"));
			generator.generateArchetype(
					options.get("namespace"), options.get("project"), Paths.get(options.get("output")));
		}
	}

	int exitCode;

	@Override
	public void run(String... args) throws Exception {
		//run_cli(args);
		exitCode = new CommandLine(new GenerateCommand()).execute(args);
	}

	@Override
	public int getExitCode() {		
		return exitCode;
	}

	public static void main(String[] args) {
		System.exit(
				SpringApplication.exit(
						SpringApplication.run(App.class, args)));
	}

}
