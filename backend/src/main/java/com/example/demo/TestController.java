package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
public class TestController {

    @GetMapping("/api/test")
    public Map<String, String> pingPong() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Kết nối thành công! Lời chào từ Spring Boot.");
        return response;
    }
}
