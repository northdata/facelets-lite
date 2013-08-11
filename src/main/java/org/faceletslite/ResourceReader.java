package org.faceletslite;

import java.io.IOException;
import java.io.InputStream;

public interface ResourceReader
{
	InputStream read(String resourceName) throws IOException;
}