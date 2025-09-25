package dev.hieunv.grpcclient.service;

import com.google.protobuf.ByteString;
import dev.hieunv.grpc.File;
import dev.hieunv.grpc.FileMetadata;
import dev.hieunv.grpc.FileUploadRequest;
import dev.hieunv.grpc.FileUploadResponse;
import dev.hieunv.grpc.FileUploadServiceGrpc;
import dev.hieunv.grpc.UploadStatus;
import dev.hieunv.grpc.shared.Constant;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    private final FileUploadServiceGrpc.FileUploadServiceStub client;

    public FileUploadServiceImpl(@GrpcClient("grpc-devhieunv-service") FileUploadServiceGrpc.FileUploadServiceStub client) {
        this.client = client;
    }

    @Override
    public String uploadFile(MultipartFile multipartFile) {
        String fileName;
        int fileSize;
        InputStream inputStream;
        fileName = multipartFile.getOriginalFilename();

        try {
            fileSize = multipartFile.getBytes().length;
            inputStream = multipartFile.getInputStream();
        } catch (IOException e) {
            return "unable to extract file info";
        }

        StringBuilder response = new StringBuilder();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Metadata metadata = new Metadata();
        metadata.put(Constant.fileMetadataKey,
                FileMetadata
                        .newBuilder().setContentLength(fileSize)
                        .setFileNameWithType(fileName)
                        .build().toByteArray());
        StreamObserver<FileUploadRequest> fileUploadRequestStreamObserver = this.client
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .uploadFile(new StreamObserver<>() {
                    @Override
                    public void onNext(FileUploadResponse fileUploadResponse) {
                        // called when server sends the response
                        response.append(fileUploadResponse.getUploadStatus());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        // called when the server sends any error
                        response.append(UploadStatus.FAILED);
                        throwable.printStackTrace();
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        // called server finished serving the request
                        countDownLatch.countDown();
                    }
                });

        byte[] fiveKb = new byte[5120];
        int length;
        try {
            while ((length = inputStream.read(fiveKb)) > 0) {
                var request = FileUploadRequest
                        .newBuilder()
                        .setFile(
                                File.newBuilder()
                                        .setContent(ByteString.copyFrom(fiveKb, 0, length))
                                        .build()
                        ).build();

                // sending the request that contains the chunked data of file.
                fileUploadRequestStreamObserver.onNext(request);
            }
            inputStream.close();
            fileUploadRequestStreamObserver.onCompleted();
            countDownLatch.await();

        } catch (Exception e) {
            e.printStackTrace();
            response.append(UploadStatus.FAILED);
        }

        return response.toString();
    }
}
