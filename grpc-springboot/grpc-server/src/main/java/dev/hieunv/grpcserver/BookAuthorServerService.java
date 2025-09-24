package dev.hieunv.grpcserver;

import dev.hieunv.grpc.Author;
import dev.hieunv.grpc.BookAuthorServiceGrpc;
import dev.hieunv.grpc.TempDb;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class BookAuthorServerService extends BookAuthorServiceGrpc.BookAuthorServiceImplBase {

    @Override
    public void getAuthor(Author request, StreamObserver<Author> responseObserver) {
        TempDb.getAuthorsFromTempDb().stream()
                .filter(author -> author.getAuthorId() == request.getAuthorId())
                .findFirst()
                .ifPresent(responseObserver::onNext);

        responseObserver.onCompleted();
    }
}
