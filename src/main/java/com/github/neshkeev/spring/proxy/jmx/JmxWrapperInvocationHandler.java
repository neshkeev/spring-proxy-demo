package com.github.neshkeev.spring.proxy.jmx;

import javax.management.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

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
                .map(m -> new MBeanOperationInfo(m.getName(), m))
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

    private Object invoke(Object[] args) throws Exception {
        final var actionName = (String) args[0];
        final var params = (Object[]) args[1];

        final Class<?>[] paramTypes = Arrays.stream(params)
                .map(Object::getClass)
                .toArray(Class<?>[]::new);

        final var declaredMethod = bean.getClass().getDeclaredMethod(actionName, paramTypes);
        return declaredMethod.invoke(bean, params);
    }
}
