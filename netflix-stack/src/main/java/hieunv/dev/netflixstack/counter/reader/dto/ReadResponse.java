package hieunv.dev.netflixstack.counter.reader.dto;

public record ReadResponse(String counterId, String mode, long value, double latencyMs) {
}
