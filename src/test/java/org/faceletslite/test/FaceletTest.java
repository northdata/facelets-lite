package org.faceletslite.test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.el.CompositeELResolver;
import javax.el.ELResolver;

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
import org.junit.Assert;
import org.junit.Test;



public class FaceletTest
{
	org.faceletslite.FaceletsCompiler compiler;
	String resourceDir = "src/test/resources/";
	private final Cleaner cleaner = new Cleaner(Whitelist.relaxed());

	public FaceletTest()
	{
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
	public void testSet()
	{
		checkAgainstExpectedOutput("set1");
	}

	/**
	 * NOTE: checkAgainstExpectedOutput() unescapes HTML escapes in attribute values,
	 * so we cannot use it for this test.
	 */
	@Test
	public void testXss() throws IOException
	{
		String output = compile("xss.html", null);
		assertThat(output, containsString("&lt;xss/&gt;"));
		assertThat(output, containsString("&lt;script/&gt;"));
	}

	@Test
	public void testCData() {
		try {
			String output = compile("cdata.html", null);
			Assert.assertTrue(
				"script section should not be escaped",
				output.contains("console.info( i >= 0 && i < 1 );"));
		} catch (IOException exc) {
			Assert.fail(exc.getMessage());
		}
	}

	@Test
	public void testIf()
	{
		checkAgainstExpectedOutput("if1");
		checkAgainstExpectedOutput("if2");
	}

	@Test
	public void testForEach()
	{
		checkAgainstExpectedOutput("foreach1");
	}

	@Test
	public void testNoEscape()
	{
		checkAgainstExpectedOutput("noescape");
	}

	@Test
	public void testComposition()
	{
		checkAgainstExpectedOutput("composition");
	}

	@Test
	public void testDocType() throws IOException
	{
		String docType = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">";
		String input = docType+"<html></html>";
		String output = compiler.compile(new ByteArrayInputStream(input.getBytes())).render(null);
		assertThat(output, containsString(docType));
	}

	@Test
	public void testFunctionExpression() {
		Map<String, Object> context = new HashMap<String, Object>();
		context.put("person", new Person("Donald", "Duck"));
		checkAgainstExpectedOutput("function", context);
	}

	@Test
	public void testWhenWithBoolean() {
		Map<String, Object> context = new HashMap<String, Object>();
		context.put("foo", true);
		checkAgainstExpectedOutput("when1", context);
	}

	@Test
	public void testWhenWithUndefinedBoolean() {
		Map<String, Object> context = new HashMap<String, Object>();
		checkAgainstExpectedOutput("when2", context);
	}



	void checkAgainstExpectedOutput(String name) {
		checkAgainstExpectedOutput(name, null);
	}

	void checkAgainstExpectedOutput(String name, Object context)
	{
		try
		{
			String dir = "compare/";

			String inputFileName = dir+name+".input.html";
			String expectedOutputFileName = dir+name+".expected.html";
			String jsonFileName = dir+name+".json";

			File jsonFile = new File(resourceDir+jsonFileName);
			File expectedOutputFile = new File(resourceDir+expectedOutputFileName);

			Object ctx = context;
			if (ctx == null) {
				ctx = jsonFile.exists() ? parseJson(new FileInputStream(jsonFile)) : null;
			}
			String output = compile(inputFileName, ctx);

			Document outputDocument = Jsoup.parse(output);
			Document expectedOutputDocument = Jsoup.parse(expectedOutputFile, "utf-8");

			String cleanedOutput = toNormalHtml(outputDocument);
			String cleanedExpectedOutput = toNormalHtml(expectedOutputDocument);

			Assert.assertEquals(
				"test "+name,
				cleanedExpectedOutput,
				cleanedOutput
				);
		}
		catch (IOException exc)
		{
			Assert.fail(exc.getMessage());
		}
	}

	String toNormalHtml(Document doc)
	{
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
				public void head(Node arg0, int arg1) {
				}
			}
			);
		return cleaner.clean(doc).html();
	}

	String compile(String inputFile, Object context) throws IOException
	{
		return compiler.compile(inputFile).render(context);
	}

	JSONObject parseJson(InputStream in)
	{
		try
		{
			byte[] data = readBytes(in);
			return new JSONObject(new String(data, "utf-8"));
		}
		catch (Exception exc)
		{
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
