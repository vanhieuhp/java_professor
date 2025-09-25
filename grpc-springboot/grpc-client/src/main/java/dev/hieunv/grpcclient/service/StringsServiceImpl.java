package dev.hieunv.grpcclient.service;

import dev.hieunv.grpc.strings.Request;
import dev.hieunv.grpc.strings.Response;
import dev.hieunv.grpc.strings.StringsServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StringsServiceImpl implements StringsService {

    @GrpcClient("grpc-devhieunv-service")
    StringsServiceGrpc.StringsServiceBlockingStub client;

    @Override
    public String getUpperCase(String lowercase) {
        try {
            Response response = client.getUpperCaseString(Request.newBuilder().setLowerCase(lowercase).build());
            return response.getUpperCase();
        } catch (Exception e) {
            log.error("error: {}", e.getMessage());
            return e.getMessage();
        }
    }
}
