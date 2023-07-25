package com.github.neshkeev.spring.proxy.simple;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Proxy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LoggerWrapperInvocationHandlerTest.PasswordGeneratorInvocationHandlerTestConfig.class)
public class LoggerWrapperInvocationHandlerTest {
    @Test
    public void test(@Autowired @Qualifier("loggedGenerator") PasswordGenerator passwordGenerator) {
        final var password = passwordGenerator.getPassword();

        assertThat(password, is(not(Matchers.emptyString())));
    }

    @TestConfiguration
    static class PasswordGeneratorInvocationHandlerTestConfig {

        @Bean("generator")
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public PasswordGenerator generator() {
            return (PasswordGenerator) Proxy.newProxyInstance(LoggerWrapperInvocationHandlerTest.class.getClassLoader(),
                    new Class[]{PasswordGenerator.class},
                    new PasswordGeneratorInvocationHandler(32));
        }

        @Bean("loggedGenerator")
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public PasswordGenerator generatorWithLogger() {
            final var generator = generator();
            return (PasswordGenerator) Proxy.newProxyInstance(LoggerWrapperInvocationHandlerTest.class.getClassLoader(),
                    new Class[]{PasswordGenerator.class},
                    new LoggerWrapperInvocationHandler(generator));
        }
    }

}