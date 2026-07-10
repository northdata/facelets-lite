package org.faceletslite.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Demonstrates that {@link DocumentBuilder} and {@link Document} (the JAXP/DOM
 * types used
 * by the Facelets compiler) are <em>not</em> thread-safe.
 * <p>
 * This is the reason {@code FaceletsCompilerImp} pools its
 * {@code DocumentBuilder}s and keeps
 * per-thread working copies of the parsed source document.
 * <p>
 * The tests deliberately share a single instance across many threads and assert
 * that this
 * corrupts state or throws. Because the failures are races, we use a large
 * number of threads,
 * iterations and a common start latch to make them reliably observable.
 */
public class DocumentThreadSafetyTest {

    private static final int THREADS = 16;
    private static final int ITERATIONS = 100;

    private static final String XML = """
            <root>
            <a><b>1</b><c>2</c></a>
            <a><b>3</b><c>4</c></a>
            <a><b>5</b><c>6</c></a>
            </root>""";

    private static DocumentBuilderFactory newNamespaceAwareFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    /**
     * A single {@link DocumentBuilder} instance must not be used to parse
     * concurrently.
     * Sharing one across threads corrupts its internal parser state, which surfaces
     * as
     * exceptions and/or documents that do not reflect the parsed input.
     */
    @Test
    void documentBuilderIsNotThreadSafe() throws Exception {
        DocumentBuilder sharedBuilder = newNamespaceAwareFactory().newDocumentBuilder();

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicInteger wrongResults = new AtomicInteger();

        runConcurrently(() -> {
            try {
                Document doc = sharedBuilder.parse(
                        new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
                // Every parse of XML should yield exactly three <a> elements.
                if (doc.getElementsByTagName("a").getLength() != 3) {
                    wrongResults.incrementAndGet();
                }
            } catch (Throwable exc) {
                failures.add(exc);
            }
        });

        assertThat(failures.size() + wrongResults.get())
                .as("sharing a single DocumentBuilder across threads should corrupt parsing "
                        + "(exceptions=%s, wrongResults=%s)", failures.size(), wrongResults.get())
                .isGreaterThan(0);
    }

    /**
     * Using a fresh {@link DocumentBuilder} per thread is safe: the factory is the
     * only shared
     * state and parsing produces correct, independent documents. This is the
     * counterpart to
     * {@link #documentBuilderIsNotThreadSafe()} and to the pooling strategy in the
     * compiler.
     */
    @Test
    void oneDocumentBuilderPerThreadIsSafe() throws Exception {
        DocumentBuilderFactory factory = newNamespaceAwareFactory();

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicInteger wrongResults = new AtomicInteger();

        runConcurrently(() -> {
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(
                        new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
                if (doc.getElementsByTagName("a").getLength() != 3) {
                    wrongResults.incrementAndGet();
                }
            } catch (Throwable exc) {
                failures.add(exc);
            }
        });

        assertThat(failures)
                .as("per-thread DocumentBuilders should never throw")
                .isEmpty();
        assertThat(wrongResults).as("per-thread DocumentBuilders should always parse correctly")
                .hasValue(0);
    }

    /**
     * A single {@link Document} instance must not be mutated concurrently.
     * Appending children to
     * the same node from multiple threads corrupts the DOM tree, which surfaces as
     * exceptions
     * (e.g. from the internal sibling links) and/or lost updates: the final child
     * count is smaller
     * than the number of appends that were actually performed.
     */
    @Test
    void documentMutationIsNotThreadSafe() throws Exception {
        DocumentBuilder builder = newNamespaceAwareFactory().newDocumentBuilder();
        Document sharedDocument = builder.parse(
                new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
        Element root = sharedDocument.getDocumentElement();

        List<Throwable> failures = new CopyOnWriteArrayList<>();

        runConcurrently(() -> {
            try {
                Element child = sharedDocument.createElement("added");
                root.appendChild(child);
            } catch (Throwable exc) {
                failures.add(exc);
            }
        });

        // If appendChild were thread-safe, every successful append would be retained
        // and the tree
        // would hold exactly one "added" child per invocation. Because the appends race
        // on the
        // node's internal sibling links, updates are lost (or throw), so the observed
        // count falls
        // short of the number of successful appends. Any exception or lost node proves
        // the race.
        int expectedAdded = THREADS * ITERATIONS - failures.size();
        int actualAdded = countChildren(root, "added");

        assertThat(failures.size() + (expectedAdded - actualAdded))
                .as("concurrent mutation of a single Document should corrupt the DOM "
                        + "(exceptions=%s, expectedAdded=%s, actualAdded=%s)",
                        failures.size(), expectedAdded, actualAdded)
                .isGreaterThan(0);
    }

    private static int countChildren(Node parent, String name) {
        int count = 0;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Runs {@code task} on {@link #THREADS} threads, each performing
     * {@link #ITERATIONS}
     * iterations. All worker threads start together on a shared latch to maximise
     * contention.
     */
    private static void runConcurrently(Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        try {
            for (int t = 0; t < THREADS; t++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < ITERATIONS; i++) {
                            task.run();
                        }
                    } catch (InterruptedException exc) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
