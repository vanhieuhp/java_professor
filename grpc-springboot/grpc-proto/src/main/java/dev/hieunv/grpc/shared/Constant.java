package dev.hieunv.grpc.shared;

import dev.hieunv.grpc.FileMetadata;
import io.grpc.Context;
import io.grpc.Metadata;

public class Constant {

    public static final Metadata.Key<byte[]> fileMetadataKey = Metadata.Key.of("file-metadata-bin", Metadata.BINARY_BYTE_MARSHALLER);
    public static final Context.Key<FileMetadata> fileMetadataContext = Context.key("file-metadata");
}
