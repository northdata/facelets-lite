package org.faceletslite.imp;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

class Const {

    @SafeVarargs
    public static <T> Set<T> setOf(T... objects) {
        Set<T> result = new LinkedHashSet<T>(objects.length);
        for (T object: objects) {
            result.add(object);
        }
        return Collections.unmodifiableSet(result);
    }
}
