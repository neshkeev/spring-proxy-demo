package com.github.neshkeev.spring.proxy.jmx;

import javax.management.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

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
                return new MBeanInfo(
                        bean.getClass().getName(),
                        bean.getClass().getName(),
                        new MBeanAttributeInfo[0],
                        new MBeanConstructorInfo[0],
                        MBeanUtils.operations(bean.getClass()),
                        new MBeanNotificationInfo[0]
                );
            }
            case "invoke" -> {
                return invoke(args);
            }
        }

        throw new UnsupportedOperationException(method.getName());
    }

    private Object invoke(Object[] args) throws Exception {
        final var actionName = (String) args[0];
        final var params = (Object[]) args[1];

        return new MBeanInvocable(this.bean, actionName, params).getResult();
    }
}
