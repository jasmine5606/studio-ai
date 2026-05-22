package com.jasmine.studioai.bilibili;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bilibili/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BilibiliAuthController {

    private final BilibiliQrLoginService qrLoginService;

    @GetMapping("/qrcode")
    public BilibiliQrLoginService.GenerateResult generate(@RequestParam String bindId) {
        return qrLoginService.generate(bindId);
    }

    @GetMapping("/poll")
    public BilibiliQrLoginService.PollResult poll(@RequestParam String bindId) {
        return qrLoginService.poll(bindId);
    }

    @GetMapping("/status")
    public Status status(@RequestParam String bindId) {
        boolean cookieReady = qrLoginService.hasCookie(bindId);
        return new Status(cookieReady);
    }

    public record Status(boolean bound) {
    }
}

