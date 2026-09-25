package com.smartqueue.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        // Serve the static SPA at the root path for browser navigation
        return "forward:/index.html";
    }
}