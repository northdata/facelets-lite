package org.faceletslite.imp;

import static org.faceletslite.imp.LocationAwareParser.getLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.stream.StreamResult;

import org.dom4j.DocumentFactory;
import org.dom4j.io.HTMLWriter;
import org.dom4j.io.OutputFormat;
import org.faceletslite.Configuration;
import org.faceletslite.CustomTag;
import org.faceletslite.Facelet;
import org.faceletslite.FaceletsCompiler;
import org.faceletslite.Namespace;
import org.faceletslite.ResourceReader;
import org.faceletslite.imp.LocationAwareParser.Location;
import org.jdom2.Attribute;
import org.jdom2.CDATA;
import org.jdom2.Comment;
import org.jdom2.Content;
import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Parent;
import org.jdom2.ProcessingInstruction;
import org.jdom2.Text;
import org.jdom2.input.JDOMParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.FunctionMapper;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

public class FaceletsCompilerImp implements FaceletsCompiler, CustomTag.Renderer {

    private static final Logger log = LoggerFactory.getLogger(FaceletsCompiler.class);
    private static final String DUMMY_ROOT = "dummy-root";

    private final Map<String, ResourceReader> resourceReaderByNsUri = new HashMap<String, ResourceReader>();
    private final Map<String, Namespace> namespaceByUri = new HashMap<String, Namespace>();
    private final ExpressionFactory expressionFactory;
    private final FunctionMapper functionMapper;
    private final ELResolver resolver;
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
        return new FaceletImp(read(in), "", Namespaces.None);
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
            result = new FaceletImp(read(resourceName, nsUri), resourceName, nsUri);
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

