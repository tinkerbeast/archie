package tinkerbeast;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tinkerbeast.generators.ArchetypeGenerator;
import tinkerbeast.generators.Config;

public class App {

  private static Logger logger_ = LoggerFactory.getLogger(ArchetypeGenerator.class);

  static void run(String[] args) throws IOException {
    Map<String, String> options = ArgumentParser.parseArguments(args);
    if (options != null) {
      ArchetypeGenerator generator = new ArchetypeGenerator(
          options.get("provider"), options.get("archetype"), options.get("version"));
      generator.generateArchetype(
          options.get("namespace"), options.get("project"), Paths.get(options.get("output")));
    }    
  }

  static void run_predefined() throws IOException {
    Map<String, String> options = new HashMap<>();
    options.put("archetype", "cpp-cmake-simple");
    options.put("provider", Config.ARCHETYPE_DEFAULT_PROVIDER);
    options.put("namespace", "org.example");
    options.put("project", "myProject");
    options.put("output", System.getProperty("user.dir"));
    options.put("version", Config.ARCHETYPE_DEFAULT_VERSION);

    if (options != null) {
      ArchetypeGenerator generator = new ArchetypeGenerator(
          options.get("provider"), options.get("archetype"), options.get("version"));
      generator.generateArchetype(
          options.get("namespace"), options.get("project"), Paths.get(options.get("output")));
    }
  }

  public static void main(String[] args) throws Exception  {
      logger_.trace("A TRACE Message");
      logger_.debug("A DEBUG Message");
      logger_.info("An INFO Message");
      logger_.warn("A WARN Message");
      logger_.error("An ERROR Message");

      //run_predefined();
      run(args);
  }
}
