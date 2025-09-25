package dev.hieunv.grpcclient;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GrpcHealthChecker {

    @GrpcClient("grpc-devhieunv-service")
    private HealthGrpc.HealthBlockingStub healthStub;

    @EventListener(ApplicationReadyEvent.class)
    public void checkServerHealth() {
        try {
            HealthCheckResponse response = healthStub.check(
                    HealthCheckRequest.newBuilder().build()
            );
            log.info("gRPC server health status: {}", response.getStatus());
        } catch (Exception e) {
            log.warn("gRPC server health check failed: {}", e.getMessage());
        }
    }

}
