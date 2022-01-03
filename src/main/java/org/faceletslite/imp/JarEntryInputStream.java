package org.faceletslite.imp;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;

/**
 * Wraps an input stream for a JAR file entry, closing
 * the entire JAR file when the entry stream is closed.
 * 
 * @author hwellmann
 *
 */
public final class JarEntryInputStream extends InputStream {

    private final JarFile jarFile;
    private final InputStream stream;

    public JarEntryInputStream(JarFile jarFile, InputStream stream) {
        this.jarFile = jarFile;
        this.stream = stream;
    }

    public void close() throws IOException {
        stream.close();
        jarFile.close();
    }

    public void mark(int readlimit) {
        stream.mark(readlimit);
    }

    public boolean markSupported() {
        return stream.markSupported();
    }

    public int read() throws IOException {
        return stream.read();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return stream.read(b, off, len);
    }

    public void reset() throws IOException {
        stream.reset();
    }

    public long skip(long n) throws IOException {
        return stream.skip(n);
    }
}
