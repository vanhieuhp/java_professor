package dev.hieunv.bankos.service;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface ExchangeRateService {
    BigDecimal getRate(String currencyPair);

    @Transactional
    void updateRate(String currencyPair, BigDecimal newRate);

    void printCacheStats();
}
