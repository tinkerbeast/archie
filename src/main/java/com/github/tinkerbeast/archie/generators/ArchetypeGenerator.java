package com.github.tinkerbeast.archie.generators;


import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import com.github.tinkerbeast.archie.JarUtil;
import com.github.tinkerbeast.archie.ds.ComputableMap;
import com.github.tinkerbeast.archie.ds.LruCache;

public class ArchetypeGenerator implements Generator {

  static class ArchetypeEntry {
    public String name;
    public String template;
    public String type;
    public Map<String, String> data;
  }

  class TemplateConversion {
    String name;
    Path input;
    Path output;

    TemplateConversion(String name, Path input, Path output) {
      this.name = name;
      this.input = input;
      this.output = output;
    }
  }

  private static Logger logger_ = LoggerFactory.getLogger(ArchetypeGenerator.class);

  private static Map<String, Generator> archetypeCache_ = new LruCache<>(20);

  private String provider_;
  private String archetype_;
  private String version_;

  private String fqan_;
  private String fqprn_;
  private JarUtil jarUtil_;
  private MustacheFactory templateEngine_;

  public ArchetypeGenerator(String provider, String archetype, String version)
      throws IOException {
    // tentative fqan naming
    // com.github/tinkerbeast/archie.git::cpp-cmake-twolevel::latest
    // org.example::somearchetype::version
    // fqan - fully qualified archetype naming
    // constraint - provider, archetype name, version may not contain ::
    // - archetype name, version may not contain /
    // - archetype name, version may not contain File.seperator
    // - an archteype may not be named _resource
    // Folder name for somearchetype::version is somearchetype/version
    // TODO: have correct validations in argument parser.
    
    logger_.info("ArchetypeGenerator instance being created : provider={} archetype={} version={}", 
      provider, archetype, version);

    // Contextual names are stored.
    this.provider_ = provider;
    this.archetype_ = archetype;
    this.version_ = version;

    // Forming fully qualified names helps with caching.
    // Fqan naming format is <provider>::<archetype>@<version>. Example -
    //   com.github/tinkerbeast/archie.git::archie-cpp-cmake-twolevel::latest
    //   org.example::somearchetype::version    
    this.fqan_ = ArchetypeGenerator.formFqan(provider, archetype, version);

    // Fqrn naming format is <provider>::resource
    // Getting the fqrn also creates the resource generator if necessary.
    this.fqprn_ = ResourceGenerator.instance(provider).getFqrn();

    // Utility to load configs and template from jar package.
    Path archetypePath = Paths.get(Config.providerToRoot.get(provider), archetype, version);
    this.jarUtil_ = new JarUtil(archetypePath.toString());
    logger_.debug("jar util initialised : fqan={} jarRoot={}", this.fqan_, archetypePath);
    
    // Template engine with custome name lookup. // TODO: make this global
    templateEngine_ = new DefaultMustacheFactory();

  }

  

  public void generateArchetype(String namespace, String project, Path outRoot) 
    throws IOException {

    // Template parameter data.
    Map<String, String> staticData = populateData_(namespace, project);
    ComputableMap<String, String> data = new ComputableMap<>(key -> {
      if (key.startsWith("::resource/")) {        
        String template = key.substring("::resource/".length());
        Path templatePath = ResourceGenerator.instance(this.provider_).resolveResourcePath(template);
        String templateName = this.fqprn_ + "/" + template;
        try {
          String out = fileTemplateToString_(templatePath, templateName, staticData);
          return out;
        } catch(IOException e) {
          logger_.error("IOException while processing partial ::resource/ : key={}", key);
          return String.format("# ERROR key=%s", key);
        }
      } else {
        return staticData.get(key);
      }
    });

    // Get entries from the archetype.json descriptor.
    Path archetypeJsonPath = jarUtil_.getAssetPath(Config.ARCHETYPE_DESCRIPTOR_FILENAME);
    logger_.debug("archetype.json will be read for given archetype : fqan={} jsonpath={}", 
      this.fqan_, archetypeJsonPath);
    List<ArchetypeEntry> entries = ArchetypeGenerator.getEntries(archetypeJsonPath);
    logger_.debug("archetype.json is read : item_count={}", entries.size());

    // Handle folders
    List<Path> folders = entries.stream()
      .filter(e -> e.type.equals("folder"))
      .map(e -> {
        String outFile = ArchetypeGenerator.stringTemplateToString(e.name, data);
        Path outPath = outRoot.resolve(outFile);
        return outPath;
      })
      .collect(Collectors.toUnmodifiableList());
    for (Path e : folders) {
      logger_.debug("archetype.json directory entry : directory={}", e);
      Files.createDirectories(e);
    }    

    // Preprocess templates.
    List<TemplateConversion> templates = entries.stream()
      .filter(e -> e.type.equals("template"))
      .map(e -> {
        logger_.debug("archetype.json template entry : template={}", e.template);
        Path templatePath = jarUtil_.getAssetPath(e.template);
        String templateName = this.fqan_ + "/" + e.template;
        String outFile = ArchetypeGenerator.stringTemplateToString(e.name, data);
        Path outPath = outRoot.resolve(outFile);
        return new TemplateConversion(templateName, templatePath, outPath);
      })
      .collect(Collectors.toUnmodifiableList());
    // Preprocess resources.
    List<TemplateConversion> resources = entries.stream()
    .filter(e -> e.type.equals("resource"))
    .map(e -> {
      logger_.debug("archetype.json resource entry : resource={}", e.template);
      Path templatePath = ResourceGenerator.instance(this.provider_).resolveResourcePath(e.template);
      String templateName = this.fqprn_ + "/" + e.template;
      String outFile = ArchetypeGenerator.stringTemplateToString(e.name, data);
      Path outPath = outRoot.resolve(outFile);
      return new TemplateConversion(templateName, templatePath, outPath);
      // TODO: differentiate between noprocess and pre-process
    })
    .collect(Collectors.toUnmodifiableList());
    
    // Do template conversion.
    List<TemplateConversion> all = Stream.of(templates, resources)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());    
    for (TemplateConversion e : all) {
      logger_.debug("Compiling and writing processed output : name={} in={} out={}", 
        e.name, e.input, e.output);
      fileTemplateToFile_(e.input, e.output, e.name, data);
    }
    
