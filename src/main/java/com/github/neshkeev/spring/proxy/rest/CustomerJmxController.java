package com.github.neshkeev.spring.proxy.rest;

import com.github.neshkeev.spring.proxy.jmx.JmxExporter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
@Component
@JmxExporter
public class CustomerJmxController {
    private final Map<Integer, Customer> customers;

    public CustomerJmxController() {
        this.customers = new HashMap<>();
    }

    public void addObject(Customer customer) {
        customers.put(customer.id(), customer);
    }

    public void add(Integer id, String name) {
        customers.put(id, new Customer(id, name, true));
    }

    public String get(Integer id) {
        return customers.get(id).toString();
    }

    public Customer get1(Integer id) {
        return customers.get(id);
    }

    public Map<Integer, Customer> map() {
        return customers;
    }

    public Collection<Customer> list() {
        return customers.values();
    }
}
