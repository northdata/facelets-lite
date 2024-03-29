package org.faceletslite.imp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.faceletslite.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads resources from the classpath.
 * <p>
 * For development, hot reloading can be enabled.
 *
 * @author hwellmann
 *
 */
public class ClasspathResourceReader implements ResourceReader {

    private static final Logger log = LoggerFactory.getLogger(ClasspathResourceReader.class);

    private final String rootDir;
    private final String defaultExtension;
    private final boolean reload;

    public ClasspathResourceReader(String rootDir, String defaultExtension) {
        this(rootDir, defaultExtension, false);
    }

    public ClasspathResourceReader(String rootDir, String defaultExtension, boolean reload) {
        this.rootDir = rootDir.replaceAll("/*$", "");

        if (!defaultExtension.startsWith(".")) {
            defaultExtension = "." + defaultExtension;
        }
        this.defaultExtension = defaultExtension;
        this.reload = reload;
    }

    public String getRootDir() {
        return rootDir;
    }

    public String getDefaultExtension() {
        return defaultExtension;
    }

    @Override
    public InputStream read(String uri) throws IOException {
        String extension = getDefaultExtension();
        if (extension != null && !uri.contains(".")) {
            uri += extension;
        }
        String dir = getRootDir();

        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }

        String filename = dir + uri;
        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }
        InputStream is = getInputStream(filename);

        if (is == null) {
            String msg = "cannot find '" + filename + "'";
            log.error(msg);
            throw new FileNotFoundException(msg);
        }
        return is;
    }

    private InputStream getInputStream(String filename) throws IOException {
        if (reload) {
            return getInputStreamWithReload(filename);
        } else {
            return getInputStreamFromClassloader(filename);
        }
    }

    private InputStream getInputStreamWithReload(String filename) throws IOException {
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
        URL resource = ccl.getResource(filename);
        if (resource == null) {
            return null;
        }

        String url = resource.toString();
        if (url.startsWith("file:")) {
            try {
                File file = Paths.get(resource.toURI()).toFile();
                return new FileInputStream(file);
            } catch (URISyntaxException exc) {
                log.error("cannot convert URL to file: {}", url, exc);
                return null;
            }
        }

        if (url.startsWith("jar:file:")) {
            int excl = url.indexOf('!');
            url = url.substring("jar:file:".length(), excl);

            JarFile jarFile = new JarFile(new File(url));
            ZipEntry entry = jarFile.getEntry(filename);
            InputStream is = jarFile.getInputStream(entry);
            return new JarEntryInputStream(jarFile, is);
        }

        return getInputStreamFromClassloader(filename);

    }

    private InputStream getInputStreamFromClassloader(String filename) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
    }

}
