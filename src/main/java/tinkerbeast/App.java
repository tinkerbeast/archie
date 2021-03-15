package tinkerbeast;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.*;
import org.apache.commons.cli.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;


/**
 * Hello world!
 *
 */
public class App 
{
    static class FileTemplate {
        public String name;
        public String template;
    }

    void generateFile(String archetype, String fileNameTemplate, String fileTemplate, Map<String, String> data) throws IOException {
        MustacheFactory mf = new DefaultMustacheFactory();
        // Create the file name.
        StringWriter writer = new StringWriter();
        Mustache fileName = mf.compile(new StringReader(fileNameTemplate), fileNameTemplate);
        fileName.execute(writer, data).flush();
        // Create the file and directory.
        File file = new File(writer.toString());
        file.getParentFile().mkdirs(); 
        file.createNewFile();
        // Populate the file.
        Mustache fileContents = mf.compile(archetype + File.separator + fileTemplate);
        fileContents.execute(new FileWriter(file), data).flush();
    }

    void generateArchetype(String archetype, Map<String, String> data) {

        InputStream descriptor = getClass().getClassLoader()
            .getResourceAsStream(archetype + File.separator + "archetype.json");
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode jsonMap = mapper.readValue(descriptor, JsonNode.class);
            
            JsonNode fileNode = jsonMap.get("files");
            List<FileTemplate> files = mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {});
            for (FileTemplate fileTemplate : files) {
                System.out.format("%s %s%n", fileTemplate.name, fileTemplate.template);
                generateFile(archetype, fileTemplate.name, fileTemplate.template, data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main( String[] args ) throws ParseException {
        Option oNamespace = new Option("n", "namespace", true, "Namespace of the project");
        oNamespace.setRequired(true);
        Option oProject = new Option("p", "project", true, "Name of the project");
        oProject.setRequired(true);
        Option oArchetype = new Option("a", "archetype", true, "Archetype to generate from");
        oArchetype.setRequired(true);
        Option oArchetypeVersion = new Option("v", "archetype-version", true, "Archetype ver");

        Options options = new Options();
        options.addOption(oNamespace)
            .addOption(oProject)
            .addOption(oArchetype)
            .addOption(oArchetypeVersion);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse( options, args);

        
        String archetype = cmd.getOptionValue('a') + "-" + cmd.getOptionValue('v', "latest");
        System.out.println("RISHIN archetype=" + archetype);


        Map<String, String> data = new HashMap<>();
        data.put("namespace", cmd.getOptionValue("namespace"));
        data.put("project", cmd.getOptionValue("project"));

        App xxx = new App();
        xxx.generateArchetype(archetype, data);



        if (cmd.hasOption("n")) {
            System.out.println( "Hello World! supposedly with time" );
        } else {
            System.out.println( "Hello World!" );
        }
    }
}
