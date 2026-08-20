package hieunv.dev.netflixstack.counter.hybridshardedflush.dto;

import java.util.Map;

public record ValueResponse(long total, Map<Integer, Long> shards) {
}
