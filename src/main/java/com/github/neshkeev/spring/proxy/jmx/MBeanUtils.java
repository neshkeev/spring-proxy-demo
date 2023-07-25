package com.github.neshkeev.spring.proxy.jmx;

import javax.management.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class MBeanUtils {

    public static MBeanOperationInfo[] operations(final Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(MBeanUtils::getmBeanOperationInfo)
                .toArray(MBeanOperationInfo[]::new);
    }

    private static MBeanOperationInfo getmBeanOperationInfo(final Method m) {
        final var params = Arrays.stream(m.getParameters())
                .map(p -> new MBeanParameterInfo(p.getName(), getPrimitiveTypeOrString(p.getType()), p.getName()))
                .toArray(MBeanParameterInfo[]::new);

        return new MBeanOperationInfo(m.getName(),
                m.getName(),
                params,
                getPrimitiveTypeOrString(m.getReturnType()),
                MBeanOperationInfo.UNKNOWN);
    }

    private static String getPrimitiveTypeOrString(final Class<?> type) {
        if (type.isPrimitive()) {
            return type.getTypeName();
        }

        if (String.class.getPackage() == type.getPackage()) {
            return type.getTypeName();
        }

        return String.class.getName();
    }
}
