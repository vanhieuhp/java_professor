package dev.hieunv.grpcserver.interceptors;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.hieunv.grpc.FileMetadata;
import dev.hieunv.grpc.shared.Constant;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@GrpcGlobalServerInterceptor
public class FileUploadInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String fullMethodName = serverCall.getMethodDescriptor().getFullMethodName();
        // Only handle file upload RPC; bypass all others
        if (!"dev.hieunv.grpc.FileUploadService/uploadFile".equals(fullMethodName)) {
            return serverCallHandler.startCall(serverCall, metadata);
        }

        FileMetadata fileMetadata = null;
        if (!metadata.containsKey(Constant.fileMetadataKey)) {
            Status status = Status.INVALID_ARGUMENT.withDescription("missing file metadata header");
            serverCall.close(status, metadata);
            return new ServerCall.Listener<>() {};
        }

        byte[] bytes = metadata.get(Constant.fileMetadataKey);
        try {
            fileMetadata = FileMetadata.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            Status status = Status.INTERNAL.withDescription("unable to create file metadata object");
            serverCall.close(status, metadata);
            return new ServerCall.Listener<>() {};
        }

        Context context = Context.current().withValue(
                Constant.fileMetadataContext,
                fileMetadata
        );

        return Contexts.interceptCall(context, serverCall, metadata, serverCallHandler);
    }
}
