package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.entity.PcrtConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PcrtConfigRepository extends JpaRepository<PcrtConfig, String> {

    @Modifying
    @Query(value = """
            UPDATE pcrt_config SET config_value = :value, updated_at = now()
            WHERE config_key = :key
            """, nativeQuery = true)
    int updateValue(@Param("key") String key, @Param("value") String value);
}
