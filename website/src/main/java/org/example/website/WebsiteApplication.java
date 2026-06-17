package org.example.website;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling //  啟用定時任務
public class WebsiteApplication {
    //PS C:\Users\User\IdeaProjects\chronoteam-server\website\frontend> npm run build
    public static void main(String[] args) {
        SpringApplication.run(WebsiteApplication.class, args);
    }
}