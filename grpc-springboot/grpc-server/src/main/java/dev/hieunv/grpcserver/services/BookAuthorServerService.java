package dev.hieunv.grpcserver.services;

import dev.hieunv.grpc.Author;
import dev.hieunv.grpc.Book;
import dev.hieunv.grpc.BookAuthorServiceGrpc;
import dev.hieunv.grpc.TempDb;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.List;
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

    @Override
    public void getBooksByAuthor(Author request, StreamObserver<Book> responseObserver) {
        TempDb.getBooksFromTempDb()
                .stream()
                .filter(book -> book.getAuthorId() ==  request.getAuthorId())
                .forEach(responseObserver::onNext);
        log.info("Get Books by Author: {}", request.getAuthorId());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<Book> getExpensiveBook(StreamObserver<Book> responseObserver) {
        return new StreamObserver<>() {
            Book expensiveBook = null;
            float priceTrack = 0;

            @Override
            public void onNext(Book book) {
                if (book.getPrice() > priceTrack) {
                    priceTrack = book.getPrice();
                    expensiveBook = book;
                }
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(expensiveBook);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<Book> getBookByAuthorGender(StreamObserver<Book> responseObserver) {
        return new StreamObserver<>() {
            List<Book> bookList = new ArrayList<>();

            @Override
            public void onNext(Book book) {
                TempDb.getBooksFromTempDb()
                        .stream()
                        .filter(booksFromDb -> book.getAuthorId() == booksFromDb.getAuthorId())
                        .forEach(bookList::add);
            }

            @Override
            public void onError(Throwable throwable) {
                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                bookList.forEach(responseObserver::onNext);
                responseObserver.onCompleted();
            }
        };
    }
}
