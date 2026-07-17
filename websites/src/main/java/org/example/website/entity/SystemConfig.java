package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "system_config")
@Data
public class SystemConfig {
    @Id
    @Column(name = "config_key", length = 50)
    private String configKey; // 例如: "SHIPPING_FEE" 或 "FREE_SHIPPING_THRESHOLD"

    @Column(name = "config_value", length = 255, nullable = false)
    private String configValue; // 例如: "50" 或 "30000"
}