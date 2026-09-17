package org.example.website.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 對應理論中的「統一返回格式」
//Response：業界常見統一返回格式，包含三個核心字段：
//code / status：業務狀態碼（如 200 表示成功，4001 表示參數錯誤）。
//message / msg：提示信息（如 "Success", "User not found"）。
//data：實際返回的業務數據（Object, Array 或 null）
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    // 理論中的 code/status，這裡被簡化為了布爾值 success (true代表200成功，false代表400/500失敗)
    private boolean success;
    // 對應理論中的 message / msg
    private String message;
    // 對應理論中的 data
    private Object data;

    // 封裝了常用的快捷構造方法
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