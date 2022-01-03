package org.faceletslite.imp;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.el.ArrayELResolver;
import javax.el.BeanELResolver;
import javax.el.CompositeELResolver;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.ListELResolver;
import javax.el.MapELResolver;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import org.faceletslite.Configuration;
import org.faceletslite.CustomTag;
import org.faceletslite.Facelet;
import org.faceletslite.FaceletsCompiler;
import org.faceletslite.Namespace;
import org.faceletslite.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DefaultConfiguration implements Configuration {

    private static final Logger log = LoggerFactory.getLogger(FaceletsCompiler.class);

    private final List<Namespace> namespaces = new ArrayList<>();
    private final Map<String, List<Class<?>>> classesByPrefix = new HashMap<>();

    public DocumentBuilderFactory getDocumentBuilderFactory() {
        try {
            DocumentBuilderFactory factory = null;
            try {
                factory = (DocumentBuilderFactory) Class.forName("org.apache.xerces.jaxp.DocumentBuilderFactoryImpl")
                    .newInstance();
            } catch (Exception exc) {
            }
            if (factory == null) {
                factory = DocumentBuilderFactory.newInstance();
            }
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setValidating(false);
            factory.setIgnoringElementContentWhitespace(true);
            factory.setXIncludeAware(false);
            //factory.setFeature("http://apache.org/xml/features/validation/unparsed-entity-checking", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            log.info("using {}", factory.getClass().getName());
            return factory;
        } catch (ParserConfigurationException exc) {
            throw new RuntimeException("XML setup failure", exc);
        }
    }

    @Override
    public DocumentBuilder createDocumentBuilder() {
        try {
            DocumentBuilder builder = getDocumentBuilderFactory().newDocumentBuilder();
            builder.setEntityResolver(
                new EntityResolver() {

                    @Override
                    public InputSource resolveEntity(String publicId, String systemId)
                        throws SAXException, IOException {
                        return new InputSource(new StringReader(""));
                    }
                });
            return builder;
        } catch (ParserConfigurationException exc) {
            throw new RuntimeException("XML setup failure", exc);
        }
    }

    public TransformerFactory getTransformerFactory() {
        TransformerFactory result = TransformerFactory.newInstance();
        result.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        result.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        try {
            result.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException exc) {
            throw new RuntimeException("XML setup failure", exc);
        }
        log.info("using " + result.getClass().getName());
        return result;
    }

    @Override
    public Transformer createDocumentTransformer() {
        try {
            Transformer result = getTransformerFactory().newTransformer();
            result.setOutputProperty(OutputKeys.INDENT, "yes");
            result.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            return result;
        } catch (TransformerConfigurationException exc) {
            throw new RuntimeException("XML setup failure", exc);
        }
    }

    @Override
    public ExpressionFactory getExpressionFactory() {
        return ExpressionFactory.newInstance();
    }

    @Override
    public ELResolver getELResolver() {
        CompositeELResolver result = new CompositeELResolver();
        result.add(new MapELResolver());
        result.add(new ArrayELResolver());
        result.add(new ListELResolver());
        result.add(new BeanELResolver());
        return result;
    }

    @Override
    public ResourceReader getResourceReader() {
        return new FileResourceReader("", ".html");
    }

    @Override
    public List<Namespace> getCustomNamespaces() {
        return namespaces;
    }

    public void addCustomNamespace(String uri, ResourceReader resourceReader) {
        addCustomNamespace(uri, resourceReader, Collections.<String, CustomTag>emptyMap());
    }

    public void addCustomNamespace(
        final String uri,
        final ResourceReader resourceReader,
        final Map<String, CustomTag> customTags) {
        namespaces.add(
            new Namespace() {

                @Override
                public String getUri() {
                    return uri;
                }

                @Override
                public ResourceReader getResourceReader() {
                    return resourceReader;
                }

                @Override
                public CustomTag getCustomTag(String tagName) {
                    return customTags.get(tagName);
                }
            });
    }

    @Override
    public FunctionMapper getFunctionMapper() {
        return new FunctionMapperImp(classesByPrefix);
    }

    public void addFunctions(String prefix, Class<?> clazz) {
        List<Class<?>> classes = classesByPrefix.get(prefix);
        if (classes == null) {
            classes = new ArrayList<>();
            classesByPrefix.put(prefix, classes);
        }
        classes.add(clazz);
    }

    @Override
    public Map<String, Facelet> getCache() {
        return new ConcurrentHashMap<>();
    }
}
