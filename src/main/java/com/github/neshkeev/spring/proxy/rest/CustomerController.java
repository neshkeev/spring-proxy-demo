package com.github.neshkeev.spring.proxy.rest;

import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@RestController
public class CustomerController {
    private final Map<Integer, Customer> customers;

    public CustomerController() {
        this.customers = new HashMap<>();
    }

    @PostMapping("/customers")
    public void add(@RequestBody Customer customer) {
        customers.put(customer.id(), customer);
    }

    @GetMapping("/customers/{id}")
    public Customer get(@PathVariable("id") int id) {
        return customers.get(id);
    }

    @GetMapping("/customers")
    public Collection<Customer> list() {
        return customers.values();
    }
}
