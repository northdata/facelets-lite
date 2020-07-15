package org.faceletslite.imp;

import java.io.IOException;
import java.io.Writer;

import javax.xml.transform.stream.StreamResult;

import org.dom4j.ProcessingInstruction;
import org.dom4j.io.HTMLWriter;
import org.dom4j.io.OutputFormat;

/**
 * Extends {@link HTMLWriter} to honour XML processing instructions for disabling
 * and enabling output escaping.
 *
 * @author hwellmann
 *
 */
public class EscapeAwareHtmlWriter extends HTMLWriter {

	public EscapeAwareHtmlWriter(Writer writer, OutputFormat format) {
		super(writer, format);
	}

	@Override
	protected void writeProcessingInstruction(ProcessingInstruction pi) throws IOException {
		if (pi.getTarget().equals(StreamResult.PI_DISABLE_OUTPUT_ESCAPING)) {
			setEscapeText(false);
		} else if (pi.getTarget().equals(StreamResult.PI_ENABLE_OUTPUT_ESCAPING)) {
			setEscapeText(true);
		}
	}
}
