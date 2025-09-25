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
        FileMetadata fileMetadata = null;
        if (metadata.containsKey(Constant.fileMetadataKey)) {
            byte[] bytes = metadata.get(Constant.fileMetadataKey);
            try {
                fileMetadata = FileMetadata.parseFrom(bytes);

            } catch (InvalidProtocolBufferException e) {
                Status status = Status.INTERNAL.withDescription("unable to create file metadata object");
                serverCall.close(status, metadata);
            }

            Context context = Context.current().withValue(
                    Constant.fileMetadataContext,
                    fileMetadata
            );

            return Contexts.interceptCall(context, serverCall, metadata, serverCallHandler);
        }
        return new ServerCall.Listener<>() {};
    }
}
