package dev.hieunv.price_radar.service;

import dev.hieunv.price_radar.model.PriceAlert;
import dev.hieunv.price_radar.repository.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertServiceImpl implements AlertService {

    private final PriceAlertRepository repo;

    @Override
    public PriceAlert createAlert(String userId, String product, double threshold) {
        PriceAlert alert = PriceAlert.builder()
                .userId(userId)
                .product(product)
                .threshold(threshold)
                .build();
        // alertId and createdAt set by @PrePersist
        return repo.save(alert);
    }

    @Override
    public boolean deleteAlert(String userId, String alertId) {
        return repo.findById(alertId)
                .filter(a -> a.getUserId().equals(userId))
                .map(a -> {
                    a.setActive(false);
                    repo.save(a);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public List<PriceAlert> getAlerts(String userId) {
        return repo.findByUserIdAndActiveTrue(userId);
    }

    @Override
    public Map<String, List<PriceAlert>> getAllAlerts() {
        return repo.findByActiveTrue().stream()
                .collect(Collectors.groupingBy(PriceAlert::getUserId));
    }

    @Override
    public List<PriceAlert> getAllActiveAlerts() {
        return repo.findByActiveTrue();
    }
}