    private String getResourceInfo(String resourceName, String nsUri) {
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

    private Document newDocument() {
        return new Document();
    }    

    class FaceletImp implements Facelet {

        private final Document sourceDocument;
        private final String resourceName;
        private final String namespace;
        private final String sourceText;
        private final Map<String, String> environmentVars;
        private final String sourceDocTypeName;
        private final String sourceDocTypePublicId;
        private final String sourceDocTypeSystemId;

        FaceletImp(String sourceText, String resourceName, String namespace) throws IOException {
            this.sourceText = sourceText;
            this.resourceName = resourceName;
            this.namespace = namespace;
            this.sourceDocument = parse();
            DocType sourceDocType = sourceDocument.getDocType();
            sourceDocTypeName = sourceDocType == null ? null : sourceDocType.getElementName();
            sourceDocTypePublicId = sourceDocType == null ? null : sourceDocType.getPublicID();
            sourceDocTypeSystemId = sourceDocType == null ? null : sourceDocType.getSystemID();

            this.environmentVars = new HashMap<String, String>();
            environmentVars.put(Environment.Namespace, namespace);
            environmentVars.put(Environment.ResourceName, resourceName);
            environmentVars.put(Environment.ResourcePath, getResourcePath());
            environmentVars.put(Environment.SourceText, sourceText);
        }

        public Map<String, String> getEnvironmentVars() {
            return environmentVars;
        }

        private Document parse() throws IOException {
            String resourceInfo = getResourceInfo(resourceName, namespace);
            try {
                return LocationAwareParser.parse(sourceText, resourceInfo);
            } catch (JDOMParseException exc) {
                int line = exc.getLineNumber();
                int col = exc.getColumnNumber();
                if (line >= 0) {
                    resourceInfo += ", line " + line;
                    if (col > 0) {
                        resourceInfo += ", column " + col;
                    }
                }
                throw new RuntimeException("cannot parse " + resourceInfo + ":\r\n\t" + exc.getMessage(), exc);
            } catch (JDOMException exc) {
                throw new RuntimeException("cannot parse " + resourceInfo + ":\r\n\t" + exc.getMessage(), exc);
            }
        }

        public String getResourceName() {
            return resourceName;
        }

        public String getNamespace() {
            return namespace;
        }

        @Override
        public String toString() {
            return sourceText;
        }

        String getResourcePath() {
            String result = "";
            int lastIndexOfSlash = resourceName.lastIndexOf("/");
            if (lastIndexOfSlash >= 0) {
                result = resourceName.substring(0, lastIndexOfSlash + 1);
            }
            return result;
        }

        String normalizeResourceNamePath(String resourceName) {
            boolean absolute = resourceName.startsWith("/");
            if (!absolute) {
                resourceName = getResourcePath() + resourceName;
            }
            return resourceName;
        }

        RuntimeException error(String message, Exception reason) {
            message += "\r\n\t(while parsing '" + getResourceName() + "'";
            if (Is.notEmpty(namespace)) {
                message += ", namespace " + namespace;
            }
            message += ")";
            throw new RuntimeException(message, reason);
        }

        RuntimeException error(String message) {
            throw error(message, (Exception) null);
        }

        RuntimeException error(String message, Location location) {
            throw error(message + ", line " + location.line() + ", column " + location.column(), (Exception) null);
        }

        RuntimeException error(String message, Location location, Exception reason) {
            throw error(message + ", line " + location.line() + ", column " + location.column(), reason);
        }


        @Override
        public String render(Object scope) {
            Document targetDocument = newDocument();
            List<Content> processedNodes = process(targetDocument, new MutableContext().scope(scope), null);

            if (hasDocType(processedNodes)) {
                Dom.appendChildren(targetDocument, processedNodes);

            } else {
                Element root = new Element(DUMMY_ROOT);
                targetDocument.addContent(root);
                Dom.appendChildren(root, processedNodes);
            }
            return html(targetDocument);
        }

        boolean hasDocType(List<Content> nodes) {
            if (nodes.size() > 0) {
                if (nodes.get(0) instanceof DocType) {
                    return true;
                }
            }
            return false;
        }

        List<Content> process(Document targetDocument, MutableContext context, Map<String, SourceFragment> defines) {
            return process(new Processor(targetDocument, context, defines));
        }

        List<Content> process(Processor processor) {
            Parent sourceRoot = getRootNode(sourceDocument);
            List<Content> result = new ArrayList<Content>();
            if (Is.notEmpty(sourceDocTypeName)) {
                result.add(new DocType(sourceDocTypeName, sourceDocTypePublicId, sourceDocTypeSystemId));
            }
            result.addAll(processor.compile(sourceRoot));
            return result;
        }

        Parent getRootNode(Document sourceDocument) {
            for (Element composition: Dom.elementsByTagName(sourceDocument, Namespaces.Ui, "composition")) {
                return composition;
            }
            for (Element component: Dom.elementsByTagName(sourceDocument, Namespaces.Ui, "component")) {
                return component;
            }
            return sourceDocument;
        }

        class Processor implements CustomTag.Processor {

            private final Document targetDocument;
            private final Map<String, SourceFragment> defines;
            private MutableContext context;
            private boolean swallowComments = true;

            public Processor(Document targetDocument, MutableContext context, Map<String, SourceFragment> defines) {
                this.targetDocument = targetDocument;
                this.context = context;
                this.defines = defines;
            }

            @Override
            public Document getTargetDocument() {
                return targetDocument;
            }

            Processor with(MutableContext context, Map<String, SourceFragment> defines) {
                return new Processor(targetDocument, context, defines);
            }

            List<Content> compileHtmlTag(Element sourceElement) {
                Element targetElement = new Element(sourceElement.getName());
                for (Attribute attr: Dom.attrs(sourceElement)) {
                    if (isHtmlNamespace(Dom.nsUri(attr))) {
                        String newValue = eval(attr);
                        if (Is.notEmpty(newValue)) {
                            targetElement.setAttribute(attr.getName(), newValue);
                        }
                    }
                }
                Dom.appendChildren(targetElement, compileChildren(sourceElement));
                return nodes(targetElement);
            }

            List<Content> compileJspCoreTag(Element element) {
                String tagName = element.getName();
                if ("set".equals(tagName)) {
                    Object value = attr(element, "value", Object.class);
                    String var = requiredAttr(element, "var", String.class);
                    context = context.put(var, value);
                    return nodes();
                }
                if ("if".equals(tagName)) {
                    Object test = attr(element, "test", Object.class);
                    String var = attr(element, "var", String.class);
                    if (Is.conditionTrue(test)) {
                        return with(
                            context.put(var, test),
                            defines).compileChildren(element);
                    }
                    return nodes();
                }
                if ("forEach".equalsIgnoreCase(tagName)) {
                    List<Content> result = new ArrayList<Content>();
                    Object _items = attr(element, "items", Object.class);
                    Iterable<?> iterable = null;
                    if (_items == null) {

                    } else if (_items instanceof Iterable<?>) {
                        iterable = (Iterable<?>) _items;
                    } else if (_items instanceof Map<?, ?>) {
                        iterable = ((Map<?, ?>) _items).entrySet();
                    } else {
                        throw error("Cannot convert class " + _items.getClass() + " to Iterable", getLocation(element));
                    }

                    List<Object> items = new ArrayList<Object>();
                    if (iterable != null) {
                        for (Object item: iterable) {
                            items.add(item);
                        }
                    }
                    String var = attr(element, "var", String.class);
                    String varStatus = attr(element, "varStatus", String.class);
                    String begin = attr(element, "begin", String.class);
                    String end = attr(element, "end", String.class);
                    String step = attr(element, "step", String.class);
                    LoopTagStatusImp status = new LoopTagStatusImp(
                        Safe.toInt(begin, 0),
                        Safe.toInt(end, items.size() - 1),
                        Safe.toInt(step, 1),
                        items);
                    while (status.hasNext()) {
                        result.addAll(
                            with(
                                context.nest() // ??
                                    .put(var, status.getCurrent())
                                    .put(varStatus, status),
                                defines).compileChildren(element));
                        status.next();
                    }
                    return result;
                }
                if ("choose".equals(tagName)) {
                    for (Element when: Dom.childrenByTagName(element, Namespaces.CoreEquivalent, "when")) {
                        boolean test = requiredAttr(when, "test", boolean.class);
                        if (test) {
                            return compileChildren(when);
                        }
                    }
                    for (Element otherwise: Dom.childrenByTagName(element, Namespaces.CoreEquivalent, "otherwise")) {
                        return compileChildren(otherwise);
                    }
                    return nodes();
                }
                throw error("invalid core tag name '" + tagName + "'", getLocation(element));
            }

            List<Content> compileUiTag(Element element) {
                String tagName = element.getName();
                if ("with".equals(tagName)) {
                    Object value = attr(element, "value", Object.class);
                    MutableContext newContext = value == null ? context : context.nest().scope(value);
                    return with(newContext, defines).compileChildren(element);
                }
                if ("include".equals(tagName)) {
                    String src = attr(element, "src", String.class);
                    String namespace = attr(element, "namespace", String.class);
                    MutableContext newContext = collectParams(element);
                    if (Is.empty(src)) {
                        return with(newContext, defines).compileChildren(element);
                    } else {
                        try {
                            FaceletImp template = FaceletsCompilerImp.this.compile(normalizeResourceNamePath(src),
                                namespace == null ? getNamespace() : namespace);
                            with(newContext, defines).compileChildren(template.getRootNode(targetDocument));
                            return template.process(targetDocument, newContext, defines);
                        } catch (IOException exc) {
                            throw error("cannot include '" + src + "'", getLocation(element), exc);
                        }
                    }
                }
                if ("insert".equals(tagName)) {
                    String name = attr(element, "name", String.class);
                    if (name == null) {
                        name = "";
                    }
                    SourceFragment fragment = defines == null ? null : defines.get(name);
                    if (fragment == null) {
                        return compileChildren(element);
                    } else {
                        return with(context, fragment.getDefinitions())
                            .compileChildren(fragment.getRoot());
                    }
                }
                if ("composition".equals(tagName)) {
                    String templateAttr = attr(element, "template", String.class);
                    if (Is.empty(templateAttr)) {
                        return compileChildren(element);
                    } else {
                        return applyTemplate(element, templateAttr);
                    }
                }
                if ("component".equals(tagName) || "fragment".equals(tagName)) {
                    return compileChildren(element);
                }
                if ("decorate".equals(tagName)) {
                    String template = requiredAttr(element, "template", String.class);
                    return applyTemplate(element, template);
                }
                if ("debug".equals(tagName)) {
                    log.warn("ignoring ui debug tag");
                    return nodes();
                }
                if ("remove".equals(tagName)) {
                    // nothing
                    return nodes();
                }
                if ("param".equals(tagName)) {
                    // ignore (already processed)
                    return nodes();
                }
                if ("define".equals(tagName)) {
                    // ignore (already processed)
                    return nodes();
                }
                throw error("invalid ui tag name '" + tagName + "'", getLocation(element));
            }

            List<Content> compileJsfHTag(Element element) {
                String tagName = element.getName();
                if ("outputText".equals(tagName)) {
                    String value = attr(element, "value", String.class);
                    if (Is.empty(value)) {
                        return nodes();
                    } else {
                        Boolean escape = attr(element, "escape", Boolean.class);
                        return text(value, escape == null || !escape.equals(Boolean.FALSE));
                    }
                }
                throw error("invalid h tag name '" + tagName + "'", getLocation(element));
            }

            List<Content> compileCustomTag(Element element) {
                String tagName = element.getName();
                String nsUri = Dom.nsUri(element);
                Namespace namespace = namespaceByUri.get(nsUri);
                if (namespace != null) {
                    CustomTag customTag = namespace.getCustomTag(tagName);
                    if (customTag != null) {
                        return customTag.process(element, this, FaceletsCompilerImp.this);
                    }
                }
                MutableContext newContext = context.nest();
                for (Attribute attr: Dom.attrs(element)) {
                    newContext.put(
                        attr.getName(),
                        eval(attr.getValue(), getLocation(attr), Object.class));
                }
                try {
                    FaceletImp template = FaceletsCompilerImp.this.compile(tagName, nsUri);
                    return template.process(with(
                        newContext,
                        collectDefines(element)));
                } catch (IOException exc) {
                    throw error("cannot load " + element.getNamespacePrefix() + ":" + tagName, getLocation(element), exc);
                }
            }

            List<Content> compile(Parent sourceNode) {
                if (sourceNode instanceof Document document) {
                    return compileList(document.getContent());
                }
                return compileContent((Content) sourceNode);
            }

            List<Content> compileContent(Content sourceNode) {
                if (sourceNode instanceof Text text) {
                    String sourceText = text.getText();
                    String targetText = eval(sourceText, getLocation(sourceNode), String.class);
                    if (Is.empty(targetText)) {
                        return nodes();
                    } else {
                        return text(targetText, true);
                    }
                } else if (sourceNode instanceof Element sourceElement) {
                    String nsUri = Dom.nsUri(sourceElement);
                    if (isHtmlNamespace(nsUri)) {
                        return compileHtmlTag(sourceElement);
                    } else if (Namespaces.Core.equals(nsUri) || Namespaces.JspCore.equals(nsUri)) {
                        return compileJspCoreTag(sourceElement);
                    } else if (Namespaces.Ui.equals(nsUri)) {
                        return compileUiTag(sourceElement);
                    } else if (Namespaces.JsfH.equals(nsUri)) {
                        return compileJsfHTag(sourceElement);
                    } else {
                        return compileCustomTag(sourceElement);
                    }
                } else if (sourceNode instanceof Comment comment) {
                    if (!swallowComments) {
                        return nodes(new Comment(comment.getText()));
                    }
                } else if (sourceNode instanceof ProcessingInstruction instruction) {
                    if ("facelets".equals(instruction.getTarget())) {
                        String data = instruction.getData().trim();
                        if (data.equals("suspendEvaluation")) {
                            context = context.nest().suspend(true);
                        }
                        if (data.equals("swallowComments='true'") || data.equals("swallowComments=\"true\"")
                            || data.equals("swallowComments=true")) {
                            swallowComments = true;
                        }
                        if (data.equals("swallowComments='false'") || data.equals("swallowComments=\"false\"")
                            || data.equals("swallowComments=false")) {
                            swallowComments = false;
                        }
                    }
                }
                return nodes();
            }

            List<Content> compileList(List<? extends Content> sourceNodes) {
                List<Content> result = new ArrayList<Content>();
                for (Content node: sourceNodes) {
                    result.addAll(
                        compileContent(node));
                }
                return result;
            }

            @Override
            public List<Content> compileChildren(Element sourceElement) {
                return compileList(sourceElement.getContent());
            }

            List<Content> compileChildren(Parent sourceParent) {
                return compileList(Dom.content(sourceParent));
            }

            public List<Content> applyTemplate(Element sourceElement, String templateAttr) {
                try {
                    FaceletImp template = FaceletsCompilerImp.this.compile(normalizeResourceNamePath(templateAttr));
                    return template.process(
                        getTargetDocument(),
                        collectParams(sourceElement),
                        collectDefines(sourceElement));
                } catch (IOException exc) {
                    throw error("cannot read template '" + templateAttr + "'", getLocation(sourceElement), exc);
                }
            }

            @Override
            public List<Content> text(String text, boolean escape) {
                List<Content> result = new ArrayList<Content>();
                if (!escape) {
                    result.add(
                        new ProcessingInstruction(StreamResult.PI_DISABLE_OUTPUT_ESCAPING, ""));
                }
                result.add(new Text(text));
                if (!escape) {
                    result.add(
                        new ProcessingInstruction(StreamResult.PI_ENABLE_OUTPUT_ESCAPING, ""));
                }
                return result;
            }

            public List<Content> nodes(Content... nodes) {
                switch (nodes.length) {
                case 0:
                    return Collections.emptyList();
                case 1:
                    return Collections.singletonList(nodes[0]);
                }
                List<Content> result = new ArrayList<Content>();
                for (Content node: nodes) {
                    result.add(node);
                }
                return result;
            }

            Map<String, SourceFragment> collectDefines(Element parent) {
                Map<String, SourceFragment> result = new HashMap<String, SourceFragment>();
                if (defines != null) {
                    result.putAll(defines);
                }
                for (Element define: Dom.childrenByTagName(parent, Namespaces.UiEquivalent, "define")) {
                    String name = requiredAttr(define, "name", String.class);
                    result.put(name, new SourceFragment(define, context, defines));
                }
                result.put("", new SourceFragment(parent, context, defines));
                return result;
            }

            MutableContext collectParams(Element parent) {
                MutableContext result = context.nest();
                for (Element param: Dom.childrenByTagName(parent, Namespaces.UiEquivalent, "param")) {
                    result.put(
                        requiredAttr(param, "name", String.class),
                        attr(param, "value", Object.class));
                }
                return result;
            }

            boolean isHtmlNamespace(String nsUri) {
                return Is.empty(nsUri) || Namespaces.Xhtml.equals(nsUri);
            }

            String eval(Attribute attr) {
                return eval(attr.getValue(), getLocation(attr), String.class);
            }

            @Override
            public <T> T attr(Element element, String name, Class<T> clazz) {
                String value = element.getAttributeValue(name);
                return Is.empty(value) ? null : eval(value, getLocation(element), clazz);
            }

            @Override
            public <T> T requiredAttr(Element element, String name, Class<T> clazz) {
                T result = attr(element, name, clazz);
                if (Is.empty(result)) {
                    throw error("missing attribute '" + name + "' in " + element.getName(), getLocation(element));
                }
                return result;
            }

            <T> T eval(String text, Location location, Class<T> clazz) {
                try {
                    return context.eval(
                        text,
                        clazz,
                        getEnvironmentVars());
                } catch (RuntimeException exc) {
                    String message = text + " expression evaluation failed:\r\n\t" + exc.getMessage();
                    throw error(message, location, exc);
                }
            }
        }
    }

    class MutableContext extends ELContext {

        private final ELContext fallback;
        private Object scope;
        private Object environmentVars;
        private boolean suspended;
        private final Map<String, ValueExpression> variables = new LinkedHashMap<String, ValueExpression>();
        private final VariableMapper variableMapper = new VariableMapper() {

            @Override
            public ValueExpression setVariable(String name, ValueExpression expr) {
                variables.put(name, expr);
                return expr;
            }

            @Override
            public ValueExpression resolveVariable(String name) {
                Object value = scope == null ? null : resolver.getValue(MutableContext.this, scope, name);
                if (value != null) {
                    return wrap(value);
                }
                value = environmentVars == null ? null : resolver.getValue(MutableContext.this, environmentVars, name);
                if (value != null) {
                    return wrap(value);
                }
                ValueExpression expr = variables.get(name);
                if (expr != null) {
                    return expr;
                }
                return fallback == null ? wrap(null) : fallback.getVariableMapper().resolveVariable(name);
            }
        };

        MutableContext() {
            this(null);
        }

        MutableContext(ELContext fallback) {
            this.fallback = fallback;
            this.suspended = (fallback instanceof MutableContext) ? ((MutableContext) fallback).suspended : false;
        }

        MutableContext nest() {
            return new MutableContext(this);
        }

        MutableContext scope(Object scope) {
            this.scope = scope;
            return this;
        }

        MutableContext suspend(boolean suspend) {
            this.suspended = suspend;
            return this;
        }

        MutableContext put(String name, Object value) {
            if (name != null) {
                variableMapper.setVariable(name, wrap(value));
            }
            return this;
        }

        private ValueExpression wrap(Object value) {
            return expressionFactory.createValueExpression(value, Object.class);
        }

        @Override
        public ELResolver getELResolver() {
            return resolver;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return functionMapper;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return variableMapper;
        }

        @SuppressWarnings("unchecked")
        <T> T eval(String text, Class<?> clazz, Object environmentVars) {
            if (suspended) {
                if (clazz == String.class || clazz == Object.class) {
                    return (T) text;
                }
            }
            this.environmentVars = environmentVars;
            return (T) expressionFactory
                .createValueExpression(this, text, clazz)
                .getValue(this);
        }
    }
}