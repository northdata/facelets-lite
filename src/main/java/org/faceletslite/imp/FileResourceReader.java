package org.faceletslite.imp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.faceletslite.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileResourceReader implements ResourceReader {

    private static final Logger log = LoggerFactory.getLogger(FileResourceReader.class);

    private final String rootDir;
    private final String defaultExtension;

    public FileResourceReader(String rootDir, String defaultExtension) {
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

    /*
    private String removeDots(String filename) {
    	filename = removeDots(filename, '/');
    	filename = removeDots(filename, '\\');
    	return filename;
    }
    
    private String removeDots(String filename, char pathSeparator)
    {
    	
    }
    */
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
        try {
            return new FileInputStream(filename);
        } catch (FileNotFoundException exc) {
            try {
                log.error("cannot find '" + filename + "', absolute path is " + new File(".").getAbsolutePath());
            } catch (Exception exc_) {
                // ignore
            }
            throw exc;
        }
    }
}
