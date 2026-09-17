package org.example.website.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration//標記一個類為「配置類」。它告訴 Spring Boot：「這個類裡面包含了一些配置資訊，請在啟動時掃描並解析它」。(控制反轉 (IoC)
public class OpenApiConfig {

    @Bean//用在方法上（通常在 @Configuration 類中），手動將該方法的返回值註冊為 Spring Bean。常用於引入第三方庫的類（因為第三方類無 @Component）。
    //@Bean = 聲明這個方法會生產一個物件，並交給 Spring 統一管理。(依賴注入 (DI))
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