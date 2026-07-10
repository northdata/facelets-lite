package org.faceletslite.imp;

import java.io.IOException;
import java.io.StringReader;

import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.located.Located;
import org.jdom2.located.LocatedJDOMFactory;
import org.xml.sax.InputSource;

/**
 * Parses XML source into a JDOM 2 {@link Document} while preserving source
 * locations (line/column) on the produced nodes.
 * <p>
 * Location tracking is provided out of the box by JDOM's
 * {@link LocatedJDOMFactory}: the resulting elements, text, comments and
 * processing instructions implement {@link Located}.
 */
public class LocationAwareParser {

    private static final String LOAD_EXTERNAL_DTD =
        "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    // Simple immutable record to hold location
    public record Location(int line, int column, String resourceInfo) {
    }

    private static final Location UNKNOWN_LOCATION = new Location(-1, -1, "unknown");

    public static Document parse(String sourceText, String resourceInfo) throws IOException, JDOMException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setJDOMFactory(new LocatedJDOMFactory());
        builder.setExpandEntities(false);
        builder.setFeature(LOAD_EXTERNAL_DTD, false);
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.build(new StringReader(sourceText));
    }

    public static Location getLocation(Content node) {
        if (node instanceof Located located) {
            return new Location(located.getLine(), located.getColumn(), null);
        }
        return UNKNOWN_LOCATION;
    }

    public static Location getLocation(Attribute attr) {
        return attr == null ? UNKNOWN_LOCATION : getLocation((Content) attr.getParent());
    }
}
