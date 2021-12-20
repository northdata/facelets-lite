package org.faceletslite.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.el.CompositeELResolver;
import javax.el.ELResolver;

import org.assertj.core.api.Assertions;
import org.faceletslite.Configuration;
import org.faceletslite.ResourceReader;
import org.faceletslite.imp.DefaultConfiguration;
import org.faceletslite.imp.FaceletsCompilerImp;
import org.faceletslite.imp.FileResourceReader;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.NodeVisitor;
import org.junit.jupiter.api.Test;

public class FaceletTest {

    org.faceletslite.FaceletsCompiler compiler;
    String resourceDir = "src/test/resources/";
    private final Cleaner cleaner = new Cleaner(Whitelist.relaxed());

    public FaceletTest() {
        Configuration configuration = new DefaultConfiguration() {

            @Override
            public ResourceReader getResourceReader() {
                return new FileResourceReader(resourceDir, ".html");
            }

            @Override
            public ELResolver getELResolver() {
                CompositeELResolver result = new CompositeELResolver();
                result.add(new JsonElResolver());
                result.add(super.getELResolver());
                return result;
            }
        };

        compiler = new FaceletsCompilerImp(configuration);
    }

    @Test
    void testSet() {
        checkAgainstExpectedOutput("set1");
    }

    /**
     * NOTE: checkAgainstExpectedOutput() unescapes HTML escapes in attribute values,
     * so we cannot use it for this test.
     */
    @Test
    void testXss() throws IOException {
        String output = compile("xss.html", null);
        Assertions.assertThat(output)
            .contains("&lt;xss/&gt;")
            .contains("&lt;script/&gt;");
    }

    @Test
    public void testCData() {
        try {
            String output = compile("cdata.html", null);
            Assertions.assertThat(output)
                .withFailMessage("script section should not be escaped")
                .contains("console.info( i >= 0 && i < 1 );");
        } catch (IOException exc) {
            Assertions.fail(exc.getMessage());
        }
    }

    @Test
    public void testIf() {
        checkAgainstExpectedOutput("if1");
        checkAgainstExpectedOutput("if2");
    }

    @Test
    public void testForEach() {
        checkAgainstExpectedOutput("foreach1");
    }

    @Test
    public void testNoEscape() {
        checkAgainstExpectedOutput("noescape");
    }

    @Test
    public void testComposition() {
        checkAgainstExpectedOutput("composition");
    }

    @Test
    public void testDocType() throws IOException {
        String docType = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">";
        String input = docType + "<html></html>";
        String output = compiler.compile(new ByteArrayInputStream(input.getBytes())).render(null);
        Assertions.assertThat(output)
            .contains(docType);
    }

    @Test
    void testFunctionExpression() {
        Map<String, Object> context = new HashMap<>();
        context.put("person", new Person("Donald", "Duck"));
        checkAgainstExpectedOutput("function", context);
    }

    @Test
    void testWhenWithBoolean() {
        Map<String, Object> context = new HashMap<>();
        context.put("foo", true);
        checkAgainstExpectedOutput("when1", context);
    }

    @Test
    public void testWhenWithUndefinedBoolean() {
        Map<String, Object> context = new HashMap<>();
        checkAgainstExpectedOutput("when2", context);
    }

    void checkAgainstExpectedOutput(String name) {
        checkAgainstExpectedOutput(name, null);
    }

    void checkAgainstExpectedOutput(String name, Object context) {
        try {
            String dir = "compare/";

            String inputFileName = dir + name + ".input.html";
            String expectedOutputFileName = dir + name + ".expected.html";
            String jsonFileName = dir + name + ".json";

            File jsonFile = new File(resourceDir + jsonFileName);
            File expectedOutputFile = new File(resourceDir + expectedOutputFileName);

            Object ctx = context;
            if (ctx == null) {
                ctx = jsonFile.exists() ? parseJson(new FileInputStream(jsonFile)) : null;
            }
            String output = compile(inputFileName, ctx);

            Document outputDocument = Jsoup.parse(output);
            Document expectedOutputDocument = Jsoup.parse(expectedOutputFile, "utf-8");

            String cleanedOutput = toNormalHtml(outputDocument);
            String cleanedExpectedOutput = toNormalHtml(expectedOutputDocument);

            Assertions.assertThat(cleanedOutput)
                .withFailMessage("test " + name)
                .isEqualTo(cleanedExpectedOutput);
        } catch (IOException exc) {
            Assertions.fail(exc.getMessage());
        }
    }

    String toNormalHtml(Document doc) {
        doc.normalise();
        doc.traverse(
            new NodeVisitor() {

                @Override
                public void tail(Node node, int depth) {
                    if (node instanceof TextNode) {
                        TextNode textNode = (TextNode) node;
                        textNode.text(textNode.text().trim());
                    }
                }

                @Override
                public void head(Node arg0, int arg1) {}
            });
        return cleaner.clean(doc).html();
    }

    String compile(String inputFile, Object context) throws IOException {
        return compiler.compile(inputFile).render(context);
    }

    JSONObject parseJson(InputStream in) {
        try {
            byte[] data = readBytes(in);
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        } catch (Exception exc) {
            throw new RuntimeException("cannot parse json", exc);
        }
    }

    static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (;;) {
                int available = in.available();
                if (available <= 0) {
                    break;
                }
                byte[] bytes = new byte[available];
                in.read(bytes);
                out.write(bytes);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
