package dev.hieunv.grpc.shared;

import io.grpc.Metadata;

public class Constant {

    public static final Metadata.Key<byte[]> fileMetadataKey = Metadata.Key.of("file-metadata-bin", Metadata.BINARY_BYTE_MARSHALLER);
}
