package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.User;
import org.example.website.repository.UserRepository; // 🟢 確保你已經創建了 UserRepository
import org.example.website.security.CustomUserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // 替換掉原來的 CustomerRepository
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用戶不存在"));

        //  根據 User 實體中的 Role 枚舉構建權限 (例如: ROLE_ADMIN, ROLE_CUSTOMER)
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        // 核心修改：返回自定義的 CustomUserDetails，把 ID 和 Role 塞進去！
        return new CustomUserDetails(
                user,
                Collections.singletonList(authority)
        );
    }
}