package com.github.neshkeev.spring.proxy.jmx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;

final class MBeanInvocable {
    private final Object target;
    private final Method method;
    private final Object[] args;

    public MBeanInvocable(Object target, String methodName, Object[] params) throws NoSuchMethodException, JsonProcessingException {
        this.target = target;
        this.args = new Object[params.length];
        this.method = prepareMethod(target, methodName, params);
    }

    private Method prepareMethod(Object target, String methodName, Object[] params) throws NoSuchMethodException, JsonProcessingException {
        final var candidates = getCandidateMethods(target, methodName);

        for (Method candidate : candidates) {
            if (candidate.getParameterCount() != params.length) continue;

            int i = 0;
            for (; i < candidate.getParameterCount(); i++) {
                final Object jmxParam = params[i];
                final var methodParam = candidate.getParameters()[i];

                if (jmxParam != null) {
                    args[i] = tryConvert(jmxParam, methodParam.getType());
                    if (args[i] == null) break;
                }
                else {
                    args[i] = null;
                }
            }

            if (i == candidate.getParameterCount()) {
                return candidate;
            }
        }
        throw new NoSuchMethodException("No method matches the required signature");
    }

    private static Object tryConvert(Object jmxParam, Class<?> paramType) throws JsonProcessingException {
        final var objectMapper = new ObjectMapper();

        final var primitiveJmxType = MethodType.methodType(jmxParam.getClass()).unwrap().returnType();

        if (jmxParam.getClass() != paramType && primitiveJmxType != paramType) {
            if (!jmxParam.getClass().equals(String.class)) {
                return Optional.empty();
            }

            return objectMapper.readValue(jmxParam.toString(), paramType);
        }
        return jmxParam;
    }

    private List<Method> getCandidateMethods(Object target, String actionName) throws NoSuchMethodException {
        final var candidates = new ArrayList<Method>();
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(actionName)) {
                candidates.add(method);
            }
        }

        if (candidates.isEmpty()) {
            throw new NoSuchMethodException("The " + actionName + " method doesn't exist in " + target.getClass());
        }
        return candidates;
    }

    public Object getResult() throws ReflectiveOperationException, JsonProcessingException {
        return augmentResult(method.invoke(target, args));
    }

    private Object augmentResult(final Object result) throws JsonProcessingException {
        if (result == null) {
            return null;
        }

        if (result.getClass().isPrimitive()) {
            return result;
        }

        if (result.getClass().getPackageName().equals("java.lang")) {
            return result;
        }

        if (result instanceof Collection<?>) {
            return collectObjects(((Collection<?>) result));
        }

        if (result instanceof Map<?, ?>) {
            return collectObjects(((Map<?, ?>) result).entrySet());
        }

        return new ObjectMapper().writeValueAsString(result);
    }

    private static <T> List<String> collectObjects(Collection<T> collection) throws JsonProcessingException {
        var result = new ArrayList<String>(collection.size());
        final var objectMapper = new ObjectMapper();
        for (T element : collection) {
            result.add(objectMapper.writeValueAsString(element));
        }
        return result;
    }
}
