package org.faceletslite;

import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public interface CustomTag 
{
	interface Renderer 
	{
		String html(Node node);
	}
	
	interface Processor 
	{
	    <T> T attr(Element element, String name, Class<T> clazz);
	    <T> T requiredAttr(Element element, String name, Class<T> clazz);
		List<Node> compileChildren(Node node);
		List<Node> text(String text, boolean escape);
		Document getTargetDocument();
	}
	
	public List<Node> process(Element element, Processor processor, Renderer renderer);
}
