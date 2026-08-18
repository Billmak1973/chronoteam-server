package org.example.website.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chronoteamOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ChronoTeam 二手名錶交易平台 API")
                        .description("提供購物車、評論、用戶管理等 RESTful API 文檔")
                        .version("1.0.0"))
                // 配置安全認證 (因為你用的是 Spring Security Session，我們配置 Cookie 認證)
                .addSecurityItem(new SecurityRequirement().addList("SessionAuth"))
                .components(new Components()
                        .addSecuritySchemes("SessionAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID") // Spring Security 默認的 Session Cookie 名稱
                                .description("登入後瀏覽器自動攜帶的 Session ID")));
    }
}