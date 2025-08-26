package dev.hieunv.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@RestControllerAdvice
public class EncryptResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (!wantsEncrypted(request)) return body;

        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(body);
            String aadStr = request.getMethod() + " " + request.getURI().getPath();
            EncryptedPayload encryptedPayload = cryptoService.encrypt(plaintext, aadStr.getBytes(StandardCharsets.UTF_8));
            response.getHeaders().setContentType(MediaType.valueOf("application/encrypted+json"));
            return encryptedPayload;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt response body", e);
        }
    }

    public static boolean wantsEncrypted(ServerHttpRequest request) {
        HttpHeaders h = request.getHeaders();
        boolean header = "1".equals(h.getFirst("X-Encrypted"));
        boolean accept = h.getAccept().stream()
                .anyMatch(mt -> mt.isCompatibleWith(MediaType.valueOf("application/encrypted+json")));

        return header || accept;
    }
}
