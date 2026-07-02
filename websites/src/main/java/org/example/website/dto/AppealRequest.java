package org.example.website.dto;

import lombok.Data;

@Data
public class AppealRequest {
    private Long notificationId;
    private String reason;
    private String appealType; // "BAN", "BLACKLIST", "DELETE_REVIEW"
}