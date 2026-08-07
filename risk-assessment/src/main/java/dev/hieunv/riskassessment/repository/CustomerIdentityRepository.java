package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.entity.CustomerIdentity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerIdentityRepository extends JpaRepository<CustomerIdentity, String> {

    @Query(value = "SELECT count(*) FROM pcrt_customer_identity WHERE scan_target", nativeQuery = true)
    long countScanTargets();

    /**
     * Mốc để lấy delta lần sau. Lấy max của {@code core_updated_at} chứ không phải
     * {@code synced_at}: mốc phải nằm trên đồng hồ của Core, vì đó là đồng hồ mà câu
     * {@code WHERE update_time > :since} so sánh. Dùng đồng hồ của PCRT sẽ bỏ sót mọi
     * thay đổi xảy ra trong lúc job đang chạy.
     */
    @Query(value = "SELECT max(core_updated_at) FROM pcrt_customer_identity", nativeQuery = true)
    Optional<Instant> findLatestCoreUpdatedAt();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM CustomerIdentity i WHERE i.cif = :cif")
    Optional<CustomerIdentity> findByCifForUpdate(@Param("cif") String cif);

    /**
     * Bản nhiều dòng của {@link #findByCifForUpdate}, cho job đồng bộ hàng loạt: một câu SELECT
     * cho cả trang thay vì một câu cho mỗi dòng.
     *
     * <h2>Vì sao job nền cũng phải khóa</h2>
     * Job đọc trang rồi ghi trang, còn đường realtime ghi xen vào giữa hai việc đó được. Không
     * khóa thì job so mốc trên dữ liệu đã cũ và ghi đè lên một thay đổi TH2 vừa ghi xong —
     * đúng cái mà chốt thứ tự sinh ra để chặn, thua ngay ở đường mà nó cần chặn nhất.
     * <p>
     * Giá phải trả là khóa {@code pageSize} dòng trong lúc ghi một trang. Chấp nhận được vì
     * trang nhỏ (mặc định 2000) và transaction chỉ sống trong một lượt ghi.
     *
     * <h2>Vì sao ORDER BY</h2>
     * Postgres khóa theo thứ tự trả về. Khóa theo một thứ tự cố định thì hai bên ghi không thể
     * xếp thành vòng chờ nhau — deadlock cần mỗi bên giữ cái bên kia đang đợi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM CustomerIdentity i WHERE i.cif IN :cifs ORDER BY i.cif")
    List<CustomerIdentity> findAllByCifInForUpdate(@Param("cifs") Collection<String> cifs);
}
