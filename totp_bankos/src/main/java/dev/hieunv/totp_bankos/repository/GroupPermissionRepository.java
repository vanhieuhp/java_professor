package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.GroupPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupPermissionRepository extends JpaRepository<GroupPermission, Long> {

    // all permissions assigned to a group
    List<GroupPermission> findByGroupId(Long groupId);

    // check if a group already has a specific permission
    boolean existsByGroupIdAndPermissionId(Long groupId, Long permissionId);

    // remove all permissions from a group (used in full replacement)
    @Modifying
    @Query("DELETE FROM GroupPermission gp WHERE gp.groupId = :groupId")
    void deleteAllByGroupId(@Param("groupId") Long groupId);

    // resolve all permission codes for a user in a specific wallet (used at wallet activation)
    @Query("""
        SELECT p.code
        FROM GroupPermission gp
        JOIN Permission p        ON p.id  = gp.permissionId
        JOIN WalletUserGroup wug ON wug.groupId  = gp.groupId
        WHERE wug.userId   = :userId
          AND wug.walletId = :walletId
          AND wug.isActive = true
        """)
    List<String> findPermissionCodesByUserIdAndWalletId(
            @Param("userId")   Long userId,
            @Param("walletId") Long walletId
    );

    // resolve all permission codes for a user across ALL wallets (used at login)
    @Query("""
        SELECT DISTINCT p.code
        FROM GroupPermission gp
        JOIN Permission p        ON p.id  = gp.permissionId
        JOIN WalletUserGroup wug ON wug.groupId = gp.groupId
        WHERE wug.userId   = :userId
          AND wug.isActive = true
        """)
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);
}