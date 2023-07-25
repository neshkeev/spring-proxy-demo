package com.github.neshkeev.spring.proxy.jmx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.management.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class JmxWrapperInvocationHandler implements InvocationHandler {
    private final Object bean;

    public JmxWrapperInvocationHandler(Object bean) {
        this.bean = bean;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "getAttribute", "setAttribute", "getAttributes", "setAttributes" -> {
                return null;
            }
            case "getMBeanInfo" -> {
                return getMBeanInfo();
            }
            case "invoke" -> {
                return invoke(args);
            }
        }

        throw new UnsupportedOperationException(method.getName());
    }

    private MBeanInfo getMBeanInfo() {
        final MBeanOperationInfo[] operations = Arrays.stream(bean.getClass().getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(JmxWrapperInvocationHandler::getmBeanOperationInfo)
                .toArray(MBeanOperationInfo[]::new);

        final var constructors = new MBeanConstructorInfo[0];
        return new MBeanInfo(this.getClass().getName(),
                "",
                new MBeanAttributeInfo[0],
                constructors,
                operations,
                new MBeanNotificationInfo[0]
        );
    }

    private static MBeanOperationInfo getmBeanOperationInfo(Method m) {
        final var params = Arrays.stream(m.getParameters())
                .map(p-> new MBeanParameterInfo(p.getName(), getPrimitiveTypeOrString(p.getType()), p.getName()))
                .toArray(MBeanParameterInfo[]::new);

        return new MBeanOperationInfo(m.getName(),
                m.getName(),
                params,
                getPrimitiveTypeOrString(m.getReturnType()),
                MBeanOperationInfo.UNKNOWN);
    }

    private static String getPrimitiveTypeOrString(Class<?> type) {
        if (type.isPrimitive()) {
            return type.getTypeName();
        }
        if ("java.lang".equals(type.getPackageName())) {
            return type.getTypeName();
        }

        return String.class.getName();
    }

    private Object invoke(Object[] args) throws Exception {
        final var actionName = (String) args[0];
        final var params = (Object[]) args[1];

        final var candidates = new ArrayList<Method>();
        for (Method method : bean.getClass().getMethods()) {
            if (method.getName().equals(actionName)) {
                candidates.add(method);
            }
        }

        if (candidates.isEmpty()) {
            throw new NoSuchMethodException("The " + actionName + " method doesn't exist in " + bean.getClass());
        }

        List<Object> callArgs = new ArrayList<>(params.length);

        final var objectMapper = new ObjectMapper();
        final Method declaredMethod = getSuitableMethod(params, candidates, callArgs, objectMapper);

        if (declaredMethod == null) {
            throw new NoSuchMethodException(
                    "No methods %s can be invoked in %s because no method with a matching signature found"
                            .formatted(actionName, bean.getClass())
            );
        }

        final var result = declaredMethod.invoke(bean, callArgs.toArray());

        return augmentResult(result);
    }

    private static Method getSuitableMethod(Object[] params, ArrayList<Method> candidates, List<Object> callArgs, ObjectMapper objectMapper) throws JsonProcessingException {
        for (Method candidate : candidates) {
            if (candidate.getParameterCount() != params.length) continue;

            boolean match = true;

            for (int i = 0; i < candidate.getParameterCount(); i++) {
                final Object jmxParam = params[i];
                final Parameter classParam = candidate.getParameters()[i];

                var value = jmxParam;
                if (!jmxParam.getClass().equals(classParam.getType())) {
                    if (!jmxParam.getClass().equals(String.class)) {
                        match = false;
                        break;
                    }

                    value = objectMapper.readValue(jmxParam.toString(), classParam.getType());
                }
                callArgs.add(value);
            }

            if (match) return candidate;

            callArgs.clear();
        }

        return null;
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

    private static<T> List<String> collectObjects(Collection<T> collection) {
        final var objectMapper = new ObjectMapper();
        return collection.stream()
                .map(e -> {
                    try {
                        return objectMapper.writeValueAsString(e);
                    }
                    catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .collect(Collectors.toList());
    }
}
