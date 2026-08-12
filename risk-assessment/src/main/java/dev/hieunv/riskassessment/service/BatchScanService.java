package dev.hieunv.riskassessment.service;

import java.util.UUID;

public interface BatchScanService {
    // T1 — blacklist changed
    UUID startBlacklistScan();

    // TH3A — đánh giá định kỳ
    UUID startPeriodicScan();
}
