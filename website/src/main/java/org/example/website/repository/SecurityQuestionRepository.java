package org.example.website.repository;

import org.example.website.entity.SecurityQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SecurityQuestionRepository extends JpaRepository<SecurityQuestion, Long> {
    // 查詢某個用戶的所有安全問題
    List<SecurityQuestion> findByCustomer_UsernameOrderByCreatedAtDesc(String username);
}