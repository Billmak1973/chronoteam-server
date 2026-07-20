package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;


@Entity
@Table(name = "site_settings")
@Data
public class SiteSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", unique = true, nullable = false)
    private String key;

    @Column(name = "setting_value")
    private String value;

    private String description;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and Setters
    //INSERT IGNORE INTO site_settings (setting_key, setting_value, description)
    //VALUES ('card_border_theme', 'day', '產品卡片邊框主題: day(白天) 或 night(晚上)');
}
