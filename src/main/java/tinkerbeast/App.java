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
    public Map<String, String> data;
  }

  private static final String ARCHETYPE_DESCRIPTOR_FILENAME = "archetype.json";
  private static final String ARCHETYPE_DEFAULT_VERSION = "latest";
  private Map<String, String> data_ = new HashMap<>();
  private Map<String, String> resourceData_ = new HashMap<>();


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
    
    System.out.format("DEBUG cppHeaderGuard=%s %n", data_.get("cppHeaderGuard"));
  }

  private void createResourceMap() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    InputStream descriptor = getClass().getClassLoader()
      .getResourceAsStream("archie-resources" + File.separator + ARCHETYPE_DESCRIPTOR_FILENAME);
    JsonNode jsonMap = mapper.readValue(descriptor, JsonNode.class);
    JsonNode fileNode = jsonMap.get("files");
    List<FileTemplate> files = 
        mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {});
    for (FileTemplate fl : files) {
      String resourcePath = "archie-resources" + File.separator + fl.template;
      if (fl.type.equals("template")) {
        StringWriter writer = new StringWriter();
        generateFile(resourcePath, writer);
        resourceData_.put("resource-" + fl.name, writer.toString());
      } else { // TODO: handle none type
        InputStream resource = getClass().getClassLoader().getResourceAsStream(resourcePath);
        resourceData_.put("resource-" + fl.name,
            new String(resource.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    data_.putAll(resourceData_);
  }

  private void generateFile(String resourceName, Writer out) throws IOException {
    MustacheFactory mf = new DefaultMustacheFactory();
    Mustache fileContents = mf.compile(resourceName);
    fileContents.execute(out, data_).flush();
  }

  private static String stringTemplateToString(String stringTemplate, Map<String, String> data) 
      throws IOException {
    MustacheFactory mf = new DefaultMustacheFactory();
    // Create the file name.
    StringWriter writer = new StringWriter();
    Mustache fileName = mf.compile(new StringReader(stringTemplate), stringTemplate);
    fileName.execute(writer, data).flush();
    // Create the file and directory.
    return writer.toString();
  }

  private void generateArchetype(String directoryContext, int recursionDepth) {
    // Check in cases archetypes are setup incorrectly.
    if (recursionDepth > 10) {
      throw new IllegalArgumentException("Too much recursion");
    }
    // Get the archetype.json as InputStream.
    String archetypeScopedName = 
        data_.get("archetype") + File.separator + ARCHETYPE_DESCRIPTOR_FILENAME;
    InputStream descriptor = getClass().getClassLoader().getResourceAsStream(archetypeScopedName);
    if (null == descriptor) {
      throw new IllegalArgumentException("Archetype not found " + archetypeScopedName);
    }

    try {
      // Parse the archetype.json file. 
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonMap = mapper.readValue(descriptor, JsonNode.class);
      JsonNode fileNode = jsonMap.get("files");
      List<FileTemplate> files = 
          mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {});
      // For each entry in archetype.json file ...
      for (FileTemplate fl : files) {
        // The resource to generate.
        File fileOrDir = new File(stringTemplateToString(
              directoryContext + File.separator + fl.name, data_));
        
        // TODO(rishin): proper logging.
        System.out.format("DEBUG %s %s %s %n", fileOrDir.toString(), fl.template, fl.type);
        
        if (fl.type.equals("template")) {
          fileOrDir.getParentFile().mkdirs();
          try (FileWriter writer = new FileWriter(fileOrDir)) {
            generateFile(data_.get("archetype") + File.separator + fl.template, writer);
          }
        } else if (fl.type.equals("folder")) {
          fileOrDir.mkdirs();
        } else if (fl.type.equals("archetype")) {
          fileOrDir.mkdirs();
          String newNamespace = stringTemplateToString(fl.data.get("namespace"), data_);
          String newProject = stringTemplateToString(fl.data.get("project"), data_);
          // Cache the old data and copy it.
          Map<String, String> newData = new HashMap<>();
          newData.put("archetype", fl.template);
          newData.put("namespace", newNamespace);
          newData.put("project", newProject);
          newData.putAll(resourceData_);
          // Change the context
          Map<String, String> oldData = data_;
          data_ = newData;
          populateData();
          // Recurse to generate the archetype
          System.out.format("DEBUG cur=%s sub=%s %n", directoryContext, fileOrDir.getAbsolutePath());
          generateArchetype(fileOrDir.getAbsolutePath(), recursionDepth + 1);
          // Restore contexts
          data_ = oldData;
        } else if (fl.type.equals("resource")) {
          fileOrDir.getParentFile().mkdirs();
          try (FileWriter writer = new FileWriter(fileOrDir)) {
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
    String archetypeWithVersion = 
      cmd.getOptionValue('a') + "-" + cmd.getOptionValue('x', ARCHETYPE_DEFAULT_VERSION);
    data_.put("archetype", archetypeWithVersion);
    data_.put("namespace", cmd.getOptionValue("namespace"));
    data_.put("project", cmd.getOptionValue("project"));
  }

  public static void main( String[] args ) throws Exception {
    App app = new App();
    app.parseArguments(args);
    app.populateData();
    app.createResourceMap();
    String currentDir = System.getProperty("user.dir");
    app.generateArchetype(currentDir, 0);
  }
}