    // Handle recursive archetypes.
    entries.stream()
    .filter(e -> e.type.equals("archetype"))
    .forEach(e -> {
      // Provider, archetype, version
      String qualifiedArchetype = e.template;
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
      String version = e.data.getOrDefault("version", Config.ARCHETYPE_DEFAULT_VERSION);
      logger_.debug("Recursive archetype : fqan={}::{}@{}", provider, archetype, version);
      // Namespace, project, outRoot
      String ns = ArchetypeGenerator.stringTemplateToString(e.data.get("namespace"), data);
      String pr = ArchetypeGenerator.stringTemplateToString(e.data.get("project"), data);
      String outFile = ArchetypeGenerator.stringTemplateToString(e.name, data);
      Path out = outRoot.resolve(outFile);
      logger_.debug("Recursive archetype generation params : ns={} pr={} out={}", 
        ns, pr, out);

      // TODO TODO TODO cache this
      try {
        new ArchetypeGenerator(provider, archetype, version).generateArchetype(ns, pr, out);
      } catch(IOException ex) {
        throw new RuntimeException(ex);
      }
    });

  }

  private static String formFqan(String provider, String archetype, String version) {
    // Note: FQAN - Fully qualified archetype name
    return String.format("%s::%s@%s", provider, archetype, version);
  }

  private static List<ArchetypeEntry> getEntries(Path archetypeDescriptor) throws IOException {
    // Parse the archetype.json file.
    List<ArchetypeEntry> files;
    try (Reader reader = Files.newBufferedReader(archetypeDescriptor)) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonMap = mapper.readValue(reader, JsonNode.class);
      JsonNode fileNode = jsonMap.get("files");
      files = mapper.convertValue(fileNode, new TypeReference<List<ArchetypeEntry>>() {
      });
    }
    return files;
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

  private Map<String, String> populateData_(String namespace, String project) {
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
    String year = new SimpleDateFormat("yyyy").format(new Date()); // TODO: fix fringe case where date time will varie.
                                                                   // Create singleton.
    data.put("time-year", year);
    //
    logger_.debug("Template dictionary data populated : data={}", data);
    return data;
  }

  private static String stringTemplateToString(String template, Map<String, String> data) {
    // Note: StringReader, StringWriter do not hold resources and Closeable
    // interface is non-functional.
    String templateName = java.util.UUID.randomUUID().toString(); // TODO: won't there be better performance if
                                                                  // `template` value is used as name?
    StringReader reader = new StringReader(template);
    StringWriter writer = new StringWriter();
    Mustache xx = new DefaultMustacheFactory().compile(reader, templateName);

    xx.execute(writer, data)/* .flush() */; // TODO: can we do without flush?
    return writer.toString();
  }

  private void fileTemplateToFile_(Path input, Path output, String name, Map<String, String> data) throws IOException {
    Mustache template = null;
    // TODO: Mustache cache for global compilation TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO
    try (Reader reader = Files.newBufferedReader(input)) {
      template = templateEngine_.compile(reader, name);        
    }
    Files.createDirectories(output.getParent());
    try (Writer writer = Files.newBufferedWriter(output)) {
      template.execute(writer, data);
    }
  }

  private String fileTemplateToString_(Path input, String name, Map<String, String> data) throws IOException {
    Mustache template = null;
    // TODO: Mustache cache for global compilation TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO
    try (Reader reader = Files.newBufferedReader(input)) {
      template = templateEngine_.compile(reader, name);        
    }
    StringWriter writer = new StringWriter();
    template.execute(writer, data);
    return writer.toString();
  }

  @Override
  public String getName() {
    return this.fqan_;
  }

}
