package org.example.website.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result { // 👈 改名為 Result
    private boolean success;
    private String message;
    private Object data;

    public static Result ok(String message) {
        return new Result(true, message, null);
    }
    public static Result error(String message) {
        return new Result(false, message, null);
    }
    public static Result okWithData(String message, Object data) {
        return new Result(true, message, data);
    }
}