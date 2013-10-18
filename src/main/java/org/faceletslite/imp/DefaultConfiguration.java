package org.faceletslite.imp;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.el.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import org.faceletslite.*;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DefaultConfiguration implements Configuration
{ 
	private static final Logger log = Logger.getLogger(FaceletsCompiler.class.getName());
	
	static String DEFAULT_EXPRESSION_FACTORY_CLASS = "de.odysseus.el.ExpressionFactoryImpl";

	private final List<Namespace> namespaces = new ArrayList<Namespace>();
	private final Map<String,List<Class<?>>> classesByPrefix = new HashMap<String, List<Class<?>>>();
	
	public DocumentBuilderFactory getDocumentBuilderFactory() 
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setExpandEntityReferences(false);
		log.info("using "+factory.getClass().getName());
		return factory;
	}

	@Override
	public DocumentBuilder createDocumentBuilder() 
	{
		try {
			DocumentBuilder builder = getDocumentBuilderFactory().newDocumentBuilder();
		 	builder.setEntityResolver(
				new EntityResolver() {
		            public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException
		            {
		            	return new InputSource(new StringReader(""));
		            }
				}	
			);
		 	return builder;
		}
		catch (ParserConfigurationException exc) {
			throw new RuntimeException("XML setup failure", exc);
		}
	}

	public TransformerFactory getTransformerFactory() 
	{
		TransformerFactory result = TransformerFactory.newInstance();
		log.info("using "+result.getClass().getName());
		return result;
	}

	@Override
	public Transformer createDocumentTransformer() 
	{
		try {
			return getTransformerFactory().newTransformer();
		} 
		catch (TransformerConfigurationException exc) {
			throw new RuntimeException("XML setup failure", exc);
		}
	}
	
	public ExpressionFactory getExpressionFactory() 
	{
		try
		{
			java.util.Properties properties = new java.util.Properties();
			properties.put("javax.el.cacheSize", "10000"); // ten times the default 
			Class<?>[] constructorParams = new Class<?>[1];
			constructorParams[0] = Properties.class;
			Constructor<?> constructor = Class.forName(DEFAULT_EXPRESSION_FACTORY_CLASS).getConstructor(constructorParams);
			return (ExpressionFactory) constructor.newInstance(properties);
		}
		catch (ClassNotFoundException exc) {
			// fall through
		}
		catch (Exception exc) {
			throw new RuntimeException("cannot instantiate JUEL", exc);
		}
		return ExpressionFactory.newInstance();
	}
	
	@Override
	public ELResolver getELResolver() 
	{
		CompositeELResolver result = new CompositeELResolver();
		result.add(new MapELResolver());
		result.add(new ArrayELResolver());
		result.add(new ListELResolver());
		result.add(new BeanELResolver());
		return result;
	}
	
	public ResourceReader getResourceReader() 
	{
		return new FileResourceReader("", ".html");
	}
	
	@Override
	public List<Namespace> getCustomNamespaces() {
		return namespaces;
	}
	
	public void addCustomNamespace(String uri, ResourceReader resourceReader)
	{
		addCustomNamespace(uri, resourceReader, Collections.<String, CustomTag>emptyMap());
	}
	
	public void addCustomNamespace(
		final String uri,
		final ResourceReader resourceReader,
		final Map<String, CustomTag> customTags
	)
	{
		namespaces.add(
			new Namespace() {
				@Override public String getUri() { return uri; }
				@Override public ResourceReader getResourceReader() { return resourceReader; }
				@Override public CustomTag getCustomTag(String tagName) { return customTags.get(tagName); }
			}
		);
	}
	
	@Override
	public FunctionMapper getFunctionMapper() {
		return new FunctionMapperImp(classesByPrefix);
	}
	
	public void addFunctions(String prefix, Class<?> clazz) 
	{
		List<Class<?>> classes = classesByPrefix.get(prefix);
		if (classes==null) {
			classes = new ArrayList<Class<?>>();
			classesByPrefix.put(prefix, classes);
		}
		classes.add(clazz);
	}
	
	@Override
	public Map<String, Facelet> getCache() {
		return new ConcurrentHashMap<String, Facelet>();
	}
}
