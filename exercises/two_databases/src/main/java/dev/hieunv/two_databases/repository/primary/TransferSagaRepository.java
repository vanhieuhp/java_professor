package dev.hieunv.two_databases.repository.primary;

import dev.hieunv.two_databases.common.Status;
import dev.hieunv.two_databases.domain.primary.TransferSaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferSagaRepository extends JpaRepository<TransferSaga, UUID> {

    List<TransferSaga> findByStatus(Status status);

    Optional<TransferSaga> findByCompensationKey(String compensationKey);
}
