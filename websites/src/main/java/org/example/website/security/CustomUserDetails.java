package org.example.website.security;

import lombok.Getter;
import org.example.website.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;

/**
 * 自定義 UserDetails，將 User 的 ID 和 Role 一起存入 SecurityContext (Session)
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;           //  核心：用戶 ID
    private final String username;
    private final String password;
    private final User.Role role;    //  附加：用戶角色，方便後續權限判斷
    private final Collection<? extends GrantedAuthority> authorities;

    // 構造函數：直接從你的 User 實體構建
    public CustomUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.role = user.getRole();
        this.authorities = authorities;
    }

    // 以下是 UserDetails 接口必須實現的方法（直接複製即可）
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}