package tinkerbeast;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

public class ResourceUtil {

    Path basePath_;

    ResourceUtil(String base) throws IOException {
        try {
            URI baseUri = ResourceUtil.class.getClassLoader().getResource(base).toURI();
            if (baseUri.getScheme().equals("jar")) {
                FileSystem fileSystem = FileSystems.newFileSystem(baseUri, Collections.<String, Object>emptyMap());
                basePath_ = fileSystem.getPath(base);
            } else {
                basePath_ = Paths.get(baseUri);
            }
        } catch (URISyntaxException e) { // TODO: bad design choice
            throw new IOException(e.getMessage());
        }
    }

    private static void walk_(String root, URI uri, int depth) throws IOException {
        Path resourcePath;
        if (uri.getScheme().equals("jar")) {
            System.out.format("DEBUG ResourceWalker working with jar%n");
            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
            resourcePath = fileSystem.getPath(root);
        } else {
            System.out.format("DEBUG ResourceWalker working with files%n");
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

    public InputStream getResource(String... locator) throws IOException {
        Path fullPath = Paths.get(basePath_.toString(), locator);
        if (!Files.exists(fullPath)) {
            throw new IOException("Resource not found : " + fullPath);
        }
        return Files.newInputStream(fullPath, StandardOpenOption.READ);
    }
}