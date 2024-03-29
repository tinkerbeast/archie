package com.github.tinkerbeast.archie.generators;

import java.util.HashMap;
import java.util.Map;

public class Config {

    public static final String ARCHETYPE_DEFAULT_VERSION = "latest";
    //public static final String  ARCHETYPE_DEFAULT_PROVIDER = "com.github/tinkerbeast/archie.git";
    public static final String  ARCHETYPE_DEFAULT_PROVIDER = "??";
    public static final String ARCHETYPE_DESCRIPTOR_FILENAME = "archetype.json";
    public static final String RESOURCE_DIRECTORY = "_resources";

    public static Map<String, String> providerToRoot = new HashMap<>();
    static {
        Config.providerToRoot.put(Config.ARCHETYPE_DEFAULT_PROVIDER, "archetypes");
      }
    

}