package org.example.website.service;

import org.example.website.dto.RegisterRequest;
import org.example.website.entity.Customer;
import org.example.website.repository.CustomerRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    // 修改構造函數，加入 RedisTemplate
    public CustomerService(CustomerRepository customerRepository,
                           PasswordEncoder passwordEncoder,
                           RedisTemplate<String, Object> redisTemplate) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Customer register(RegisterRequest request) {
        if (customerRepository.existsById(request.getUsername())) throw new RuntimeException("用戶名已存在");
        if (customerRepository.findByEmail(request.getEmail()).isPresent()) throw new RuntimeException("郵箱已被註冊");
        // 檢查手機號碼是否已被註冊
        if (customerRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new RuntimeException("該手機號碼已被註冊，請使用其他號碼或嘗試登入");
        }

        Customer customer = new Customer();
        customer.setUsername(request.getUsername());
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPassword(passwordEncoder.encode(request.getPassword()));
        customer.setPhone(request.getPhone());
        customer.setCreditLimit(5000.00);

        Customer savedCustomer = customerRepository.save(customer);

        //  註冊成功後，將用戶資訊存入 Redis，設定 1 小時過期
        String redisKey = "user:info:" + savedCustomer.getUsername();
        redisTemplate.opsForValue().set(redisKey, savedCustomer, 1, TimeUnit.HOURS);

        return savedCustomer;
    }

    public Customer findByUsername(String username) {
        //  1. 先從 Redis 快取中查找
        String redisKey = "user:info:" + username;
        Customer cachedCustomer = (Customer) redisTemplate.opsForValue().get(redisKey);

        if (cachedCustomer != null) {
            System.out.println(" 從 Redis 快取中獲取用戶: " + username);
            return cachedCustomer;
        }

        // 2. 快取沒有，再去資料庫查找
        System.out.println(" 從資料庫獲取用戶: " + username);
        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在，請重新登入"));

        //  3. 將資料庫查到的結果放入 Redis，設定 1 小時過期
        redisTemplate.opsForValue().set(redisKey, customer, 1, TimeUnit.HOURS);

        return customer;
    }
}