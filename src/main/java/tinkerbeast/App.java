package tinkerbeast;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.*;
import org.apache.commons.cli.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
    private static final String ARCHETYPE_DEFAULT_VERSION = "latest";
    private String archetype_;
    private Map<String, String> data_ = new HashMap<>();

    Map<String, String> convertForCmake() {
        Map<String, String> data = new HashMap<>(data_);
        String namespace = data.get("namespace");
        data.put("namespaceCmake", namespace.replace('.', '-'));
        return  data;
    }

    Map<String, String> convertForCpp() {
        Map<String, String> data = new HashMap<>(data_);
        String namespace = data.get("namespace");
        data.put("cppNamespace", namespace.replace(".", "::"));
        data.put("headerGuard", namespace.replace('.', '_').toUpperCase());
        return data;
    }

    Map<String, String> convertData(String conversion) {
        if (conversion == null) {
            return data_;
        } else if (conversion.equals("cmake")) {
            return convertForCmake();
        } else if (conversion.equals("cpp")) {
            return convertForCpp();
        } else {
            throw new IllegalArgumentException("Conversion type not supported");
        }
    }

    void createResourceMap() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream descriptor = getClass().getClassLoader()
            .getResourceAsStream("archie-resources" + File.separator + ARCHETYPE_DESCRIPTOR_FILENAME);
        JsonNode jsonMap = mapper.readValue(descriptor, JsonNode.class);
        JsonNode fileNode = jsonMap.get("files");
        List<FileTemplate> files = mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {});
        for (FileTemplate fl : files) {
            String resourcePath = "archie-resources" + File.separator + fl.template;
            InputStream resource = getClass().getClassLoader().getResourceAsStream(resourcePath);
            data_.put("resource-" + fl.name, 
                    new String(resource.readAllBytes(), StandardCharsets.UTF_8));
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
        ObjectMapper mapper = new ObjectMapper();
        InputStream descriptor = getClass().getClassLoader()
            .getResourceAsStream(archetype_ + File.separator + ARCHETYPE_DESCRIPTOR_FILENAME);
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
        // Create the options.
        Options options = new Options();
        options.addOption(OptionBuilder.withLongOpt("namespace")
                                .withDescription("Namespace of the project (required)")
                                .hasArg()
                                .withArgName("NAMESPACE")
                                .isRequired()
                                .create('n'));
         options.addOption(OptionBuilder.withLongOpt("project")
                                .withDescription("Name of the project (required)")
                                .hasArg()
                                .withArgName("PROJECT")
                                .isRequired()
                                .create('p'));
         options.addOption(OptionBuilder.withLongOpt("archetype")
                                .withDescription("Archetype to generate from (required)")
                                .hasArg()
                                .withArgName("ARCHETYPE")
                                .isRequired()
                                .create('a'));
         options.addOption(OptionBuilder.withLongOpt("archetype-version")
                                .withDescription("Archetype ver (default %s)".format(ARCHETYPE_DEFAULT_VERSION))
                                .hasArg()
                                .withArgName("ARCHETYPE-VERSION")
                                .create('x'));
         options.addOption(OptionBuilder.withLongOpt("help")
                                .withDescription("Print help")
                                .create('h'));
        // Try to parse the command line
        boolean printHelpAndExit = false;
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
            printHelpAndExit = (cmd.getOptionValue('h') != null);
        } catch (ParseException e) {
            e.printStackTrace();
            printHelpAndExit = true;
        }
        // Print help and exit if required
        if (printHelpAndExit) {
            String header = "Archetype generation system.\n\n";
            String footer = "\nPlease report issues at https://github.com/tinkerbeast/archie";
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("archie", header, options, footer, true);
            System.exit(1);
        }
        // Form values from the options.
        archetype_ = cmd.getOptionValue('a') + "-" + cmd.getOptionValue('x', ARCHETYPE_DEFAULT_VERSION);
        data_.put("namespace", cmd.getOptionValue("namespace"));
        data_.put("project", cmd.getOptionValue("project"));
    }

    public static void main( String[] args ) throws Exception {
        App xxx = new App();
        xxx.parseArguments(args);
        xxx.createResourceMap();
        xxx.generateArchetype();
    }
}
