
package org.example.website.config;

import org.example.website.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**", "/css/**", "/js/**", "/images/**").permitAll()
                        .anyRequest().permitAll()  // 暫時保持這樣
                )
                .userDetailsService(userDetailsService)
                //  關鍵：啟用 Session 管理
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)
                )
                //  關鍵：啟用 SecurityContext 保存
                .securityContext(securityContext -> securityContext
                        .requireExplicitSave(false)
                )
                //  新增：配置登出行為
                .logout(logout -> logout
                        .logoutUrl("/logout")              // 監聽 POST /logout 請求
                        .logoutSuccessUrl("/")             // 登出成功後跳轉到首頁
                        .invalidateHttpSession(true)       // 銷毀當前 Session
                        .clearAuthentication(true)         // 清除安全認證信息
                        .deleteCookies("JSESSIONID")       // 刪除 Session Cookie
                        .permitAll()                       // 允許所有用戶訪問登出接口
                );

        return http.build();
    }
}