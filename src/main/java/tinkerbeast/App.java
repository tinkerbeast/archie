package tinkerbeast;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.*;
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
  
  private Map<String, String> resourceData_ = new HashMap<>();
  // tentative fqan naming com.github/tinkerbeast/::archie-cpp-cmake-twolevel::latest
  //                       com.google.cloud.cproj::somearchetype::version
  // fqan - fully qualified archetype naming
  // constraint - provider, archetype name, version may not contain ::
  // Folder name for somearchetype::version is somearchetype/version

  
  private ResourceUtil ru_;
  private Map<String, String> data_;

  App(Map<String, String> archetypeGenerationParameters) throws IOException {
    this.ru_ = new ResourceUtil("archetypes");
    this.data_ = archetypeGenerationParameters;
  }

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
    InputStream descriptor = ru_.getResource("archie-resources", ARCHETYPE_DESCRIPTOR_FILENAME);
    JsonNode jsonMap = mapper.readValue(descriptor, JsonNode.class);
    JsonNode fileNode = jsonMap.get("files");
    List<FileTemplate> files = 
        mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {});
    for (FileTemplate fl : files) {
      System.out.format("DEBUG createResourceMap tmpl=(%s %s %s)%n", fl.name, fl.template, fl.type);
      final String resourceName = "resource-" + fl.name;
      if (fl.type.equals("template")) {
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(ru_.getResource("archie-resources", fl.template)));
        StringWriter writer = new StringWriter();
        generateFile(resourceName, reader, writer);
        resourceData_.put(resourceName, writer.toString());
      } else { // TODO: handle none type
        InputStream resource = ru_.getResource("archie-resources", fl.template);
        resourceData_.put(resourceName,
            new String(resource.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    data_.putAll(resourceData_);
  }

  private void generateFile(String name, Reader in, Writer out) throws IOException {
    MustacheFactory mf = new DefaultMustacheFactory();
    Mustache fileContents = mf.compile(in, name);
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


  public void generateArchetype() {
    generateArchetype(data_.get("output"), 0);
  }

  private void generateArchetype(String directoryContext, int recursionDepth) {
    // Check in cases archetypes are setup incorrectly.
    if (recursionDepth > 10) {
      throw new IllegalArgumentException("Too much recursion");
    }

    try {
      // Get the archetype.json as InputStream.
      InputStream descriptor = ru_.getResource(data_.get("archetype"), ARCHETYPE_DESCRIPTOR_FILENAME);
      if (null == descriptor) {
        throw new IllegalArgumentException("Archetype not found " + data_.get("archetype"));
      }
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
        System.out.format("DEBUG generateArchetype tmpl=(%s %s %s) out=%s%n", fl.name, fl.template, fl.type, fileOrDir.toString());
        
        if (fl.type.equals("template")) {
          fileOrDir.getParentFile().mkdirs();
          try (FileWriter writer = new FileWriter(fileOrDir)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(ru_.getResource(data_.get("archetype"), fl.template)));
            //generateFile(data_.get("archetype") + File.separator + fl.template, writer);
            String name = String.format("%s/%s", data_.get("archetype"), fl.template);
            generateFile(name, reader, writer);
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

  

  public static void main( String[] args ) throws Exception {     
    Map<String, String> options = ArgumentParser.parseArguments(args);
    if (options != null) {
      App app = new App(options);
      app.populateData();
      app.createResourceMap();
      app.generateArchetype();
    }
    
  }
}
