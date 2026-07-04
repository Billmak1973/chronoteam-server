package org.example.website.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用戶名不能為空")
    @Size(min = 3, max = 50, message = "用戶名長度3-50位")
    private String username;

    @NotBlank(message = "姓名不能為空")
    @Size(max = 100, message = "姓名不能超過100字符")
    private String name;

    @NotBlank(message = "郵箱不能為空")
    @Email(message = "郵箱格式不正確")
    private String email;

    @NotBlank(message = "密碼不能為空")
    @Size(min = 6, message = "密碼至少6位")
    private String password;

    @NotBlank(message = "手機號不能為空")
    private String phone;

    private String address;

}