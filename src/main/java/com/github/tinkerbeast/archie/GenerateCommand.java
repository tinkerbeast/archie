package com.github.tinkerbeast.archie;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.Callable;

import com.github.tinkerbeast.archie.generators.ArchetypeGenerator;
import com.github.tinkerbeast.archie.generators.Config;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "archie", mixinStandardHelpOptions = true, version = "???? version", description = "Genreate a new archetype.")
public class GenerateCommand implements Callable<Integer> {

    @Option(names = { "-n", "--namespace" }, required = true, description = "Namespace of the project")
    private String namespace;

    @Option(names = { "-p", "--project" }, required = true, description = "Name of the project")
    private String project;

    @Option(names = { "-a", "--archetype" }, required = true, description = "Archetype to generate from")
    private String qualifiedArchetype;

    @Option(names = { "-x", "--archetype-version" }, description = "Archetype version")
    private String archetypeVersion = Config.ARCHETYPE_DEFAULT_VERSION;

    @Option(names = { "-o", "--output" }, description = "Ouput directory")
    private Path output = Paths.get(".");

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        HashMap<String, String> options = new HashMap<>();
		options.put("archetype", "cpp-cmake-simple");
		options.put("provider", Config.ARCHETYPE_DEFAULT_PROVIDER);
		options.put("namespace", "org.example");
		options.put("project", "myProject");
		options.put("output", System.getProperty("user.dir"));
		options.put("version", Config.ARCHETYPE_DEFAULT_VERSION);

        // TODO: validations on each parameter
        String[] archetypeAndProvider = qualifiedArchetype.split("::");
        String archetype;
        String provider;
        if (archetypeAndProvider.length == 1) {
            archetype = archetypeAndProvider[0];
            provider = Config.ARCHETYPE_DEFAULT_PROVIDER;
        } else {
            archetype = archetypeAndProvider[1];
            provider = archetypeAndProvider[0];
        }
        ArchetypeGenerator generator = new ArchetypeGenerator(provider, archetype, archetypeVersion);
        generator.generateArchetype(namespace, project, output);
        return 0;
    }
}
