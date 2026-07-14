package com.onclick.domain.consulting;

import java.lang.reflect.Field;

public final class TestFieldSetter {

    private TestFieldSetter() {
    }

    public static void setField(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not set test field " + fieldName, exception);
            }
        }
        throw new IllegalArgumentException("Test field does not exist: " + fieldName);
    }
}
