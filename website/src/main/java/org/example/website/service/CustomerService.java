
package org.example.website.service;

import org.example.website.dto.RegisterRequest;
import org.example.website.entity.Customer;
import org.example.website.repository.CustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    // 🟢 1. 去掉 static
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Customer register(RegisterRequest request) {
        if (customerRepository.existsById(request.getUsername())) throw new RuntimeException("用戶名已存在");
        if (customerRepository.findByEmail(request.getEmail()).isPresent()) throw new RuntimeException("郵箱已被註冊");

        Customer customer = new Customer();
        customer.setUsername(request.getUsername());
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPassword(passwordEncoder.encode(request.getPassword()));
        customer.setPhone(request.getPhone());
        customer.setCreditLimit(5000.00);
        return customerRepository.save(customer);
    }

    // 🟢 2. 去掉 static，改為實例方法
    public Customer findByUsername(String username) {
        return customerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在，請重新登入"));
    }
}