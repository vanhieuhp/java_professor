package dev.hieunv.uploadfile.chunkstorage;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class FileSystemChunkStorageService implements ChunkStorageService {

	private final Path tmpRoot;
	private final Path finalRoot;

	public FileSystemChunkStorageService(StorageProperties props) throws IOException {
		this.finalRoot = Path.of(props.getLocation());
		this.tmpRoot = this.finalRoot.resolve("tmp-uploads");
		Files.createDirectories(this.finalRoot);
		Files.createDirectories(this.tmpRoot);
	}

	@Override
	public String initiateUpload(String filename, Long totalSize, Integer totalChunks, Integer chunkSize) throws IOException {
		String uploadId = UUID.randomUUID().toString();
		Path dir = tmpRoot.resolve(uploadId);
		Files.createDirectories(dir);
		// minimally store filename
		Files.writeString(dir.resolve("filename.txt"), filename == null ? "file" : filename);
		return uploadId;
	}

	@Override
	public void saveChunk(String uploadId, int chunkIndex, InputStream content, String checksum) throws IOException {
		Path dir = tmpRoot.resolve(uploadId);
		Files.createDirectories(dir);
		Path part = dir.resolve(String.format("part-%05d", chunkIndex));
		Files.copy(content, part, StandardCopyOption.REPLACE_EXISTING);
		if (checksum != null && !checksum.isBlank()) {
			String actual = sha256Base64(Files.readAllBytes(part));
			if (!actual.equals(checksum)) {
				Files.deleteIfExists(part);
				throw new IOException("Chunk checksum mismatch");
			}
		}
	}

	@Override
	public Set<Integer> getReceivedChunks(String uploadId) throws IOException {
		Path dir = tmpRoot.resolve(uploadId);
		if (!Files.isDirectory(dir)) return Set.of();
		Set<Integer> out = new TreeSet<>();
		try (var stream = Files.list(dir)) {
			stream.filter(p -> p.getFileName().toString().startsWith("part-"))
					.forEach(p -> {
						String name = p.getFileName().toString();
						String idx = name.substring("part-".length());
						try { out.add(Integer.parseInt(idx)); } catch (NumberFormatException ignored) {}
					});
		}
		return out;
	}

	@Override
	public Path complete(String uploadId, String expectedChecksum) throws IOException {
		Path dir = tmpRoot.resolve(uploadId);
		if (!Files.isDirectory(dir)) throw new IOException("Upload not found");
		String filename = Files.readString(dir.resolve("filename.txt"));
		Path target = finalRoot.resolve(filename);
		Path tmpMerged = dir.resolve("merged.tmp");
		try (var out = Files.newOutputStream(tmpMerged)) {
			for (int i : getReceivedChunks(uploadId)) {
				Path part = dir.resolve(String.format("part-%05d", i));
				Files.copy(part, out);
			}
		}
		if (expectedChecksum != null && !expectedChecksum.isBlank()) {
			String actual = sha256Base64(Files.readAllBytes(tmpMerged));
			if (!actual.equals(expectedChecksum)) {
				throw new IOException("Final checksum mismatch");
			}
		}
		Files.move(tmpMerged, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		abort(uploadId);
		return target;
	}

	@Override
	public void abort(String uploadId) throws IOException {
		Path dir = tmpRoot.resolve(uploadId);
		if (Files.isDirectory(dir)) {
			try (var s = Files.walk(dir)) {
				s.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
					try { Files.deleteIfExists(p); } catch (IOException ignored) {}
				});
			}
		}
	}

	private static String sha256Base64(byte[] data) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(data);
			return Base64.getEncoder().encodeToString(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}


