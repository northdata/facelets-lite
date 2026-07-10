package org.faceletslite.imp;

import java.util.Map;

import org.faceletslite.imp.FaceletsCompilerImp.MutableContext;
import org.jdom2.Element;

class SourceFragment {

    private final Element root;
    private final MutableContext context;
    private final Map<String, SourceFragment> defines;

    public SourceFragment(Element root, MutableContext context, Map<String, SourceFragment> defines) {
        this.root = root;
        this.context = context;
        this.defines = defines;
    }

    public Element getRoot() {
        return root;
    }

    public MutableContext getContext() {
        return context;
    }

    public Map<String, SourceFragment> getDefinitions() {
        return defines;
    }

    @Override
    public String toString() {
        return root.toString();
    }
}
