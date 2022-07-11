package com.github.tinkerbeast.archie.generators;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.tinkerbeast.archie.ResourceUtil;
import com.github.tinkerbeast.archie.ds.ComputableMap;
import com.github.tinkerbeast.archie.ds.LruCache;


public class ArchetypeGenerator {

  static class FileTemplate {
    public String name;
    public String template;
    public String type;
    public Map<String, String> data;
  }

  private static Logger logger_ = LoggerFactory.getLogger(ArchetypeGenerator.class);

  private static MustacheFactory templateEngine_ = new DefaultMustacheFactory();
  private static Map<String, Mustache> cache_ = new LruCache<>(20); // TODO: proper sizing

  private String provider_;
  private String archetype_;
  private String fqan_;
  private ResourceUtil ru_;
  private ResourceGenerator resGen_;
  

  public ArchetypeGenerator(String provider, String archetype, String version) 
      throws IOException {
    Path archetypeAssetRelativePath = Paths.get(Config.providerToResource.get(provider), archetype, version);
    logger_.info("## Instance created ##  : provider={} archetype={} version={} ru_path={}", 
        provider, archetype, version, archetypeAssetRelativePath);
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
    this.resGen_ = ResourceGenerator.getGenerator(provider);
  }

  private Function<String, String> resourceMapper(Map<String, String> data) {
    return (key) -> {
      logger_.warn(">>>>>>>>>>>>>>>>>>> resourceMapper called <<<<<<<<<<<<<<<<<<<<<<<<<<");
      String out = data.get(key);
      if (null != out) {
        return out;
      } else {
        return String.format("Resource mapper called for a=%s n=%s p=%s", 
          data.get("archetype"), data.get("namespace"), data.get("project"));
      }
      
    };
  }

  private Map<String, String> populateData(String namespace, String project) {
    Map<String, String> data = new HashMap<>();
    // for all
    data.put("archetype", this.archetype_);
    data.put("namespace", namespace);
    data.put("project", project);    
    // Case-augmented all
    data.put("projectLower", project.toLowerCase());
    // for cpp
    data.put("cppNamespace", namespace.replace(".", "::"));
    data.put("cppHeaderGuard",
        (namespace.replace('.', '_') + "_" + project).toUpperCase());
    // for cmake
    data.put("cmakeNamespace", namespace.replace('.', '-'));
    // date time
    String year = new SimpleDateFormat("yyyy").format(new Date());
    data.put("time-year", year);
    //
    logger_.debug("Contextual archetype mapping created : data={}", data);
    return data;
  }

  static String stringTemplateToString(String template, Map<String, String> data) throws IOException {    
    // Note: StringReader, StringWriter do not hold resources and Closeable interface is non-functional.
    String templateName = java.util.UUID.randomUUID().toString(); // TODO: won't there be better performance if 
                                                                  // `template` value is used as name?
    StringReader reader = new StringReader(template);
    StringWriter writer = new StringWriter();
    templateEngine_.compile(reader, templateName).execute(writer, data).flush();
    return writer.toString();
  }

  static Mustache generateTemplate(String name, Path resource) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(resource)) {
      Mustache template = templateEngine_.compile(reader, name);
      return template;
    }
  }

  void generateFile(Writer writer, final Mustache template, final Map<String, String> data) 
      throws IOException {

    // 1..1 with this
    Function<String, String> mapper = (String key) -> {      
      if (key.startsWith("::resource/")) { // TODO: resouces from other providers
        key = key.substring("::resource/".length());
        String resName = this.resGen_.lookup(key);
        if (resName == null) {
          return null;
        }
        String fqrn = String.format("%s::*::*::resource/%s", provider_, resName);
        Mustache tmpl = cache_.get(fqrn);
        StringWriter out = new StringWriter();
        if (tmpl == null) {
          try {
            Path resPath = this.resGen_.get(key);
            logger_.warn("Generating fqrn={} path={}", fqrn, resName);
            tmpl = generateTemplate(fqrn, resPath);
            cache_.put(fqrn, tmpl);
            
            tmpl.execute(out, data).flush();
          } catch(IOException e) {
            logger_.error(e.getMessage());
            System.exit(1);
          }
        }
        return out.toString();
      } else {
        return data.get(key);
      }
    };
    
    Map<String, String> context = new ComputableMap<>(mapper);
    template.execute(writer, context);
  }

  public void generateArchetype(String namespace, String project, Path outRoot) throws IOException {
    this.generateArchetype_(namespace, project, outRoot, 0);
  }

  private void generateArchetype_(String namespace, String project, Path outRoot, int depth) throws IOException {
    logger_.info("## Generating archetype ##  : namespace={} project={} outRoot={}", namespace, project, outRoot);
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
      logger_.debug("File output path determined : file={} path={}", fl.name, out);
      // Form fully qualified resource name.
      String fqrn;
      if (fl.type.equals("resource")) {
        String resName = this.resGen_.lookup(fl.template);
        fqrn = String.format("%s::*::*::resource/%s", provider_, resName);
      } else {
        fqrn = String.format("%s::%s/%s", fqan_, fl.type, fl.template);
      }
      logger_.debug("Fully qualified resource name formed : fqrn={}", fqrn);
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
            Mustache cachedOutput = cache_.get(fqrn);
            if (cachedOutput == null) {            
              Path resourcePath = fl.type.equals("template") ? 
                  ru_.getAssetPath(fl.template) : 
                  this.resGen_.get(fl.template); // TODO: slight inefficiency in the fact that resource generator is looked up twice.
              cachedOutput = generateTemplate(fqrn, resourcePath);
              cache_.put(fqrn, cachedOutput);
            }
            generateFile(w, cachedOutput, data);
          }
          break;
        }
        case "archetype": {
          out.toFile().getParentFile().mkdirs();
          ArchetypeGenerator g = new ArchetypeGenerator(this.provider_, fl.template, "latest"); // TODO: cache this          
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
