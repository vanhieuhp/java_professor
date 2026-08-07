package dev.hieunv.riskassessment.dto;

import dev.hieunv.riskassessment.matching.CompiledCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class WatchlistSnapshot {

    private CompiledCategory blacklist;

    private List<CompiledCategory> cifEvaluateLists;

    private Instant loadedFrom;
}
