package org.example.website.dto;

import lombok.Data;

@Data
public class AddressUpdateRequest {
    private String receiverName;
    private String contactPhone;
    private String fullAddress;
    private Integer ranking;
}