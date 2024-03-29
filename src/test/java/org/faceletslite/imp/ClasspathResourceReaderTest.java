package org.faceletslite.imp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClasspathResourceReaderTest {

    @Test
    public void testFromFilesWithReload() throws IOException {

        ClasspathResourceReader classpathResourceReader = new ClasspathResourceReader("/compare/", ".html", true);

        try (InputStream readWithSlash = classpathResourceReader.read("/composition.expected.html")) {
            Assertions.assertThat(readWithSlash)
                .isNotNull();
        }

        try (InputStream readWithoutSlash = classpathResourceReader.read("composition.expected.html")) {
            Assertions.assertThat(readWithoutSlash)
                .isNotNull();
        }

    }

    @Test
    public void testWithJar() throws IOException {
        ClasspathResourceReader classpathResourceReader = new ClasspathResourceReader("/compare/", ".html", true);

        Path jarFile = Files.createTempFile("tmp", ".jar");

        try (JarOutputStream jarOutputStream = new JarOutputStream(
            Files.newOutputStream(jarFile))) {

            ZipEntry zipEntry = new ZipEntry("compare/composition.expected1.html");
            jarOutputStream.putNextEntry(zipEntry);

            Files.copy(Paths.get("src/test/resources/compare/composition.expected.html"), jarOutputStream);
        }

        URLClassLoader urlClassLoader = new URLClassLoader(new URL[] {jarFile.toUri().toURL()});

        ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(urlClassLoader);

            try (InputStream readWithSlash = classpathResourceReader.read("/composition.expected1.html")) {
                Assertions.assertThat(readWithSlash)
                    .isNotNull();
            }

            try (InputStream readWithoutSlash = classpathResourceReader.read("composition.expected1.html")) {

                Assertions.assertThat(readWithoutSlash)
                    .isNotNull();
            }

        } finally {
            Thread.currentThread().setContextClassLoader(prevCL);
        }

    }
}