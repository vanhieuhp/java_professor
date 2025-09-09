package dev.hieunv.controller;

import dev.hieunv.service.ExternalApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/timeout")
public class TimeOutController {

    private final ExternalApiService externalApiService;

    @GetMapping
    public String callSlowApi() throws Exception {
        return externalApiService.callSlowApi();
    }
}
