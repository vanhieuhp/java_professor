package dev.hieunv.grpcserver;

import dev.hieunv.grpc.Author;
import dev.hieunv.grpc.BookAuthorServiceGrpc;
import dev.hieunv.grpc.TempDb;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Optional;

@Slf4j
@GrpcService
public class BookAuthorServerService extends BookAuthorServiceGrpc.BookAuthorServiceImplBase {

    @Override
    public void getAuthor(Author request, StreamObserver<Author> responseObserver) {
        try {
            log.info("getAuthor: {}", request);
            Optional<Author> author = TempDb.getAuthorsFromTempDb().stream()
                    .filter(a -> a.getAuthorId() == request.getAuthorId())
                    .findFirst();
            
            if (author.isPresent()) {
                responseObserver.onNext(author.get());
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(new RuntimeException("Author with ID " + request.getAuthorId() + " not found"));
            }
        } catch (Exception e) {
            responseObserver.onError(new RuntimeException("Error retrieving author: " + e.getMessage()));
        }
    }
}
