package tinkerbeast.generators;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;

import tinkerbeast.ResourceUtil;
import tinkerbeast.ds.LruCache;



public class ArchetypeGenerator {

  static class FileTemplate {
    public String name;
    public String template;
    public String type;
    public Map<String, String> data;
  }

  private static Logger logger_ = LoggerFactory.getLogger(ArchetypeGenerator.class);

  private static MustacheFactory templateEngine_ = new DefaultMustacheFactory();
  private static Map<String, String> cache_ = new LruCache<>(10); // TODO: proper sizing

  private String provider_;
  private String archetype_;
  private String fqan_;
  private ResourceUtil ru_;
  private Map<String, ArchetypeGenerator> subGens_;

  public ArchetypeGenerator(String provider, String archetype, String version) 
      throws IOException {
    Path archetypeAssetRelativePath = Paths.get(Config.providerToResource.get(provider), archetype, version);
    this.ru_ = new ResourceUtil(archetypeAssetRelativePath.toString());
    // tentative fqan naming com.github/tinkerbeast/archie.git::archie-cpp-cmake-twolevel::latest
    //                       org.example::somearchetype::version
    // fqan - fully qualified archetype naming
    // constraint - provider, archetype name, version may not contain ::
    //            - archetype name, version may not contain /
    //            - archetype name, version may not contain File.seperator
    //            - an archteype may not be named _resource
    // Folder name for somearchetype::version is somearchetype/version
    // TODO: have correct validations in argument parser.
    this.fqan_ = String.format("%s::%s::%s", provider, archetype, version);
    this.archetype_ = archetype;
    this.provider_ = provider;
    ResourceGenerator.usageHint(provider);
    this.subGens_ = new HashMap<>();
    
  }

  private Map<String, String> populateData(String namespace, String project) {
    Map<String, String> data_ = new HashMap<>();
    // for all
    data_.put("archetype", this.archetype_);
    data_.put("namespace", namespace);
    data_.put("project", project);
    // Case-augmented all
    data_.put("projectLower", project.toLowerCase());
    // for cpp
    data_.put("cppNamespace", namespace.replace(".", "::"));
    data_.put("cppHeaderGuard",
        (namespace.replace('.', '_') + "_" + project).toUpperCase());
    // for cmake
    data_.put("cmakeNamespace", namespace.replace('.', '-'));
    // date time
    String year = new SimpleDateFormat("yyyy").format(new Date());
    data_.put("time-year", year);

    return data_;
  }

  String stringTemplateToString(String template, Map<String, String> data) throws IOException {    
    String templateName = java.util.UUID.randomUUID().toString();
    StringReader reader = new StringReader(template);
    StringWriter writer = new StringWriter();
    templateEngine_.compile(reader, templateName).execute(writer, data).flush();
    return writer.toString();
  }

  String generateFile(String name, Path resource, Map<String, String> data) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(resource)) {
      StringWriter writer = new StringWriter();
      templateEngine_.compile(reader, name).execute(writer, data).flush();
      return writer.toString();
    }
  }

  public void generateArchetype(String namespace, String project, Path outRoot) throws IOException {
    this.generateArchetype_(namespace, project, outRoot, 0);
  }

  private void generateArchetype_(String namespace, String project, Path outRoot, int depth) throws IOException {
    if (depth > 10) {
      throw new IOException("Too much recursion in generation of archetypes");
    }
    // Populate namespace and project related data.
    Map<String, String> data = populateData(namespace, project);
    // Get the archetype.json.
    Path archetypeDescriptor = ru_.getAssetPath(Config.ARCHETYPE_DESCRIPTOR_FILENAME);
    // Parse the archetype.json file.
    List<FileTemplate> files;
    try (BufferedReader reader = Files.newBufferedReader(archetypeDescriptor)) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonMap = mapper.readValue(reader, JsonNode.class);
      JsonNode fileNode = jsonMap.get("files");
      files = mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {});
    }
    for (FileTemplate fl : files) {
      // The output path where resource will be genreated.
      String filePathParsed = stringTemplateToString(fl.name, data);
      Path out = Paths.get(outRoot.toString(), filePathParsed);
      logger_.debug("Resource output path determined : path={}", out);
      // Form fully qualified resource name.
      String fqrn;
      if (fl.type.equals("resource")) {
        fqrn = String.format("%s::*::*::%s/%s?n=%s&p=%s", provider_, fl.type, fl.template, namespace, project);
      } else {
        fqrn = String.format("%s::%s/%s?n=%s&p=%s", fqan_, fl.type, fl.template, namespace, project);
      }
      logger_.debug("Fully qualified resource name formed : fqrn={} resource={}", fqrn);
      // Handle each type of generation
      switch (fl.type) {
        case "folder": {
          out.toFile().getParentFile().mkdirs();
          break;
        }
        case "template":
        case "resource": {
          out.toFile().getParentFile().mkdirs();
          try (BufferedWriter w = Files.newBufferedWriter(out)) {
            String cachedOutput = cache_.get(fqrn);
            if (cachedOutput == null) {            
              Path resourcePath = fl.type.equals("template") ? 
                  ru_.getAssetPath(fl.template) : 
                  ResourceGenerator.get(this.provider_, fl.template);
              cachedOutput = generateFile(fqrn, resourcePath, data);
              cache_.put(fqrn, cachedOutput);
            }
            w.write(cachedOutput);
          }
          break;
        }
        case "archetype": {
          out.toFile().getParentFile().mkdirs();
          ArchetypeGenerator g = subGens_.get(fqrn);
          if (g == null) {
            // TODO: fix latest version hardcoding
            g = new ArchetypeGenerator(this.provider_, fl.template, "latest");
          }
          String newNamespace = stringTemplateToString(fl.data.get("namespace"), data);
          String newProject = stringTemplateToString(fl.data.get("project"), data);
          g.generateArchetype_(newNamespace, newProject, out, depth + 1);
          break;
        }
        default:
          throw new IllegalArgumentException("Template type or conversion not supported");
      }
    }

  }

}
