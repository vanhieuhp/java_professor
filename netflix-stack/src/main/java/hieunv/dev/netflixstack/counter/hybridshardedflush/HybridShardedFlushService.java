package hieunv.dev.netflixstack.counter.hybridshardedflush;

import hieunv.dev.netflixstack.counter.dto.LoadTestRequest;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.ChaosSlowRequest;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.IncrementResponse;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.LoadTestResult;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.StatusResponse;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.ValueResponse;

public interface HybridShardedFlushService {

    IncrementResponse increment(int serverId);

    StatusResponse status(int serverId);

    ValueResponse currentValue();

    void induceDeadChaos(int serverId);

    void induceSlowChaos(int serverId, ChaosSlowRequest request);

    void recoverChaos(int serverId);

    LoadTestResult runLoadTest(int serverId, LoadTestRequest request);
}
