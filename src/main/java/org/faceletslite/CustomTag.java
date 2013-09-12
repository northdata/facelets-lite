package org.faceletslite;

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
		void handleChildren(Node node);
		void appendText(String text, boolean escape);
		Node getTargetParent();
	}
	
	public void process(Element element, Processor processor, Renderer renderer);
}
