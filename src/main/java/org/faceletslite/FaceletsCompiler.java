package org.faceletslite;

import java.io.IOException;
import java.io.InputStream;

public interface FaceletsCompiler
{
	Facelet compile(InputStream in) throws IOException;
	Facelet compile(String resourceName) throws IOException;
	Facelet compile(String resourceName, String namespaceUri) throws IOException;
}