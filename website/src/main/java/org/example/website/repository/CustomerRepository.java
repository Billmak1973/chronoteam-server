package org.example.website.repository;

import org.example.website.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByUsername(String username);
    boolean existsByUsername(String username);  // 如果 username 是主键，这个可能不需要
    Optional<Customer> findByPhone(String phone);
}