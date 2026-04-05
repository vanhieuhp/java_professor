package dev.hieunv.price_radar.controller;

public record CreateAlertRequest(
        String userId,
        String product,
        double threshold
) {
}
