package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    @Query("SELECT p.code FROM UserPermission up JOIN Permission p ON p.id = up.permissionId WHERE up.userId = :userId")
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);
}
