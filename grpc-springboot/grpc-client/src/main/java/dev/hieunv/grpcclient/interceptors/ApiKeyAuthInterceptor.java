package dev.hieunv.grpcclient.interceptors;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;

@GrpcGlobalClientInterceptor
@Slf4j
public class ApiKeyAuthInterceptor implements ClientInterceptor {

    @Value("${grpc.api-key}")
    private String secretApiKey;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
        log.info("client interceptor: {}", methodDescriptor.getFullMethodName());

        return new ForwardingClientCall.SimpleForwardingClientCall<>(channel.newCall(methodDescriptor, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), secretApiKey);
                super.start(responseListener, headers);
            }
        };
    }
}
