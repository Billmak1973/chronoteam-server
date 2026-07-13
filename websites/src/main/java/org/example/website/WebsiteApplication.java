package org.example.website;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling //  啟用定時任務
public class WebsiteApplication {
    //cd frontend
    //PS C:\Users\User\IdeaProjects\chronoteam-server\website\frontend>
    public static void main(String[] args) {
        SpringApplication.run(WebsiteApplication.class, args);
    }
}