package org.faceletslite.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.faceletslite.Facelet;
import org.faceletslite.FaceletsCompiler;
import org.faceletslite.imp.DefaultConfiguration;
import org.faceletslite.imp.FaceletsCompilerImp;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates that Facelet compilation and rendering <em>are</em> thread-safe, even though the
 * underlying JAXP {@code DocumentBuilder} and DOM {@code Document} are not (see
 * {@link DocumentThreadSafetyTest}).
 * <p>
 * Thread-safety is achieved inside the compiler by pooling {@code DocumentBuilder}s and by keeping
 * per-thread working copies of the parsed source document.
 * <p> 
 * These tests exercise exactly those paths under heavy contention and assert that 
 * every thread observes correct, uncorrupted output.
 */
public class FaceletThreadSafetyTest {

    private static final int THREADS = 8;
    private static final int ITERATIONS = 50;

    /** Number of sibling {@code <section>} blocks in the template (width). */
    private static final int SECTIONS = 80;
    /** Number of items each {@code c:forEach} iterates over. */
    private static final int ITEMS = 40;

    /**
     * A large, wide and deeply-nested template. Each render forces the processor to walk the shared
     * source document by index ({@code getChildNodes().item(i)}) across many siblings and nesting
     * levels. Xerces caches the last-accessed child index <em>on the parent node</em>, so when many
     * threads traverse the same (unpooled) working copy concurrently that cache thrashes and
     * {@code item(i)} returns the wrong node — corrupting the rendered output. Pooling per-thread
     * working copies is exactly what prevents this, so with pooling disabled this template makes the
     * race reliably observable.
     */
    private static final String TEMPLATE = buildTemplate();

    private static String buildTemplate() {
        StringBuilder xml = new StringBuilder();
        xml.append("<html xmlns:c=\"http://java.sun.com/jsp/jstl/core\"><body>");
        for (int s = 0; s < SECTIONS; s++) {
            xml.append("<section id='s").append(s).append("'>")
               .append("<header><h2>#{prefix}").append(s).append("</h2></header>")
               .append("<div class='outer'><div class='inner'><ul>")
               .append("<c:forEach var='i' items='#{items}'>")
               .append("<li><span class='a'>#{prefix}</span>")
               .append("<span class='b'>#{i}</span>")
               .append("<em class='c'>").append(s).append("</em></li>")
               .append("</c:forEach>")
               .append("</ul></div></div>")
               .append("</section>");
        }
        xml.append("</body></html>");
        return xml.toString();
    }

    private final FaceletsCompiler compiler = new FaceletsCompilerImp(new DefaultConfiguration());

    private static Map<String, Object> context(String prefix) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("prefix", prefix);
        List<Integer> items = new java.util.ArrayList<>(ITEMS);
        for (int i = 0; i < ITEMS; i++) {
            items.add(i);
        }
        ctx.put("items", items);
        return ctx;
    }

    private Facelet compileTemplate() throws IOException {
        return compiler.compile(
            new ByteArrayInputStream(TEMPLATE.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Compiling the same source concurrently from many threads must always produce a Facelet that
     * renders to the exact same, correct output as a single-threaded compilation.
     */
    @Test
    void compilationIsThreadSafe() throws Exception {
        Map<String, Object> ctx = context("x-");
        String expected = compileTemplate().render(ctx);

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<String> mismatches = new CopyOnWriteArrayList<>();

        runConcurrently(() -> {
            try {
                String actual = compileTemplate().render(ctx);
                if (!expected.equals(actual)) {
                    mismatches.add(actual);
                }
            } catch (Throwable exc) {
                failures.add(exc);
            }
        });

        assertThat(failures).as("concurrent compilation must not throw").isEmpty();
        assertThat(mismatches).as("concurrent compilation must produce identical output").isEmpty();
    }

    /**
     * Rendering a single, shared {@link Facelet} concurrently with per-thread contexts must give
     * every thread the output for its <em>own</em> context, with no cross-contamination. This is
     * the critical guarantee: the pooled per-thread document working copies keep renders isolated.
     */
    @Test
    void renderingIsThreadSafe() throws Exception {
        Facelet facelet = compileTemplate();

        // Establish the expected output for each thread's context, single-threaded.
        Map<Integer, String> expectedByThread = new HashMap<>();
        for (int t = 0; t < THREADS; t++) {
            expectedByThread.put(t, facelet.render(context("t" + t + "-")));
        }

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        Map<Integer, String> mismatches = new ConcurrentHashMap<>();

        runConcurrentlyIndexed(threadIndex -> {
            String expected = expectedByThread.get(threadIndex);
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    String actual = facelet.render(context("t" + threadIndex + "-"));
                    if (!expected.equals(actual)) {
                        mismatches.put(threadIndex, actual);
                        break;
                    }
                }
            } catch (Throwable exc) {
                failures.add(exc);
            }
        });

        assertThat(failures)
            .as("concurrent rendering must not throw")
            .isEmpty();
        assertThat(mismatches)
            .as("each thread must always observe the output for its own context")
            .isEmpty();
    }

    /**
     * Runs {@code task} on {@link #THREADS} threads, each performing {@link #ITERATIONS}
     * iterations, all released together to maximise contention.
     */
    private static void runConcurrently(Runnable task) throws InterruptedException {
        runConcurrentlyIndexed(threadIndex -> {
            for (int i = 0; i < ITERATIONS; i++) {
                task.run();
            }
        });
    }

    /**
     * Like {@link #runConcurrently(Runnable)}, but hands each worker its thread index so it can
     * use a distinct context. The worker is responsible for its own iteration loop.
     */
    private static void runConcurrentlyIndexed(java.util.function.IntConsumer worker)
        throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        try {
            for (int t = 0; t < THREADS; t++) {
                final int threadIndex = t;
                executor.execute(() -> {
                    try {
                        start.await();
                        worker.accept(threadIndex);
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
