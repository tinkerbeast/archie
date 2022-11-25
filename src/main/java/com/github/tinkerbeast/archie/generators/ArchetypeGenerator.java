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
import com.github.mustachejava.MustacheResolver;

import com.github.tinkerbeast.archie.JarUtil;
import com.github.tinkerbeast.archie.ds.ComputableMap;
import com.github.tinkerbeast.archie.ds.LruCache;

public class ArchetypeGenerator implements Generator {

  static class FileTemplate {
    public String name;
    public String template;
    public String type;
    public Map<String, String> data;
  }

  private static Logger logger_ = LoggerFactory.getLogger(ArchetypeGenerator.class);

  //private static Map<String, Mustache> cache_ = new HashMap<>();

  private static Map<String, Generator> generatorCache_ = new LruCache<>(20);

  private String provider_;
  private String archetype_;
  private String version_;

  private String fqan_;
  private String fqprn_;
  private JarUtil jarUtil_;
  private ResourceGenerator resGen_;
  private MustacheFactory templateEngine_;

  public ArchetypeGenerator(String provider, String archetype, String version)
      throws IOException {
    // tentative fqan naming
    // com.github/tinkerbeast/archie.git::archie-cpp-cmake-twolevel::latest
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

    // Utility to load configs and template from jar package.
    Path archetypePath = Paths.get(Config.providerToRoot.get(provider), archetype, version);
    this.jarUtil_ = new JarUtil(archetypePath.toString());

    // Forming fully qualified names helps with caching.
    // Fqan naming format is <provider>::<archetype>@<version>. Example -
    //   com.github/tinkerbeast/archie.git::archie-cpp-cmake-twolevel::latest
    //   org.example::somearchetype::version    
    this.fqan_ = ArchetypeGenerator.formFqan(provider, archetype, version);

    // Fqrn naming format is <provider>::resource
    // Getting the fqrn also creates the resource generator if necessary.
    this.fqprn_ = ResourceGenerator.getGenerator(provider).getFqrn();
    
    
    // Template engine with custome name lookup.
    /*
    templateEngine_ = new DefaultMustacheFactory(new MustacheResolver() {
      @Override
      public Reader getReader(String resourceName) {
        logger_.warn("Mustache resolver called : fqan={} resourceName={}", fqan_, resourceName);
        return null; // TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO
        // TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO
      }
    });
    */
    templateEngine_ = new DefaultMustacheFactory();

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

  public void generateArchetype(String namespace, String project, Path outRoot) 
    throws IOException {

    Map<String, String> data = populateData_(namespace, project);

    Path archetypeJsonPath = jarUtil_.getAssetPath(Config.ARCHETYPE_DESCRIPTOR_FILENAME);
    logger_.debug("archetype.json will be read for given archetype : fqan={} jsonpath={}", 
      this.fqan_, archetypeJsonPath);
    List<FileTemplate> entries = ArchetypeGenerator.getEntries(archetypeJsonPath);
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
      Files.createDirectories(e);
    }    

    // Preprocess templates.
    List<TemplateConversion> templates = entries.stream()
      .filter(e -> e.type.equals("template"))
      .map(e -> {
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
      Path templatePath = ResourceGenerator.getGenerator(this.provider_).resolveResourcePath(e.template);
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
      Mustache template = null;
      // TODO: Mustache cache for global compilation TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO
      try (Reader reader = Files.newBufferedReader(e.input)) {
        template = templateEngine_.compile(reader, e.name);
      }
      Files.createDirectories(e.output.getParent());
      try (Writer writer = Files.newBufferedWriter(e.output)) {
        template.execute(writer, data);
      }
    }
    
    // Handle recursion - TODO
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

  private static List<FileTemplate> getEntries(Path archetypeDescriptor) throws IOException {
    // Parse the archetype.json file.
    List<FileTemplate> files;
    try (Reader reader = Files.newBufferedReader(archetypeDescriptor)) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonMap = mapper.readValue(reader, JsonNode.class);
      JsonNode fileNode = jsonMap.get("files");
      files = mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {
      });
    }
    return files;
  }

  private static String formFqrn(String provider, String archetype, String version, String resource) {
    return String.format("%s/%s", ArchetypeGenerator.formFqan(provider, archetype, version), resource);
  }

  private String getFqrn(String resource) {
    return String.format("%s/%s", fqan_, resource);
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
    logger_.debug("Contextual archetype mapping created : data={}", data);
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
/*
  Mustache generateTemplate(String name, Path resource) throws IOException {
    try (Reader reader = Files.newBufferedReader(resource)) {
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
          } catch (IOException e) {
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
*/  

  
/*
  private void generateArchetype2_(String namespace, String project, Path outRoot, int depth) throws IOException {
    logger_.info("Generating archetype : namespace={} project={} outRoot={}", namespace, project, outRoot);
    if (depth > 10) {
      throw new IOException("Too much recursion in generation of archetypes");
    }

    // Populate namespace and project related data.
    Map<String, String> data = populateData(namespace, project);

    // Parse the archetype.json file.
    Path archetypeDescriptor = jarUtil_.getAssetPath(Config.ARCHETYPE_DESCRIPTOR_FILENAME);
    List<FileTemplate> entries = ArchetypeGenerator.getEntries(archetypeDescriptor);
    entries.stream()
        .filter(e -> e.type.equals("template"))
        .map(e -> {
          // Get fully qualified names and path from entry.
          String fqrn = this.getFqrn(e.template);
          String filePathParsed = stringTemplateToString(e.name, data);
          Path out = Paths.get(outRoot.toString(), filePathParsed);
          Mustache template = cache_.get(fqrn);
          logger_.debug("Currently generating templte : fqrn={} out={} cached={}", fqrn, out, template);
          // Create mustache template paired to fqrn.          
          if (null == template) {
            template = this.templateEngine_.compile(fqrn);
            cache_.put(fqrn, template);
          }
          return Map.entry(template, out);
        }).forEach(pair -> {          
          Path out = pair.getValue();
          out.toFile().getParentFile().mkdirs();          
          // 
          Mustache template = pair.getKey();
          try (BufferedWriter w = Files.newBufferedWriter(out)) {            
            template.execute(w, data).flush();
          } catch (IOException e) {
            logger_.error("IO error while processing template : template={} out={}", template, out, e);
            throw new RuntimeException(e);
          }
        });
  }

  private void generateArchetype_(String namespace, String project, Path outRoot, int depth) throws IOException {
    logger_.info("## Generating archetype ##  : namespace={} project={} outRoot={}", namespace, project, outRoot);
    if (depth > 10) {
      throw new IOException("Too much recursion in generation of archetypes");
    }
    // Populate namespace and project related data.
    Map<String, String> data = populateData(namespace, project);
    // Get the archetype.json.
    Path archetypeDescriptor = jarUtil_.getAssetPath(Config.ARCHETYPE_DESCRIPTOR_FILENAME);
    // Parse the archetype.json file.
    List<FileTemplate> files;
    try (BufferedReader reader = Files.newBufferedReader(archetypeDescriptor)) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonMap = mapper.readValue(reader, JsonNode.class);
      JsonNode fileNode = jsonMap.get("files");
      files = mapper.convertValue(fileNode, new TypeReference<List<FileTemplate>>() {
      });
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
              Path resourcePath = fl.type.equals("template") ? jarUtil_.getAssetPath(fl.template)
                  : this.resGen_.get(fl.template); // TODO: slight inefficiency in the fact that resource generator is
                                                   // looked up twice.
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
*/

  @Override
  public String getName() {
    return this.fqan_;
  }

}
