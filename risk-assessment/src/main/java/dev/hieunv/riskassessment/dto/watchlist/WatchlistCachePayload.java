package dev.hieunv.riskassessment.dto.watchlist;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;


/*
* WatchlistCachePayload      toàn bộ danh sách, dạng JSON, có version   ← Redis
  ├─ Watchlist               một danh sách hoàn chỉnh
  │    ├─ WatchlistCategory  cấu hình  (1 dòng DB)
  │    └─ WatchlistEntry     nội dung  (n dòng DB)
  └─ loadedFrom              mốc để biết còn tươi không
WatchlistSnapshot            cùng dữ liệu, đã biên dịch                 ← RAM
  └─ CompiledCategory        index băm + closure
* */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistCachePayload {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private int schemaVersion;

    private Instant loadedFrom;

    private Watchlist blacklist;

    private List<Watchlist> cifEvaluateLists;
}
