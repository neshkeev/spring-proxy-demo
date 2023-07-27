[![Gitpod](https://img.shields.io/badge/Open%20in%20Gitpod-908a85?logo=gitpod)](https://gitpod.io/#https://github.com/neshkeev/spring-proxy-demo)

# Overview

The repository reveals the "magic" behind Spring Framework. As a demo it automatically configures JMX MBeans for spring beans that are annotated with `@JmxExporter`.

**IMPORTANT**: the application is built on top of Spring Boot 3.1.2, which supports only Java 17, so `JAVA_HOME` should point at a JDK 17 distribution.

## Quick start

0. Set `JAVA_HOME` to point at JDK17;
1. Clone the repository:
```bash
git clone https://github.com/neshkeev/spring-proxy-demo && cd spring-proxy-demo
```
2. Start the application:
```bash
./mvnw spring-boot:run
```
3. Add a consumer:
```bash
curl http://localhost:8080/customers \
    -X POST \
    -H "Content-type: application/json" \
    -d '{"id":0,"name":"Hello, World!", "active": true}'
```
3. Start `jconsole`:
```bash
jconsole
```
4. Find the `com.github.neshkeev.spring.proxy.Main`	process and connect to it;
5. Go to `MBeans`;
6. In the left panel unfold `com.github.neshkeev.spring.proxy.rest` | `basic` | `customerController` | `Operations` | `get`;
7. Click on the `get` button.

## Run tests with maven

The repository contains tests that can be executed with maven:
- Run all tests:
```bash
./mvnw clean test
```
- Run a specific test:
```bash
./mvnw clean test -Dtest=com.github.neshkeev.spring.proxy.rest.CustomerControllerJMXTest
```

## Run the application with Intellij IDEA

If you open the repository in Intellij IDEA you can run the application and it's tests with predefined run configurations:

1. `Main` starts the Spring Boot application with a rest controller and registered MBeans;
1. `HelloWorldInvocationHandlerTest` starts a Spring Boot test that demonstrates the simplest proxy;
1. `PasswordGeneratorInvocationHandlerTest` starts a Spring Boot test for password generators;
1. `LoggerWrapperInvocationHandlerTest`
1. `CustomerControllerJMXTest` starts Spring Boot tests that checks registered JMX MBeans;
