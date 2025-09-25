package dev.hieunv.grpcclient.service;

import com.google.protobuf.Descriptors;
import dev.hieunv.grpc.Author;
import dev.hieunv.grpc.BookAuthorServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookAuthorServiceImpl implements BookAuthorService{


    @GrpcClient("grpc-devhieunv-service")
    BookAuthorServiceGrpc.BookAuthorServiceBlockingStub synchronousClient;

    @Override
    public Map<Descriptors.FieldDescriptor, Object> getAuthor(int authorId) {
        Author authorRequest = Author.newBuilder().setAuthorId(authorId).build();
        Author authorResponse = synchronousClient.getAuthor(authorRequest);
        return authorResponse.getAllFields();
    }
}
