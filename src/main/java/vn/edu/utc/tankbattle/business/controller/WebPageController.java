package vn.edu.utc.tankbattle.business.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Trả vỏ Thymeleaf dùng chung; dữ liệu cá nhân chỉ được tải qua API đã xác thực. */
@Controller
public class WebPageController {
    /** Vỏ trang không chứa danh tính hoặc CSRF nên có thể dùng làm trang dự phòng offline. */
    @GetMapping({
        "/", "/login", "/register", "/rooms", "/rooms/{code}", "/play/{id}",
        "/leaderboard", "/history", "/profile", "/saves", "/help", "/offline"
    })
    public String application() {
        return "app";
    }
}
