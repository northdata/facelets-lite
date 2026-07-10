package org.faceletslite.imp;

import java.util.List;

class Is {

    static boolean conditionTrue(Object object) {
        if (object == null) {
            return false;
        }
        if (object instanceof Boolean) {
            return (Boolean) object;
        }
        if (object instanceof String) {
            return !((String) object).trim().equalsIgnoreCase("false");
        }
        return true;
    }

    static boolean empty(Object object) {
        if (object == null) {
            return true;
        }
        if (object instanceof String) {
            return ((String) object).length() == 0;
        }
        if (object instanceof List<?>) {
            return ((List<?>) object).size() == 0;
        }
        return false;
    }

    static boolean notEmpty(Object object) {
        return !empty(object);
    }
}
