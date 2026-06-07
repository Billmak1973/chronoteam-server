package org.example.website.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse {
    private boolean success;
    private String message;
    private Object data;

    public static ApiResponse ok(String message) {
        return new ApiResponse(true, message, null);
    }
    public static ApiResponse error(String message) {
        return new ApiResponse(false, message, null);
    }

    public static ApiResponse okWithData(String message, Object data) {
        return new ApiResponse(true, message, data);
    }
}