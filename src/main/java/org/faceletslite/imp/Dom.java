package org.faceletslite.imp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Parent;

class Dom {

    static List<Content> content(Parent parent) {
        if (parent instanceof Document document) {
            return document.getContent();
        }
        return ((Element) parent).getContent();
    }

    static Iterable<Element> elementsByTagName(Parent parent, String nsUri, String tagName) {
        List<Element> result = new ArrayList<Element>();
        for (Content content: content(parent)) {
            if (content instanceof Element element) {
                collectByTagName(element, nsUri, tagName, result);
            }
        }
        return result;
    }

    private static void collectByTagName(Element element, String nsUri, String tagName, List<Element> result) {
        if (element.getName().equals(tagName) && nsUri.equals(element.getNamespaceURI())) {
            result.add(element);
        }
        for (Element child: element.getChildren()) {
            collectByTagName(child, nsUri, tagName, result);
        }
    }

    static Iterable<Element> childrenByTagName(Parent parent, Set<String> nsUris, String tagName) {
        List<Element> elements = new ArrayList<Element>();
        for (Content content: content(parent)) {
            if (content instanceof Element element
                && element.getName().equals(tagName)
                && nsUris.contains(element.getNamespaceURI())) {
                elements.add(element);
            }
        }
        return elements;
    }

    static List<Attribute> attrs(Element element) {
        return element.getAttributes();
    }

    static void appendChildren(Parent parent, List<Content> children) {
        if (parent instanceof Document document) {
            for (Content child: children) {
                document.addContent(child);
            }
        } else {
            Element element = (Element) parent;
            for (Content child: children) {
                element.addContent(child);
            }
        }
    }

    static String nsUri(Element element) {
        return element.getNamespaceURI();
    }

    static String nsUri(Attribute attr) {
        return attr.getNamespaceURI();
    }
}
