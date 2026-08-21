package hieunv.dev.netflixstack.counter.rollup;

import hieunv.dev.netflixstack.counter.rollup.dto.RollupCheckpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RollupStoreImpl implements RollupStore {

    private final RollupCheckpointRepository rollupCheckpointRepository;

    @Override
    public RollupCheckpoint getCheckpoint(String counterId) {
        return rollupCheckpointRepository.findById(counterId)
                .map(entity -> new RollupCheckpoint(
                        entity.getCounterId(), entity.getLastRollupCount(), entity.getLastRollupTs()))
                .orElseGet(() -> RollupCheckpoint.empty(counterId));
    }


    @Override
    public void saveCheckpoint(RollupCheckpoint checkpoint, long writeTimestampMicros) {
        rollupCheckpointRepository.upsertWithTimestamp(
                checkpoint.counterId(),
                checkpoint.lastRollupCount(),
                checkpoint.lastRollupTs(),
                writeTimestampMicros);
    }
}
