package com.github.neshkeev.spring.proxy.simple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Proxy;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = HelloWorldInvocationHandlerTest.HelloWorldInvocationHandlerTestConfig.class)
public class HelloWorldInvocationHandlerTest {

    @Test
    public void test(@Autowired CharSequence value) {
        assertThat(value, is(equalTo(HelloWorldInvocationHandler.HELLO_WORLD_MESSAGE)));
    }

    @TestConfiguration
    static class HelloWorldInvocationHandlerTestConfig {
        @Bean
        public CharSequence text() {
            return (CharSequence) Proxy.newProxyInstance(HelloWorldInvocationHandlerTest.class.getClassLoader(),
                    new Class[]{CharSequence.class},
                    new HelloWorldInvocationHandler());
        }
    }

}