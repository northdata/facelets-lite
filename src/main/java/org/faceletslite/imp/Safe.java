package org.faceletslite.imp;

import java.util.Arrays;
import java.util.List;

class Safe {

    public static boolean equals(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }
        Class<?> class1 = obj1.getClass();
        Class<?> class2 = obj2.getClass();
        if (class1.isArray() && class2.isArray()) {
            Object[] array1 = (Object[]) obj1;
            Object[] array2 = (Object[]) obj2;
            return Arrays.deepEquals(array1, array2);
        }
        return obj1.equals(obj2);
    }

    public static <T> T get(List<T> list, int index) {
        return get(list, index, null);
    }

    public static <T> T get(List<T> list, int index, T _default) {
        return list != null && 0 <= index && index < list.size() ? list.get(index) : _default;
    }

    public static int toInt(Object object, int _default) {
        if (object != null) {
            if (object instanceof Number) {
                return ((Number) object).intValue();
            }
            if (object instanceof CharSequence) {
                try {
                    return Integer.parseInt(object.toString());
                } catch (NumberFormatException exc) {
                }
            }
        }
        return _default;
    }
}
