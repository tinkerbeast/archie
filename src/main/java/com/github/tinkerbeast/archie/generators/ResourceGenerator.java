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

import com.github.tinkerbeast.archie.JarUtil;
import com.github.tinkerbeast.archie.ds.Trie;



class ResourceGenerator implements Generator {

  private static Logger logger_ = LoggerFactory.getLogger(ResourceGenerator.class);

  // TODO: use LRU map
  private static Map<String, ResourceGenerator> providers_ = new HashMap<>(); // Avoids regenerating multiple ResourceGenerator-s
  
  
  private JarUtil jarUtil_;
  private Map<String, Path> resourceData_;
  private Trie<String> lookup_;
  private String fqrn_;

  private ResourceGenerator() throws IOException {
    this(Config.ARCHETYPE_DEFAULT_PROVIDER);
  }

  public String getFqrn() {    
    return this.fqrn_;
  }

  private ResourceGenerator(String provider) {
    logger_.info("ResourceGenerator instance being created : provider={}", provider);
    this.fqrn_ = String.format("%s::resource", provider);
    Path resources = Paths.get(Config.providerToRoot.get(provider), Config.RESOURCE_DIRECTORY);    
    logger_.info("ResourceGenerator created : provider={} path={}", provider, resources);
    this.jarUtil_ = new JarUtil(resources.toString());
    this.resourceData_ = new HashMap<>();
    this.lookup_ = new Trie<String>();
    try {
      this.createResourceMap_();
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ResourceGenerator instance(String provider) {
    // Resource lookup request.    
    ResourceGenerator p = providers_.get(provider);
    if (null == p) {
      p = new ResourceGenerator(provider);
      providers_.put(provider, p);
    }
    return p;
  }

  public Path resolveResourcePath(String resource) {
    return resourceData_.get(this.lookup(resource));
  }

  public String lookup(String resource) {
    // Resource lookup response or exception on null.
    List<String> matches = lookup_.get(resource);
    logger_.debug("Resource lookup response : resource={} matches={}", resource, matches);
    if (matches.isEmpty()) {
      return null;
    }
    // Return the most recent version of the resource.
    Collections.sort(matches);
    String latestResource = matches.get(matches.size() - 1);
    return latestResource;
  }

  /**
   * Populates resourceData_ and lookup_ with a map from name to path.
   * Example entry: cmake-protobuf-v1=.../archetypes/_resources/cmake-protobuf-v1.cmake
   * 
   * @throws IOException
   */
  private void createResourceMap_() throws IOException {
    // TODO: suffix validation.
    //  String[] xx = new String[]{"v1", "v10", "v2", "v22", "v222", "v2.2", "v2.2.2", "v-10", "v-20"};
    // sorted as = v-10 v-20 v1 v10 v2 v2.2 v2.2.2 v22 v222 
    // So allowed schemes are v{num} or v{num}.{num} or v{num}.{num}.{num}
    // {num} must be positive
    try (Reader in = Files.newBufferedReader(jarUtil_.getAssetPath(Config.ARCHETYPE_DESCRIPTOR_FILENAME))) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonMap = mapper.readValue(in, JsonNode.class);
      JsonNode fileNode = jsonMap.get("files");
      List<ArchetypeGenerator.ArchetypeEntry> files = mapper.convertValue(fileNode,
          new TypeReference<List<ArchetypeGenerator.ArchetypeEntry>>() {});
      for (ArchetypeGenerator.ArchetypeEntry fl : files) {        
        Path resPath = jarUtil_.getAssetPath(fl.name); 
        resourceData_.put(fl.template, resPath);
        lookup_.put(fl.template);
      }
      logger_.debug("Resource mapping created : mapping={}", resourceData_);
    }
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return null;
  }
  
}