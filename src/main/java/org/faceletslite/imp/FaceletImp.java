package org.faceletslite.imp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.faceletslite.Facelet;
import org.faceletslite.imp.LocationAwareParser.Location;
import org.jdom2.Content;
import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Parent;
import org.jdom2.input.JDOMParseException;

class FaceletImp implements Facelet {

    final FaceletsCompilerImp compiler;
    private final Document sourceDocument;
    private final String resourceName;
    private final String namespace;
    private final String sourceText;
    private final Map<String, String> environmentVars;
    private final String sourceDocTypeName;
    private final String sourceDocTypePublicId;
    private final String sourceDocTypeSystemId;

    FaceletImp(FaceletsCompilerImp compiler, String sourceText, String resourceName, String namespace)
        throws IOException {
        this.compiler = compiler;
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
        String resourceInfo = compiler.getResourceInfo(resourceName, namespace);
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
        Document targetDocument = compiler.newDocument();
        List<Content> processedNodes = process(targetDocument, new MutableContext(compiler).scope(scope), null);

        if (hasDocType(processedNodes)) {
            Dom.appendChildren(targetDocument, processedNodes);

        } else {
            Element root = new Element(FaceletsCompilerImp.DUMMY_ROOT);
            targetDocument.addContent(root);
            Dom.appendChildren(root, processedNodes);
        }
        return compiler.html(targetDocument);
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
        return process(new Processor(this, targetDocument, context, defines));
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
}
