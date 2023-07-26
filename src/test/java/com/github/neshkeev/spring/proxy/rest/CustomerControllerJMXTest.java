package com.github.neshkeev.spring.proxy.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public class CustomerControllerJMXTest {
    private static JMXConnector jmcConnector;
    private static MBeanServerConnection mbc;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mvc;

    @Test
    public void testList() throws Exception {
        final var name = new ObjectName(getCustomerControllerMBeanName());
        final var result = mbc.invoke(name, "list", new Object[0], new String[0]);
        //noinspection unchecked
        final var results = (List<String>) result;

        final List<Customer> customers = results.stream()
                .map(this::readCustomer)
                .toList();

        assertThat(customers, hasItem(getCustomer()));
    }

    @Test
    public void testGet() throws Exception {
        final var name = new ObjectName(getCustomerControllerMBeanName());
        final var result = mbc.invoke(name, "get", new Object[]{1}, new String[0]);
        final var customer = readCustomer((String) result);

        assertThat(customer, is(equalTo(getCustomer())));
    }

    @Test
    public void testAdd() throws Exception {
        final var name = new ObjectName(getCustomerControllerMBeanName());
        var customer = new Customer(2, "Jane Doe", false);

        mbc.invoke(name, "add", new Object[]{objectMapper.writeValueAsString(customer)}, new String[0]);

        mvc.perform(
                        get("/customers/" + customer.id())
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(customer.id())))
                .andExpect(jsonPath("$.name", is(customer.name())))
                .andExpect(jsonPath("$.active", is(customer.active())));

    }

    @BeforeAll
    public static void beforeAll() throws IOException, AttachNotSupportedException {
        final long pid = ProcessHandle.current().pid();
        final VirtualMachine vm = VirtualMachine.attach(Long.toString(pid));
        final String jmxUrl = vm.startLocalManagementAgent();
        final JMXServiceURL url = new JMXServiceURL(jmxUrl);

        jmcConnector = JMXConnectorFactory.connect(url, null);
        mbc = jmcConnector.getMBeanServerConnection();
    }

    @AfterAll
    public static void afterAll() throws IOException {
        jmcConnector.close();
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        final Customer customer = getCustomer();

        mvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().is2xxSuccessful());
    }

    private static Customer getCustomer() {
        return new Customer(1, "John Snow", true);
    }

    private String getCustomerControllerMBeanName() {
        final String packageName = CustomerController.class.getPackageName();
        final String className = CustomerController.class.getSimpleName();
        final String beanName = Character.toLowerCase(className.charAt(0)) + className.substring(1);

        return packageName + ":type=basic,name=" + beanName;
    }

    @TestConfiguration
    static class TestConfig {
    }

    private Customer readCustomer(String result) {
        try {
            return objectMapper.readValue(result, Customer.class);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}