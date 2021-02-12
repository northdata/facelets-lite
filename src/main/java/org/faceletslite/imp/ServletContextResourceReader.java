package org.faceletslite.imp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletContext;

import org.faceletslite.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads web resources from a servlet context.
 * <p>
 * TODO: Move to facelets-lite.
 * 
 * @author hwellmann
 *
 */
public class ServletContextResourceReader implements ResourceReader {

    private static Logger log = LoggerFactory.getLogger(ServletContextResourceReader.class);

    private final ServletContext context;
    private final String path;
    private final String defaultExtension;

    public ServletContextResourceReader(ServletContext context, String path, String defaultExtension) {
        this.context = context;
        this.path = path;
        this.defaultExtension = defaultExtension;
    }

    @Override
    public InputStream read(String resourceName) throws IOException {
        String uri = resourceName;
        if (defaultExtension != null && !resourceName.contains(".")) {
            uri += defaultExtension;
        }
        String dir = "/" + path;
        if (!dir.endsWith("/") && !uri.startsWith("/")) {
            uri = "/" + uri;
        }
        String fullPath = dir + uri;
        InputStream is = context.getResourceAsStream(fullPath);
        if (is == null) {
            log.error("resource not found: {}", fullPath);
            throw new FileNotFoundException(fullPath);
        }
        return is;
    }
}
