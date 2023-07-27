package com.github.neshkeev.spring.proxy.jmx;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class JmxWrapperInvocationHandler implements InvocationHandler {

    private final MBeanInvocable mBeanInvocable;

    private final Object bean;

    public JmxWrapperInvocationHandler(MBeanInvocable mBeanInvocable, Object bean) {
        this.mBeanInvocable = mBeanInvocable;
        this.bean = bean;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "getAttribute", "setAttribute", "getAttributes", "setAttributes" -> null;
            case "getMBeanInfo" ->
                    new MBeanInfo(bean.getClass().getName(),
                            bean.getClass().getName(),
                            new MBeanAttributeInfo[0],
                            new MBeanConstructorInfo[0],
                            MBeanUtils.operations(bean.getClass()),
                            new MBeanNotificationInfo[0]);
            case "invoke" -> invoke(args);
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private Object invoke(Object[] args) throws Exception {
        final var actionName = (String) args[0];
        final var params = (Object[]) args[1];

        return mBeanInvocable.getResult(bean, actionName, params);
    }
}
