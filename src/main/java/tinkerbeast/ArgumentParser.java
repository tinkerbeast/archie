package tinkerbeast;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.*;

class ArgumentParser {

  private static final String ARCHETYPE_DEFAULT_VERSION = "latest";

  private static Options helpOptions = buildHelpOptions_();
  private static Options allOptions = buildAllOptions_(true);

  private static Options buildAllOptions_(boolean required) {
    Options options = new Options();
    options.addOption(Option.builder("n")
        .desc("Namespace of the project (required)")
        .hasArg()
        .argName("NAMESPACE")
        .required(required)
        .longOpt("namespace")
        .build());
    options.addOption(Option.builder("p")
        .desc("Name of the project (required)")
        .hasArg()
        .argName("PROJECT")
        .required(required)
        .longOpt("project")
        .build());
    options.addOption(Option.builder("a")
        .desc("Archetype to generate from (required)")
        .hasArg()
        .argName("ARCHETYPE")
        .required(required)
        .longOpt("archetype")
        .build());
    options.addOption(Option.builder("x")
        .desc(String.format("Archetype ver (default %s)", ArgumentParser.ARCHETYPE_DEFAULT_VERSION))
        .hasArg()
        .argName("ARCHETYPE-VERSION")
        .longOpt("archetype-version")
        .build());
    
    options.addOption(Option.builder("l")
        .desc(String.format("Archetype ver (default %s)", ArgumentParser.ARCHETYPE_DEFAULT_VERSION))
        .hasArg()
        .argName("ARCHETYPE-VERSION")
        .longOpt("archetype-version")
        .build());
    return options;
  }
  
  private static Options buildHelpOptions_() {
    Options options = new Options();
    options.addOption(Option.builder("h")
        .desc("Print help")
        .longOpt("help")
        .build());
      
    for (Option o : buildAllOptions_(false).getOptions()) {
      options.addOption(o);      
    }

    return options;
  }

  String[] args;

  public ArgumentParser(String[] args) {
    this.args = args;
  }

  private void printHelp(String header) {
    final String config_cmd = "archie";
    final String config_url = "https://github.com/tinkerbeast/archie";

    if (header == null) {
      header = String.format("Archetype generation system.%n" +
          "Example: %s -n org.example -p ProjectName -a archie-cpp-cmake-simple%n" +
          "%n", config_cmd);
    }
    final String footer = String.format("%nPlease report issues at %s%n", config_url);

    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(-1, config_cmd, header, allOptions, footer, true);
  }

  private boolean checkForHelp() throws ParseException {
    boolean hasHelp = false;

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(helpOptions, args);
    if (cmd.hasOption('h')) {
      hasHelp = true;
    }

    return hasHelp;
  }

  private Map<String, String> parseArguments_() throws ParseException {

    // Check if help is requested.
    boolean printHelpAndExit = checkForHelp();
    if (printHelpAndExit) {
      printHelp(null);
      return null;
    }
    
    // Pare the complete set of arguements for all options.
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(allOptions, args);

    // Convert options to parameters
    Map<String, String> data = new HashMap<>();
    // TODO: there must be a better mechanism of geeting default version
    String version = cmd.getOptionValue('x', ArgumentParser.ARCHETYPE_DEFAULT_VERSION);
    String archetypeWithVersion = cmd.getOptionValue('a') + "-" + version;
    data.put("archetype", archetypeWithVersion);
    data.put("namespace", cmd.getOptionValue("namespace"));
    data.put("project", cmd.getOptionValue("project"));
    // TODO: getting output directory default value needs to be thought upon
    data.put("output", System.getProperty("user.dir")); 
    data.put("version", version);
    return data;    
  }

  public static Map<String, String> parseArguments(String[] args) {
    ArgumentParser argParser = new ArgumentParser(args);
    try {
      return argParser.parseArguments_();
    } catch (ParseException e) {
      // e.printStackTrace();// TODO: debug logs
      argParser.printHelp(String.format("%s%n", e.getMessage()));
      return null;
    }
  }

}