package dev.hieunv.price_radar.service;

import dev.hieunv.price_radar.model.PriceAlert;

import java.util.List;
import java.util.Map;

public interface AlertService {

    PriceAlert createAlert(String userId, String product, double threshold);

    boolean deleteAlert(String userId, String alertId);

    List<PriceAlert> getAlerts(String userId);

    Map<String, List<PriceAlert>> getAllAlerts();

    List<PriceAlert> getAllActiveAlerts();
}
