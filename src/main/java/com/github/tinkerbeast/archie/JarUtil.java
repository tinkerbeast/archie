package com.github.tinkerbeast.archie;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mustachejava.MustacheResolver;


public class JarUtil {

    private static Logger logger_ = LoggerFactory.getLogger(JarUtil.class);

    private Path basePath_;

    public JarUtil(String base) {
        try {
            URI baseUri = JarUtil.class.getClassLoader().getResource(base).toURI();
            if (baseUri.getScheme().equals("jar")) {
                FileSystem fileSystem = FileSystems.newFileSystem(baseUri, Collections.<String, Object>emptyMap());
                basePath_ = fileSystem.getPath(base);
            } else {
                basePath_ = Paths.get(baseUri);
            }
        } catch (URISyntaxException | IOException e) { // TODO: bad design choice
            throw new RuntimeException(e.getMessage());
        }
    }

    /*
    private static void walk_(String root, URI uri, int depth) throws IOException {
        Path resourcePath;
        boolean uriSchemeIsJar = uri.getScheme().equals("jar");
        logger_.debug("ResourceWalker working URI scheme determined: uriSchemeIsJar={}", uriSchemeIsJar);
        if (uriSchemeIsJar) {
            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
            resourcePath = fileSystem.getPath(root);
        } else {            
            resourcePath = Paths.get(uri);
        }
        try (Stream<Path> walk = Files.walk(resourcePath, depth)) {
            for (Iterator<Path> it = walk.iterator(); it.hasNext();) {
                System.out.println(it.next());
            }
        }
    }

    public static void walkRelative(String root, int depth) throws URISyntaxException, IOException {
        URI uri = ResourceUtil.class.getClassLoader().getResource(root).toURI();
        walk_(root, uri, depth);
    }

    public static void walkAbsolute(String root, int depth) throws URISyntaxException, IOException {
        URI uri = ResourceUtil.class.getResource(root).toURI();
        walk_(root, uri, depth);
    }
    */

    public Path getAssetPath(String... locator) {        
        Path fullPath = Paths.get(basePath_.toString(), locator);        
        if (!Files.exists(fullPath)) {
            logger_.warn("Resource not found : path={}", fullPath);
            return null;
        }
        return fullPath;
    }

    
}