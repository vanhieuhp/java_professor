package hieunv.dev.netflixstack.counter.reader;

import hieunv.dev.netflixstack.counter.eventlog.CounterEvent;
import hieunv.dev.netflixstack.counter.eventlog.EventLogStore;
import hieunv.dev.netflixstack.counter.rollup.RollupStore;
import hieunv.dev.netflixstack.counter.rollup.dto.RollupCheckpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service("accurate")
@RequiredArgsConstructor
@Slf4j
public class AccurateReaderServiceImpl implements CounterReaderService {

    private final EventLogStore eventLogStore;
    private final RollupStore rollupStore;

    @Override
    public long getCount(String counterId) {
        RollupCheckpoint cp = rollupStore.getCheckpoint(counterId);
        Instant since = cp.lastRollupTs();
        List<CounterEvent> delta = eventLogStore.readEventsSince(counterId, since);
        // readEventsSince() la inclusive (event_time >= since), nen event nam
        // dung tren high-water mark quay ve o day du no da duoc cong vao
        // lastRollupCount tu pass truoc. Khong loc thi no bi dem hai lan va
        // reader "chinh xac" tra ve 1051 thay vi 1050 - dung dung bo loc ma
        // RollupServiceImpl.currentValue() dang dung.
        long deltaSum = delta.stream()
                .filter(event -> event.eventTime().isAfter(since))
                .mapToLong(CounterEvent::delta)
                .sum();
        return cp.lastRollupCount() + deltaSum;
    }
}
