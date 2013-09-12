package org.faceletslite;

public interface Namespace
{
	String getUri();
	CustomTag getCustomTag(String tagName);
	ResourceReader getResourceReader();
}