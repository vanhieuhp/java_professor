package dev.hieunv.grpcclient.controller;

import dev.hieunv.grpcclient.service.StringsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping(("/strings"))
public class StringsController {

    private final StringsService stringsService;

    @GetMapping("/uppercase/{lowercase}")
    public ResponseEntity<String> upperCase(@PathVariable String lowercase) {
        return ResponseEntity.ok(stringsService.getUpperCase(lowercase));
    }
}
