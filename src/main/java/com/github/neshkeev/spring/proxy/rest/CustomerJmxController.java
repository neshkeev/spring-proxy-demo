package com.github.neshkeev.spring.proxy.rest;

import com.github.neshkeev.spring.proxy.jmx.JmxExporter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Component
@JmxExporter
public class CustomerJmxController {
    private final Map<Integer, Customer> customers;

    public CustomerJmxController() {
        this.customers = new HashMap<>();
    }

    public void add(Integer id, String name) {
        customers.put(id, new Customer(id, name, true));
    }

    public String get(Integer id) {
        return customers.get(id).toString();
    }

    public Collection<String> list() {
        return customers.values().stream().map(Customer::toString).collect(Collectors.toList());
    }
}
