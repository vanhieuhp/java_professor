package dev.hieunv.uploadfile;

import dev.hieunv.uploadfile.chunkstorage.ChunkStorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;

@Controller
@RequestMapping("/files/multipart")
@RequiredArgsConstructor
public class MultipartUploadController {

	private final ChunkStorageService chunkStorage;

	@PostMapping("/initiate")
	@ResponseBody
	public InitiateResponse initiate(@RequestBody InitiateRequest req) throws IOException {
		String uploadId = chunkStorage.initiateUpload(req.getFilename(), req.getTotalSize(), req.getTotalChunks(), req.getChunkSize());
		InitiateResponse res = new InitiateResponse();
		res.setUploadId(uploadId);
		res.setChunkSize(req.getChunkSize() != null ? req.getChunkSize() : 5 * 1024 * 1024);
		return res;
	}

	@PutMapping("/{uploadId}/{chunkIndex}")
	public ResponseEntity<?> putChunk(@PathVariable String uploadId,
	                                 @PathVariable int chunkIndex,
	                                 HttpServletRequest request,
	                                 @RequestHeader(value = "X-Chunk-Checksum", required = false) String checksum) throws IOException {
		try (var in = request.getInputStream()) {
			chunkStorage.saveChunk(uploadId, chunkIndex, in, checksum);
		}
		return ResponseEntity.ok().build();
	}

	@GetMapping("/{uploadId}/status")
	@ResponseBody
	public StatusResponse status(@PathVariable String uploadId) throws IOException {
		Set<Integer> chunks = chunkStorage.getReceivedChunks(uploadId);
		StatusResponse res = new StatusResponse();
		res.setReceivedChunks(chunks);
		return res;
	}

	@PostMapping("/{uploadId}/complete")
	@ResponseBody
	public CompleteResponse complete(@PathVariable String uploadId,
	                                @RequestBody(required = false) CompleteRequest req) throws IOException {
		var path = chunkStorage.complete(uploadId, req == null ? null : req.getFileChecksum());
		CompleteResponse res = new CompleteResponse();
		res.setPath(path.toString());
		return res;
	}

	@DeleteMapping("/{uploadId}")
	public ResponseEntity<?> abort(@PathVariable String uploadId) throws IOException {
		chunkStorage.abort(uploadId);
		return ResponseEntity.ok().build();
	}

	@Data
	public static class InitiateRequest {
		private String filename;
		private Long totalSize;
		private Integer totalChunks;
		private Integer chunkSize;
	}

	@Data
	public static class InitiateResponse {
		private String uploadId;
		private int chunkSize;
	}

	@Data
	public static class StatusResponse {
		private Set<Integer> receivedChunks;
	}

	@Data
	public static class CompleteRequest {
		private String fileChecksum;
	}

	@Data
	public static class CompleteResponse {
		private String path;
	}
}


