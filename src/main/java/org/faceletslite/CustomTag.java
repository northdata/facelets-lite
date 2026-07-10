package org.faceletslite;

import java.util.List;

import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;

public interface CustomTag
{
	interface Renderer
	{
		String html(Document node);
	}

	interface Processor
	{
	    <T> T attr(Element element, String name, Class<T> clazz);
	    <T> T requiredAttr(Element element, String name, Class<T> clazz);
		List<Content> compileChildren(Element element);
		List<Content> text(String text, boolean escape);
		Document getTargetDocument();
	}

	public List<Content> process(Element element, Processor processor, Renderer renderer);
}
