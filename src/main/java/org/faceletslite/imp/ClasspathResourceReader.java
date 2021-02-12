package org.faceletslite.imp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.faceletslite.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads resources from the classpath.
 * <p>
 * TODO: move to facelets-lite.
 *
 * @author hwellmann
 *
 */
public class ClasspathResourceReader implements ResourceReader {

    private static final Logger log = LoggerFactory.getLogger(ClasspathResourceReader.class);

    private final String rootDir;
    private final String defaultExtension;

    public ClasspathResourceReader(String rootDir, String defaultExtension) {
        this.rootDir = rootDir;
        if (!defaultExtension.startsWith(".")) {
            defaultExtension = "." + defaultExtension;
        }
        this.defaultExtension = defaultExtension;
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
        if (!dir.endsWith("/") && !uri.startsWith("/")) {
            uri = "/" + uri;
        }
        String filename = dir + uri;
        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
        if (is == null) {
            String msg = "cannot find '" + filename + "'";
            log.error(msg);
            throw new FileNotFoundException(msg);
        }
        return is;
    }
}
