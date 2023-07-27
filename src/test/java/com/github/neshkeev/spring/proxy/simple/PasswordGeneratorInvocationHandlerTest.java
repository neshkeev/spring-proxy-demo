package com.github.neshkeev.spring.proxy.simple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Proxy;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PasswordGeneratorInvocationHandlerTest.PasswordGeneratorInvocationHandlerTestConfig.class)
public class PasswordGeneratorInvocationHandlerTest {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordGeneratorInvocationHandlerTest.class);

    @Test
    public void test(@Autowired PasswordGenerator passwordGenerator1, @Autowired PasswordGenerator passwordGenerator2) {
        final var password1 = passwordGenerator1.getPassword();
        final var password2 = passwordGenerator2.getPassword();

        LOG.info("The first password: {}", password1);
        LOG.info("The second password: {}", password2);

        assertThat(password1, is(not(equalTo(password2))));
        assertAll(
                () -> assertThat(password1, is(equalTo(passwordGenerator1.getPassword()))),
                () -> assertThat(password1, is(not(equalTo(password2)))),
                () -> assertThat(password2, is(equalTo(passwordGenerator2.getPassword())))
        );
    }

    @TestConfiguration
    static class PasswordGeneratorInvocationHandlerTestConfig {

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public PasswordGenerator generator() {
            return (PasswordGenerator) Proxy.newProxyInstance(PasswordGeneratorInvocationHandlerTest.class.getClassLoader(),
                    new Class[]{PasswordGenerator.class},
                    new PasswordGeneratorInvocationHandler(32));
        }
    }

}