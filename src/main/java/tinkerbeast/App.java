package tinkerbeast;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.*;
import org.apache.commons.cli.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.text.SimpleDateFormat;


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
    }

    private static final String ARCHETYPE_DESCRIPTOR_FILENAME = "archetype.json";
    private static final String ARCHETYPE_DEFAULT_VERSION = "latest";
    private String archetype_;
    private Map<String, String> data_ = new HashMap<>();


    private void populateData() {
        String namespace = data_.get("namespace");
        String project = data_.get("project");
        // for all
        data_.put("projectLower", project.toLowerCase());
        // for cpp
        data_.put("cppNamespace", namespace.replace(".", "_"));
        data_.put("cppHeaderGuard", 
                namespace.replace('.', '_').toUpperCase() + "_" + project.toUpperCase());
        // for cmake
        data_.put("cmakeNamespace", namespace.replace('.', '-'));
        // data time
        String year = new SimpleDateFormat("yyyy").format(new Date());
        data_.put("time-year", year); 
    }

    private void createResourceMap() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream descriptor = getClass().getClassLoader()
            .getResourceAsStream("archie-resources" + File.separator + ARCHETYPE_DESCRIPTOR_FILENAME);
        JsonNode jsonMap = mapper.readValue(descriptor, JsonNode.class);
        JsonNode fileNode = jsonMap.get("files");
        List<FileTemplate> files = mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {});
        for (FileTemplate fl : files) {
            String resourcePath = "archie-resources" + File.separator + fl.template;
            if (fl.type.equals("template")) {
                StringWriter writer = new StringWriter();
                generateFile(resourcePath, writer);
                data_.put("resource-" + fl.name, writer.toString());
            } else { // TODO: handle none type
                InputStream resource = getClass().getClassLoader().getResourceAsStream(resourcePath);
                data_.put("resource-" + fl.name,
                        new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    private void generateFile(String resourceName, Writer out) throws IOException {
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache fileContents = mf.compile(resourceName);
        fileContents.execute(out, data_).flush();
    }

    private File fileNameTemplateToFile(String fileNameTemplate) throws IOException {
        MustacheFactory mf = new DefaultMustacheFactory();
        // Create the file name.
        StringWriter writer = new StringWriter();
        Mustache fileName = mf.compile(new StringReader(fileNameTemplate), fileNameTemplate);
        fileName.execute(writer, data_).flush();
        // Create the file and directory.
        File file = new File(writer.toString());
        return file;
    }

    private void generateArchetype() {
        ObjectMapper mapper = new ObjectMapper();
        String archetypeScopedName = archetype_ + File.separator + ARCHETYPE_DESCRIPTOR_FILENAME;
        InputStream descriptor = getClass().getClassLoader().getResourceAsStream(archetypeScopedName);
        try {
        if (null == descriptor) {
        throw new IllegalArgumentException("Archetype not found " + archetypeScopedName);
        }
            JsonNode jsonMap = mapper.readValue(descriptor, JsonNode.class);
            JsonNode fileNode = jsonMap.get("files");
            List<FileTemplate> files = mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {});
            for (FileTemplate fl : files) {
                // TODO(rishin): proper logging.
                File file = fileNameTemplateToFile(fl.name);
                System.out.format("DEBUG %s %s %s %n", file.toString(), fl.template, fl.type);
                if (fl.type.equals("template")) {
                    file.getParentFile().mkdirs();
                    try (FileWriter writer = new FileWriter(file)) {
                        generateFile(archetype_ + File.separator + fl.template, writer);
                    }
                } else if (fl.type.equals("folder")) {
                    file.mkdirs();
                } else if (fl.type.equals("resource")) {
                    file.getParentFile().mkdirs();
                    try (FileWriter writer = new FileWriter(file)) {
                        String content = data_.get("resource-" + fl.template);
                        writer.write(content);
                    }
                } else {
                    throw new IllegalArgumentException("Template type or conversion not supported");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void parseArguments(String[] args) throws ParseException {
        // Create the options.
        Options options = new Options();
        options.addOption(Option.builder("n")
                                .desc("Namespace of the project (required)")
                                .hasArg()
                                .argName("NAMESPACE")
                                .required()
                                .longOpt("namespace")
                                .build());
         options.addOption(Option.builder("p")
                                .desc("Name of the project (required)")
                                .hasArg()
                                .argName("PROJECT")
                                .required()
                                .longOpt("project")
                                .build());
         options.addOption(Option.builder("a")
                                .desc("Archetype to generate from (required)")
                                .hasArg()
                                .argName("ARCHETYPE")
                                .required()
                                .longOpt("archetype")
                                .build());
         options.addOption(Option.builder("x")
                                .desc(String.format("Archetype ver (default %s)", ARCHETYPE_DEFAULT_VERSION))
                                .hasArg()
                                .argName("ARCHETYPE-VERSION")
                                .longOpt("archetype-version")
                                .build());
         options.addOption(Option.builder("h")
                                .desc("Print help")
                                .longOpt("help")
                                .build());
        // Try to parse the command line.
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
        // Print help and exit if required.
        if (printHelpAndExit) {
            String header = "Archetype generation system.\n\n";
            String footer = "\nPlease report issues at https://github.com/tinkerbeast/archie";
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("archie", header, options, footer, true);
            System.exit(1);
        }
        // Form values from the options.
        archetype_ = cmd.getOptionValue('a') + "-" + cmd.getOptionValue('x', ARCHETYPE_DEFAULT_VERSION);
        data_.put("archetype", archetype_);
        data_.put("namespace", cmd.getOptionValue("namespace"));
        data_.put("project", cmd.getOptionValue("project"));
    }

    public static void main( String[] args ) throws Exception {
        App app = new App();
        app.parseArguments(args);
        app.populateData();
        app.createResourceMap();
        app.generateArchetype();
    }
}
