package org.faceletslite.imp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.el.ArrayELResolver;
import jakarta.el.BeanELResolver;
import jakarta.el.CompositeELResolver;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.el.FunctionMapper;
import jakarta.el.ListELResolver;
import jakarta.el.MapELResolver;
import jakarta.el.RecordELResolver;

import org.faceletslite.Configuration;
import org.faceletslite.CustomTag;
import org.faceletslite.Facelet;
import org.faceletslite.Namespace;
import org.faceletslite.ResourceReader;

public class DefaultConfiguration implements Configuration {

    private final List<Namespace> namespaces = new ArrayList<>();
    private final Map<String, List<Class<?>>> classesByPrefix = new HashMap<>();

    @Override
    public ExpressionFactory getExpressionFactory() {
        return ExpressionFactory.newInstance();
    }

    @Override
    public ELResolver getELResolver() {
        CompositeELResolver result = new CompositeELResolver();
        result.add(new RecordELResolver());
        result.add(new MapELResolver());
        result.add(new ArrayELResolver());
        result.add(new ListELResolver());
        result.add(new BeanELResolver());
        return result;
    }

    @Override
    public ResourceReader getResourceReader() {
        return new FileResourceReader("", ".html");
    }

    @Override
    public List<Namespace> getCustomNamespaces() {
        return namespaces;
    }

    public void addCustomNamespace(String uri, ResourceReader resourceReader) {
        addCustomNamespace(uri, resourceReader, Collections.<String, CustomTag>emptyMap());
    }

    public void addCustomNamespace(
        final String uri,
        final ResourceReader resourceReader,
        final Map<String, CustomTag> customTags) {
        namespaces.add(
            new Namespace() {

                @Override
                public String getUri() {
                    return uri;
                }

                @Override
                public ResourceReader getResourceReader() {
                    return resourceReader;
                }

                @Override
                public CustomTag getCustomTag(String tagName) {
                    return customTags.get(tagName);
                }
            });
    }

    @Override
    public FunctionMapper getFunctionMapper() {
        return new FunctionMapperImp(classesByPrefix);
    }

    public void addFunctions(String prefix, Class<?> clazz) {
        List<Class<?>> classes = classesByPrefix.get(prefix);
        if (classes == null) {
            classes = new ArrayList<>();
            classesByPrefix.put(prefix, classes);
        }
        classes.add(clazz);
    }

    @Override
    public Map<String, Facelet> getCache() {
        return new ConcurrentHashMap<>();
    }
}
