package dev.hieunv.price_radar.repository;

import dev.hieunv.price_radar.model.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, String> {

    List<PriceAlert> findByUserIdAndActiveTrue(String userId);

    List<PriceAlert> findByActiveTrue();
}
