package org.faceletslite.imp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.DocumentFactory;
import org.dom4j.io.HTMLWriter;
import org.dom4j.io.OutputFormat;
import org.faceletslite.Configuration;
import org.faceletslite.CustomTag;
import org.faceletslite.Facelet;
import org.faceletslite.FaceletsCompiler;
import org.faceletslite.Namespace;
import org.faceletslite.ResourceReader;
import org.jdom2.Attribute;
import org.jdom2.CDATA;
import org.jdom2.Comment;
import org.jdom2.Content;
import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.ProcessingInstruction;
import org.jdom2.Text;

import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.FunctionMapper;

public class FaceletsCompilerImp implements FaceletsCompiler, CustomTag.Renderer {

    static final String DUMMY_ROOT = "dummy-root";

    private final Map<String, ResourceReader> resourceReaderByNsUri = new HashMap<String, ResourceReader>();
    final Map<String, Namespace> namespaceByUri = new HashMap<String, Namespace>();
    final ExpressionFactory expressionFactory;
    final FunctionMapper functionMapper;
    final ELResolver resolver;
    private final Map<String, Facelet> templateCache;

    public FaceletsCompilerImp() {
        this(new DefaultConfiguration());
    }

    public FaceletsCompilerImp(final Configuration configuration) {
        this.expressionFactory = configuration.getExpressionFactory();
        this.templateCache = configuration.getCache();
        this.resolver = configuration.getELResolver();
        this.functionMapper = configuration.getFunctionMapper();
        ResourceReader standardResourceReader = configuration.getResourceReader();
        if (standardResourceReader != null) {
            resourceReaderByNsUri.put(Namespaces.None, standardResourceReader);
        }
        for (Namespace namespace: configuration.getCustomNamespaces()) {
            String nsUri = namespace.getUri();
            namespaceByUri.put(nsUri, namespace);
            ResourceReader resourceReader = namespace.getResourceReader();
            if (resourceReader != null) {
                resourceReaderByNsUri.put(nsUri, resourceReader);
            }
        }
    }

    @Override
    public FaceletImp compile(InputStream in) throws IOException {
        return new FaceletImp(this, read(in), "", Namespaces.None);
    }

    @Override
    public FaceletImp compile(String resourceName) throws IOException {
        return compile(resourceName, null);
    }

    @Override
    public FaceletImp compile(String resourceName, String nsUri) throws IOException {
        String key = resourceName;
        if (nsUri == null) {
            nsUri = Namespaces.None;
        }
        if (!Namespaces.None.equals(nsUri)) {
            key = nsUri + "/" + key;
        }
        FaceletImp result = templateCache == null ? null : (FaceletImp) templateCache.get(key);
        if (result == null) {
            result = new FaceletImp(this, read(resourceName, nsUri), resourceName, nsUri);
            if (templateCache != null) {
                templateCache.put(key, result);
            }
        }
        return result;
    }

    private String read(String resourceName, String nsUri) throws IOException {
        ResourceReader resourceReader = resourceReaderByNsUri.get(nsUri);
        if (resourceReader == null) {
            throw new IOException("no resource reader to read " + getResourceInfo(resourceName, nsUri));
        }
        return read(resourceReader.read(resourceName));
    }

    String getResourceInfo(String resourceName, String nsUri) {
        String resourceInfo = "resource '" + resourceName + "'";
        if (!Namespaces.None.equals(nsUri)) {
            resourceInfo += ", namespace " + nsUri;
        }
        return resourceInfo;
    }

    public String read(InputStream in) throws IOException {
        try {
            InputStreamReader reader = new InputStreamReader(in, "utf-8");
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                // skip signature, if any
                if (buffer[0] == '\uFEFF') {
                    builder.append(buffer, 1, read - 1);
                } else {
                    builder.append(buffer, 0, read);
                }
            }
            return builder.toString();
        } finally {
            in.close();
        }
    }

    @Override
    public String html(Document node) {
        StringWriter writer = new StringWriter();
            OutputFormat format = new OutputFormat();
            format.setExpandEmptyElements(true);
        try (HTMLWriter htmlWriter = new EscapeAwareHtmlWriter(writer, format)){
            org.dom4j.Document document = toDom4j(node);
            
            if (document.getRootElement().getName().equals(DUMMY_ROOT)) {
                htmlWriter.write(document.getRootElement().content());
            } else {
                htmlWriter.write(document);
            }
            return writer.toString();
        } catch (IOException exc) {
            throw new RuntimeException("cannot write", exc);
        }
    }

    private static org.dom4j.Document toDom4j(Document jdomDocument) {
        DocumentFactory factory = DocumentFactory.getInstance();
        org.dom4j.Document result = factory.createDocument();
        DocType docType = jdomDocument.getDocType();
        if (docType != null) {
            result.addDocType(docType.getElementName(), docType.getPublicID(), docType.getSystemID());
        }
        for (Content content: jdomDocument.getContent()) {
            if (content instanceof Element element) {
                result.setRootElement(toDom4j(element, factory));
            } else if (content instanceof Comment comment) {
                result.addComment(comment.getText());
            } else if (content instanceof ProcessingInstruction pi) {
                result.addProcessingInstruction(pi.getTarget(), pi.getData());
            }
        }
        return result;
    }

    private static org.dom4j.Element toDom4j(Element element, DocumentFactory factory) {
        org.dom4j.Element result = factory.createElement(element.getName());
        List<Attribute> attributes = new ArrayList<Attribute>(element.getAttributes());
        // The previous W3C DOM (Xerces) implementation stored attributes sorted by
        // qualified name; keep that ordering so the rendered output is stable.
        attributes.sort(Comparator.comparing(Attribute::getQualifiedName));
        for (Attribute attr: attributes) {
            result.addAttribute(attr.getName(), attr.getValue());
        }
        for (Content content: element.getContent()) {
            if (content instanceof Element child) {
                result.add(toDom4j(child, factory));
            } else if (content instanceof CDATA cdata) {
                result.addCDATA(cdata.getText());
            } else if (content instanceof Text text) {
                result.addText(text.getText());
            } else if (content instanceof Comment comment) {
                result.addComment(comment.getText());
            } else if (content instanceof ProcessingInstruction pi) {
                result.addProcessingInstruction(pi.getTarget(), pi.getData());
            }
        }
        return result;
    }

    Document newDocument() {
        return new Document();
    }    
}