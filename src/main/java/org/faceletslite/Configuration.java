package org.faceletslite;

import java.util.List;
import java.util.Map;

public interface Configuration 
{
	// File I/O
	ResourceReader getResourceReader();
	List<Namespace> getCustomNamespaces();

	// XML Handling
	javax.xml.parsers.DocumentBuilder createDocumentBuilder();
	javax.xml.transform.Transformer createDocumentTransformer();
	
	// EL Expression Language 
	javax.el.ExpressionFactory getExpressionFactory();
	javax.el.ELResolver getELResolver();
	javax.el.FunctionMapper getFunctionMapper();

	// Else...
	Map<String, Facelet> getCache();
}
