package org.faceletslite.imp;

import static org.faceletslite.imp.LocationAwareParser.getLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.stream.StreamResult;

import org.faceletslite.CustomTag;
import org.faceletslite.Namespace;
import org.faceletslite.imp.LocationAwareParser.Location;
import org.jdom2.Attribute;
import org.jdom2.Comment;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Parent;
import org.jdom2.ProcessingInstruction;
import org.jdom2.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Processor implements CustomTag.Processor {

    private static final Logger log = LoggerFactory.getLogger(FaceletsCompilerImp.class);

    private final FaceletImp facelet;
    private final FaceletsCompilerImp compiler;
    private final Document targetDocument;
    private final Map<String, SourceFragment> defines;
    private MutableContext context;
    private boolean swallowComments = true;

    Processor(FaceletImp facelet, Document targetDocument, MutableContext context, Map<String, SourceFragment> defines) {
        this.facelet = facelet;
        this.compiler = facelet.compiler;
        this.targetDocument = targetDocument;
        this.context = context;
        this.defines = defines;
    }

    @Override
    public Document getTargetDocument() {
        return targetDocument;
    }

    @Override
    public List<Content> compileChildren(Element sourceElement) {
        return compileList(sourceElement.getContent());
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

    @Override
    public <T> T attr(Element element, String name, Class<T> clazz) {
        String value = element.getAttributeValue(name);
        return Is.empty(value) ? null : eval(value, getLocation(element), clazz);
    }

    @Override
    public <T> T requiredAttr(Element element, String name, Class<T> clazz) {
        T result = attr(element, name, clazz);
        if (Is.empty(result)) {
            throw facelet.error("missing attribute '" + name + "' in " + element.getName(), getLocation(element));
        }
        return result;
    }

    List<Content> compile(Parent sourceNode) {
        if (sourceNode instanceof Document document) {
            return compileList(document.getContent());
        }
        return compileContent((Content) sourceNode);
    }

    private Processor with(MutableContext context, Map<String, SourceFragment> defines) {
        return new Processor(facelet, targetDocument, context, defines);
    }

    private List<Content> compileHtmlTag(Element sourceElement) {
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

    private List<Content> compileJspCoreTag(Element element) {
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
                throw facelet.error("Cannot convert class " + _items.getClass() + " to Iterable", getLocation(element));
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
        throw facelet.error("invalid core tag name '" + tagName + "'", getLocation(element));
    }

    private List<Content> compileUiTag(Element element) {
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
                    FaceletImp template = compiler.compile(facelet.normalizeResourceNamePath(src),
                        namespace == null ? facelet.getNamespace() : namespace);
                    return template.process(targetDocument, newContext, defines);
                } catch (IOException exc) {
                    throw facelet.error("cannot include '" + src + "'", getLocation(element), exc);
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
        throw facelet.error("invalid ui tag name '" + tagName + "'", getLocation(element));
    }

    private List<Content> compileJsfHTag(Element element) {
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
        throw facelet.error("invalid h tag name '" + tagName + "'", getLocation(element));
    }

    private List<Content> compileCustomTag(Element element) {
        String tagName = element.getName();
        String nsUri = Dom.nsUri(element);
        Namespace namespace = compiler.namespaceByUri.get(nsUri);
        if (namespace != null) {
            CustomTag customTag = namespace.getCustomTag(tagName);
            if (customTag != null) {
                return customTag.process(element, this, compiler);
            }
        }
        MutableContext newContext = context.nest();
        for (Attribute attr: Dom.attrs(element)) {
            newContext.put(
                attr.getName(),
                eval(attr.getValue(), getLocation(attr), Object.class));
        }
        try {
            FaceletImp template = compiler.compile(tagName, nsUri);
            return template.process(with(
                newContext,
                collectDefines(element)));
        } catch (IOException exc) {
            throw facelet.error("cannot load " + element.getNamespacePrefix() + ":" + tagName, getLocation(element), exc);
        }
    }

    private List<Content> compileContent(Content sourceNode) {
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
            } else if (Namespaces.Page.equals(nsUri)) {
                return compileJspPageTag(sourceElement);
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

    private List<Content> compileJspPageTag(Element element) {
        String tagName = element.getName();
        if ("useBean".equals(tagName)) {
            String id = attr(element, "id", String.class);
            String className = attr(element, "class", String.class);
            if (Is.empty(id) || Is.empty(className)) {
                return nodes();
            }
            Class<?> clazz = loadClass(className);
            if (clazz == null) {
                throw facelet.error("cannot load class '" + className + "'", getLocation(element));
            }
            Object bean = eval("#{" + id + "}", getLocation(element), Object.class);
            if (bean == null) {
                throw facelet.error("no context object named '" + id + "' found for useBean", getLocation(element));
            }
            if (!clazz.isInstance(bean)) {
                throw facelet.error(
                    "context object '" + id + "' has type " + bean.getClass().getName()
                        + " but useBean requires " + className,
                    getLocation(element));
            }
            return nodes();
        }
        throw facelet.error("invalid jsp tag name '" + tagName + "'", getLocation(element));
    }

    private List<Content> compileList(List<? extends Content> sourceNodes) {
        List<Content> result = new ArrayList<Content>();
        for (Content node: sourceNodes) {
            result.addAll(
                compileContent(node));
        }
        return result;
    }

    private List<Content> compileChildren(Parent sourceParent) {
        return compileList(Dom.content(sourceParent));
    }

    private List<Content> applyTemplate(Element sourceElement, String templateAttr) {
        try {
            FaceletImp template = compiler.compile(facelet.normalizeResourceNamePath(templateAttr));
            return template.process(
                getTargetDocument(),
                collectParams(sourceElement),
                collectDefines(sourceElement));
        } catch (IOException exc) {
            throw facelet.error("cannot read template '" + templateAttr + "'", getLocation(sourceElement), exc);
        }
    }

    private List<Content> nodes(Content... nodes) {
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

    private Map<String, SourceFragment> collectDefines(Element parent) {
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

    private MutableContext collectParams(Element parent) {
        MutableContext result = context.nest();
        for (Element param: Dom.childrenByTagName(parent, Namespaces.UiEquivalent, "param")) {
            result.put(
                requiredAttr(param, "name", String.class),
                attr(param, "value", Object.class));
        }
        return result;
    }

    private boolean isHtmlNamespace(String nsUri) {
        return Is.empty(nsUri) || Namespaces.Xhtml.equals(nsUri);
    }

    private String eval(Attribute attr) {
        return eval(attr.getValue(), getLocation(attr), String.class);
    }

    private <T> T eval(String text, Location location, Class<T> clazz) {
        try {
            return context.eval(
                text,
                clazz,
                facelet.getEnvironmentVars());
        } catch (RuntimeException exc) {
            String message = text + " expression evaluation failed:\r\n\t" + exc.getMessage();
            throw facelet.error(message, location, exc);
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException exc) {
            return null;
        }
    }

}
