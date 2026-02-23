package org.faceletslite.imp;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

public class LocationAwareParser {

    private static final String LOCATION_KEY = "xml:source-location";

    // Simple immutable record to hold location
    public record Location(int line, int column, String resourceInfo) {
    }

    // Single handler implementing both ContentHandler and LexicalHandler
    private static class Handler extends DefaultHandler implements LexicalHandler {

        private final Document doc;
        private final String resourceInfo;
        private final Deque<Node> stack = new ArrayDeque<>();
        private final StringBuilder text = new StringBuilder();
        private DocumentType doctype;
        private Locator locator;

        Handler(Document doc, String resourceInfo) {
            this.doc = doc;
            this.resourceInfo = resourceInfo;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        private void attachLocation(Node node) {
            if (locator != null) {
                Location loc = new Location(
                        locator.getLineNumber(),
                        locator.getColumnNumber(),
                        resourceInfo);
                node.setUserData(LOCATION_KEY, loc, null);
            }
        }

        @Override
        public void startDocument() {
            stack.push(doc);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            flushText();

            Element element = doc.createElementNS(uri, qName);
            attachLocation(element);

            for (int i = 0; i < atts.getLength(); i++) {
                Attr attr = doc.createAttributeNS(atts.getURI(i), atts.getQName(i));
                attr.setValue(atts.getValue(i));
                attachLocation(attr);
                element.setAttributeNodeNS(attr);
            }

            stack.peek().appendChild(element);
            stack.push(element);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            flushText();
            stack.pop();
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            text.append(ch, start, length);
        }

        private void flushText() {
            if (!text.isEmpty()) {
                Text textNode = doc.createTextNode(text.toString());
                attachLocation(textNode);
                stack.peek().appendChild(textNode);
                text.setLength(0);
            }
        }

        @Override
        public void comment(char[] ch, int start, int length) {
            flushText();
            Comment comment = doc.createComment(new String(ch, start, length));
            attachLocation(comment);
            stack.peek().appendChild(comment);
        }

        @Override
        public void processingInstruction(String target, String data) {
            flushText();
            ProcessingInstruction pi = doc.createProcessingInstruction(target, data);
            attachLocation(pi);
            stack.peek().appendChild(pi);
        }

        @Override
        public void endDocument() {
            flushText();
            if (doctype != null) {
                doc.appendChild(doctype); // DOCTYPE becomes first child
            }
        }

        @Override
        public void startDTD(String name, String publicId, String systemId) {
            doctype = doc.getImplementation().createDocumentType(name, publicId, systemId);
            attachLocation(doctype);
        }

        @Override
        public void endDTD() {
        }

        @Override
        public void startCDATA() {
        }

        @Override
        public void endCDATA() {
        }

        @Override
        public void startEntity(String name) {
        }

        @Override
        public void endEntity(String name) {
        }
    }

    public static Document parseWithLocations(DocumentBuilder builder, InputSource inputSource, String resourceName)
            throws IOException, SAXException, ParserConfigurationException {
        Document doc = builder.newDocument(); // empty document

        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        SAXParser saxParser = spf.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();

        Handler handler = new Handler(doc, resourceName);
        xmlReader.setContentHandler(handler);
        xmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);

        xmlReader.parse(inputSource);
        return doc;

    }

    public static Location getLocation(Node node) {
        Object data = node.getUserData(LOCATION_KEY);
        return data instanceof Location ? (Location) data : null;
    }

    public static Document cloneDocumentPreservingLocation(DocumentBuilder builder, Document original) {
        Document cloned = builder.newDocument();

        // Import the root element (deep)
        Element clonedRoot = (Element) cloned.importNode(original.getDocumentElement(), true);
        cloned.appendChild(clonedRoot);

        // Copy DOCTYPE if present
        if (original.getDoctype() != null) {
            DocumentType dt = original.getDoctype();
            DocumentType clonedDt = cloned.getImplementation().createDocumentType(
                    dt.getName(), dt.getPublicId(), dt.getSystemId());
            Object loc = dt.getUserData(LOCATION_KEY);
            if (loc != null) {
                clonedDt.setUserData(LOCATION_KEY, loc, null);
            }
            cloned.appendChild(clonedDt);
        }

        // Recursively copy the LOCATION_KEY to all nodes
        copyLocationRecursively(original.getDocumentElement(), clonedRoot);

        return cloned;
    }

    private static void copyLocationRecursively(Node src, Node dst) {
        Object loc = src.getUserData(LOCATION_KEY);
        if (loc != null) {
            dst.setUserData(LOCATION_KEY, loc, null);
        }

        NodeList srcChildren = src.getChildNodes();
        NodeList dstChildren = dst.getChildNodes();
        for (int i = 0; i < srcChildren.getLength(); i++) {
            copyLocationRecursively(srcChildren.item(i), dstChildren.item(i));
        }
    }
}
