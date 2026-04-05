package dev.hieunv.price_radar.controller;

import dev.hieunv.price_radar.model.PriceAlert;
import dev.hieunv.price_radar.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @PostMapping
    public PriceAlert create(@RequestBody CreateAlertRequest request) {
        return alertService.createAlert(request.userId(), request.product(), request.threshold());
    }

    @GetMapping("/{userId}")
    public List<PriceAlert> getAlerts(@PathVariable String userId) {
        return alertService.getAlerts(userId);
    }

    @DeleteMapping("/{userId}/{alertId}")
    public Map<String, Boolean> delete(@PathVariable String userId,
                                       @PathVariable String alertId) {
        return Map.of("deleted", alertService.deleteAlert(userId, alertId));
    }
}
