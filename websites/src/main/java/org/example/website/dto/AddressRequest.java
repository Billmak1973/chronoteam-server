package org.example.website.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AddressRequest {
    private String receiverName;
    private String contactPhone;
    private String fullAddress;

    // 【核心修復】：強制指定 JSON 映射名稱，讓 Jackson 能正確讀取 "isDefault"
    @JsonProperty("isDefault")
    private boolean isDefault;
}