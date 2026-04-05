package dev.hieunv.price_radar.service;

import dev.hieunv.price_radar.model.PriceAlert;
import dev.hieunv.price_radar.model.PriceResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCheckScheduler {

    private final AlertService alertService;
    private final PriceAggregatorService aggregator;
    private final AlertPublisher alertPublisher;   // publishes to Redis Pub/Sub

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = true;

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::checkPrices,
                30, 30, TimeUnit.SECONDS
        );
        log.info("Scheduled price check started");
    }

    private void checkPrices() {
        if (!running) return;
        log.info("Running scheduled price check...");

        List<PriceAlert> activeAlerts = alertService.getAllActiveAlerts();
        for (PriceAlert alert : activeAlerts) {
            try {
                PriceResult cheapest = aggregator.findCheapest(alert.getProduct());
                if (cheapest.getPrice() <= alert.getThreshold()) {
                    log.info("Alert triggered — userId={} product={} price={}",
                            alert.getUserId(), alert.getProduct(), cheapest.getPrice());
                    // Publish to Redis — ALL instances receive this, each checks its own SSE emitters
                    alertPublisher.publish(
                            alert.getUserId(),
                            alert.getProduct(),
                            alert.getAlertId(),
                            alert.getThreshold(),
                            cheapest.getPrice(),
                            cheapest.getSupplierName()
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to check price for alert {}: {}", alert.getAlertId(), e.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Stopping scheduler...");
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
