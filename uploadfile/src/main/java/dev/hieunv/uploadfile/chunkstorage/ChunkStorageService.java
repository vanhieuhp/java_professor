package dev.hieunv.uploadfile.chunkstorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

public interface ChunkStorageService {

	String initiateUpload(String filename, Long totalSize, Integer totalChunks, Integer chunkSize) throws IOException;

	void saveChunk(String uploadId, int chunkIndex, InputStream content, String checksum) throws IOException;

	Set<Integer> getReceivedChunks(String uploadId) throws IOException;

	Path complete(String uploadId, String expectedChecksum) throws IOException;

	void abort(String uploadId) throws IOException;
}


