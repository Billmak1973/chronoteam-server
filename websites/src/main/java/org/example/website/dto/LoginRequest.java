package org.example.website.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "用戶名不能為空")
    private String username;
    @NotBlank(message = "密碼不能為空")
    private String password;
}