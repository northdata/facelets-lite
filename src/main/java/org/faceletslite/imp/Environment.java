package org.faceletslite.imp;

public interface Environment {

    String VarPrefix = "__facelet__";
    String ResourceName = VarPrefix + "resourceName";
    String ResourcePath = VarPrefix + "resourcePath";
    String Namespace = VarPrefix + "namespace";
    String SourceText = VarPrefix + "sourceText";
}
