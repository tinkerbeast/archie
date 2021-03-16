package tinkerbeast;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.*;
import org.apache.commons.cli.*;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Hello world!
 *
 */
public class App 
{
    static class FileTemplate {
        public String name;
        public String template;
        public String type;
        public String conversion;
    }

    private static final String ARCHETYPE_DESCRIPTOR_FILENAME = "archetype.json";
    private String archetype_;
    private Map<String, String> data_ = new HashMap<>();

    Map<String, String> convertForCmake() {
        Map<String, String> data = new HashMap<>(data_);
        String namespace = data.get("namespace");
        namespace = namespace.replace('.', '-');
        data.put("namespace", namespace);
        return  data;
    }

    Map<String, String> convertData(String conversion) {
        if (conversion == null) {
            return data_;
        } else if (conversion.equals("cmake")) {
            return convertForCmake();
        } else {
            throw new IllegalArgumentException("Conversion type not supported");
        }
    }

    void generateFile(String fileNameTemplate, String fileTemplate, Map<String, String> data) throws IOException {
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
        Mustache fileContents = mf.compile(archetype_ + File.separator + fileTemplate);
        fileContents.execute(new FileWriter(file), data).flush();
    }

    void generateArchetype() {
        InputStream descriptor = getClass().getClassLoader()
            .getResourceAsStream(archetype_ + File.separator + ARCHETYPE_DESCRIPTOR_FILENAME);
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode jsonMap = mapper.readValue(descriptor, JsonNode.class);
            JsonNode fileNode = jsonMap.get("files");
            List<FileTemplate> files = mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {});
            for (FileTemplate fl : files) {
                // TODO(rishin): proper logging.
                System.out.format("%s %s %s %s %n",
                        fl.name, fl.template,
                        fl.type, fl.conversion);
                if (fl.type.equals("file")) {
                    generateFile(fl.name, fl.template, convertData(fl.conversion));
                } else {
                    throw new IllegalArgumentException("Template type not supported");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void parseArguments(String[] args) throws ParseException {
        // Command line options.
        Option oNamespace = new Option("n", "namespace", true, "Namespace of the project");
        oNamespace.setRequired(true);
        Option oProject = new Option("p", "project", true, "Name of the project");
        oProject.setRequired(true);
        Option oArchetype = new Option("a", "archetype", true, "Archetype to generate from");
        oArchetype.setRequired(true);
        Option oArchetypeVersion = new Option("v", "archetype-version", true, "Archetype ver");
        // Parse the options.
        Options options = new Options();
        options.addOption(oNamespace)
                .addOption(oProject)
                .addOption(oArchetype)
                .addOption(oArchetypeVersion);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse( options, args);
        // Form values from the options.
        archetype_ = cmd.getOptionValue('a') + "-" + cmd.getOptionValue('v', "latest");
        data_.put("namespace", cmd.getOptionValue("namespace"));
        data_.put("project", cmd.getOptionValue("project"));
    }

    public static void main( String[] args ) throws Exception {
        App xxx = new App();
        xxx.parseArguments(args);
        xxx.generateArchetype();
    }
}
