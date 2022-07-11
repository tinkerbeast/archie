package com.github.tinkerbeast.archie.generators;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tinkerbeast.archie.ResourceUtil;
import com.github.tinkerbeast.archie.ds.Trie;
import com.github.tinkerbeast.archie.generators.ArchetypeGenerator.FileTemplate;


class ResourceGenerator {

  private static Logger logger_ = LoggerFactory.getLogger(ResourceGenerator.class);

  private static Map<String, ResourceGenerator> providers_ = new HashMap<>(); // Avoids regenerating multiple ResourceGenerator-s
  
  private ResourceUtil ru_;
  private Map<String, Path> resourceData_;
  private Trie<String> lookup_;

  private ResourceGenerator() throws IOException {
    this(Config.ARCHETYPE_DEFAULT_PROVIDER);
  }

  private ResourceGenerator(String provider) throws IOException {    
    Path resources = Paths.get(Config.providerToResource.get(provider), Config.RESOURCE_DIRECTORY);    
    logger_.info("## Creating resource cache ## : provider={} path={}", provider, resources);
    this.ru_ = new ResourceUtil(resources.toString());
    this.resourceData_ = new HashMap<>();
    this.lookup_ = new Trie<String>();
    this.createResourceMap_();
    
  }

  public static ResourceGenerator getGenerator(String provider) throws IOException {
    // Resource lookup request.
    logger_.debug("Instance might be used soon : provider={}", provider);
    ResourceGenerator p = providers_.get(provider);
    if (null == p) {
      p = new ResourceGenerator(provider);
      providers_.put(provider, p);
    }
    return p;
  }

  public Path get(String resource) throws IOException {    
    return resourceData_.get(this.lookup(resource));
  }

  public String lookup(String resource) {
    // Resource lookup request.
    logger_.debug("Resource lookup request : resource={}", resource);
    // Resource lookup response or exception on null.
    List<String> matches = lookup_.get(resource);
    logger_.debug("Resource lookup response : matches={}", matches);
    if (matches.isEmpty()) {
      return null;
    }
    // Return the most recent version of the resource.
    Collections.sort(matches);
    String latestResource = matches.get(matches.size() - 1);
    return latestResource;
  }

  private void createResourceMap_() throws IOException {
    // TODO: suffix validation.
    //  String[] xx = new String[]{"v1", "v10", "v2", "v22", "v222", "v2.2", "v2.2.2", "v-10", "v-20"};
    // sorted as = v-10 v-20 v1 v10 v2 v2.2 v2.2.2 v22 v222 
    // So allowed schemes are v{num} or v{num}.{num} or v{num}.{num}.{num}
    // {num} must be positive
    try (Reader in = Files.newBufferedReader(ru_.getAssetPath(Config.ARCHETYPE_DESCRIPTOR_FILENAME))) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonMap = mapper.readValue(in, JsonNode.class);
      JsonNode fileNode = jsonMap.get("files");
      List<ArchetypeGenerator.FileTemplate> files = mapper.convertValue(fileNode,
          new TypeReference<List<ArchetypeGenerator.FileTemplate>>() {});
      for (FileTemplate fl : files) {        
        Path resPath = ru_.getAssetPath(fl.name); 
        resourceData_.put(fl.template, resPath);
        lookup_.put(fl.template);
      }
      logger_.debug("Resource mapping created : mapping={}", resourceData_);
    }
  }
  
}