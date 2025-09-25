package dev.hieunv.grpcserver.interceptors;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

import java.util.Objects;

@GrpcGlobalServerInterceptor
@Slf4j
public class ApiKeyAuthInterceptor implements ServerInterceptor {

    @Value("${grpc.api-key}")
    private String secretApiKey;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        log.info("Server interceptor {}", serverCall.getMethodDescriptor());

        Metadata.Key<String> apiKeyMetadata = Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);
        String apiKey = metadata.get(apiKeyMetadata);
        log.info("x-api-key from client {}", apiKey);

        if (Objects.nonNull(apiKey) && apiKey.equals(secretApiKey)) {
            return serverCallHandler.startCall(serverCall, metadata);
        } else {
            Status status = Status.UNAUTHENTICATED.withDescription("Invalid api key");
            serverCall.close(status, metadata);
        }

        return new ServerCall.Listener<>() {
        };
    }
}
