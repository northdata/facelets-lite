package org.faceletslite.imp;

import java.util.Set;

public interface Namespaces {

    String Ui = "http://java.sun.com/jsf/facelets";
    String Core = "http://java.sun.com/jstl/core";
    String JspCore = "http://java.sun.com/jsp/jstl/core";
    String JsfH = "http://java.sun.com/jsf/html";
    String Xhtml = "http://www.w3.org/1999/xhtml";
    String None = "";

    Set<String> CoreEquivalent = Const.setOf(Core, JspCore);
    Set<String> UiEquivalent = Const.setOf(Ui);
}
