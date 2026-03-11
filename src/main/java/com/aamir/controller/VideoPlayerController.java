package com.aamir.controller;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class VideoPlayerController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/player")
    public String player(@RequestParam(required = false) String hls,
                         @RequestParam(required = false) String file,
                         Model model) {
        model.addAttribute("hlsUrl", hls);
        model.addAttribute("fileName", file);
        return "player";
    }
}
