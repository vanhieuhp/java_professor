package dev.hieunv.grpc;

import io.grpc.health.v1.HealthGrpc;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.autoconfigure.server.GrpcServerFactoryCustomizer;
import org.springframework.grpc.server.NettyGrpcServerFactory;
import org.springframework.grpc.server.ServerServiceDefinitionFilter;

import java.util.Set;

@Configuration
public class GrpcConfig {

    @Bean
    ServerServiceDefinitionFilter myServiceFilter() {
        return (serviceDefinition, __) ->
                !Set.of(HealthGrpc.SERVICE_NAME, ServerReflectionGrpc.SERVICE_NAME)
                        .contains(serviceDefinition.getServiceDescriptor().getName());
    }

    @Bean
    GrpcServerFactoryCustomizer myServerFactoryCustomizer(ServerServiceDefinitionFilter myServiceFilter) {
        return factory -> {
            if (factory instanceof NettyGrpcServerFactory nettyGrpcServerFactory) {
                nettyGrpcServerFactory.setServiceFilter(myServiceFilter);
            }
        };
    }
}
