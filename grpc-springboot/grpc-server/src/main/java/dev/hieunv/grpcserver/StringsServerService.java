package dev.hieunv.grpcserver;

import dev.hieunv.grpc.strings.Request;
import dev.hieunv.grpc.strings.Response;
import dev.hieunv.grpc.strings.StringsServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@RequiredArgsConstructor
@Slf4j
@GrpcService
public class StringsServerService extends StringsServiceGrpc.StringsServiceImplBase {

    @Override
    public void getUpperCaseString(Request request, StreamObserver<Response> responseObserver) {
        log.info("getUpperCaseString {} -> {}", request.getLowerCase(), request.getLowerCase().toUpperCase());
        responseObserver.onNext(
                Response.newBuilder().setUpperCase(request.getLowerCase().toUpperCase()).build()
        );
        responseObserver.onCompleted();
    }
}
