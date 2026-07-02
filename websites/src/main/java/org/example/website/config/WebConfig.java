package org.example.website.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 讀取 application.properties 中的 upload.path
    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 將相對路徑轉換為絕對路徑的 URI 格式 (例如: file:/C:/Users/.../project/uploads/)
        String absolutePath = Paths.get(uploadPath).toAbsolutePath().toUri().toString();

        // 核心映射：將 URL 中的 /uploads/** 請求，指向本地硬碟的 uploads 資料夾
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(absolutePath);
    }
}