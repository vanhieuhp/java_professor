package dev.hieunv.grpcserver.services;

import dev.hieunv.grpc.FileMetadata;
import dev.hieunv.grpc.FileUploadRequest;
import dev.hieunv.grpc.FileUploadResponse;
import dev.hieunv.grpc.FileUploadServiceGrpc;
import dev.hieunv.grpc.UploadStatus;
import dev.hieunv.grpc.shared.Constant;
import dev.hieunv.grpcserver.utils.DiskFileStorage;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.io.IOException;

@GrpcService
@Slf4j
public class FileUploadServiceImpl extends FileUploadServiceGrpc.FileUploadServiceImplBase implements FileUploadService {

    @Override
    public StreamObserver<FileUploadRequest> uploadFile(StreamObserver<FileUploadResponse> responseObserver) {
        log.info("uploadFile");
        FileMetadata fileMetadata = Constant.fileMetadataContext.get();
        DiskFileStorage diskFileStorage = new DiskFileStorage();

        return new StreamObserver<>() {
            @Override
            public void onNext(FileUploadRequest fileUploadRequest) {
                // called when client sends the data
                log.info("received {} length of data", fileUploadRequest.getFile().getContent().size());
                try {
                    fileUploadRequest.getFile().getContent()
                            .writeTo(diskFileStorage.getByteArrayOutputStream());
                } catch (IOException e) {
                    responseObserver.onError(Status.INTERNAL.withDescription("can not write data due to: " + e.getMessage())
                            .asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                // called when client sends error
                log.warn("{}", throwable.toString());
            }

            @Override
            public void onCompleted() {
                // called when client finished sending the data
                try {
                    int totalBytesReceived = diskFileStorage.getByteArrayOutputStream().size();
                    if (totalBytesReceived == fileMetadata.getContentLength()) {
                        diskFileStorage.write(fileMetadata.getFileNameWithType());
                        diskFileStorage.close();
                    } else {
                        responseObserver.onError(Status.INTERNAL
                                .withDescription(String.format("received %d bytes but expected %d bytes", fileMetadata.getContentLength(), totalBytesReceived))
                                .asRuntimeException());
                    }
                } catch (IOException e) {
                    responseObserver.onError(Status.INTERNAL.withDescription("can not write data due to: " + e.getMessage())
                            .asRuntimeException());
                }

                responseObserver.onNext(
                        FileUploadResponse.newBuilder()
                                .setFileName(fileMetadata.getFileNameWithType())
                                .setUploadStatus(UploadStatus.SUCCESS)
                                .build()
                );

                responseObserver.onCompleted();
            }
        };
    }
}
