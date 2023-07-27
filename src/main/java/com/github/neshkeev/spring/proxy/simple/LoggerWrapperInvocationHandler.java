package com.github.neshkeev.spring.proxy.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;

public class LoggerWrapperInvocationHandler<T> implements InvocationHandler {
    private final static Logger LOG = LoggerFactory.getLogger(LoggerWrapperInvocationHandler.class);

    private final T delegate;

    public LoggerWrapperInvocationHandler(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        LOG.info("Start executing {}", method.getName());
        final var start = LocalDateTime.now();
        try {
            return method.invoke(delegate, args);
        }
        finally {
            final var end = LocalDateTime.now();
            LOG.info("End executing {} which took {}ns", method.getName(), Duration.between(start, end).toNanos());
        }
    }
}
