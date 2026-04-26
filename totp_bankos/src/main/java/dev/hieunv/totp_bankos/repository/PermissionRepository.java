package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    List<Permission> findByFeatureId(Long featureId);

    List<Permission> findByFunctionId(Long functionId);

    // all permissions for a given feature code e.g. "TRANSFER"
    @Query("""
        SELECT p FROM Permission p
        JOIN Feature f ON f.id = p.featureId
        WHERE f.code = :featureCode
        """)
    List<Permission> findByFeatureCode(@Param("featureCode") String featureCode);
}