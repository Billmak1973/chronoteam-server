package org.example.website.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")  //加上这个！所有 API 路径都会带 /api 前缀
public class TestController {

    @GetMapping("/")
    public String home() {
        return " Spring Boot 运行成功！";
    }

    @GetMapping("/test")
    public String test() {
        return " 测试接口正常工作！";
    }
}