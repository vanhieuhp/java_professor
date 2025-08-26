package dev.hieunv.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@ControllerAdvice
public class DecryptRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                           Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        if (!isEncrypted(inputMessage)) return inputMessage;

        byte[] body = inputMessage.getBody().readAllBytes();
        EncryptedPayload payload = objectMapper.readValue(body, EncryptedPayload.class);

        byte[] aad = computeAad();
        byte[] decrypted = cryptoService.decrypt(payload, aad);

        return new HttpInputMessage() {
            @Override
            public InputStream getBody() throws IOException {
                return new ByteArrayInputStream(decrypted);
            }

            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders h = new HttpHeaders();
                h.putAll(inputMessage.getHeaders());
                h.setContentType(MediaType.APPLICATION_JSON);
                h.remove(HttpHeaders.CONTENT_LENGTH);

                return h;
            }
        };
    }

    private static boolean isEncrypted(HttpInputMessage inputMessage) {
        HttpHeaders headers = inputMessage.getHeaders();
        boolean header = "1".equals(headers.getFirst("X-Encrypted"));
        boolean cType = MediaType.valueOf(headers.getFirst(HttpHeaders.CONTENT_TYPE) != null
                        ? headers.getFirst(HttpHeaders.CONTENT_TYPE)
                        : MediaType.APPLICATION_JSON_VALUE)
                .isCompatibleWith(MediaType.valueOf("application/encrypted+json"));
        return header && cType;
    }

    private static byte[] computeAad() {
        // Bind ciphertext to method+path to prevent replay across endpoints
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return new byte[0];
        String method = attrs.getRequest().getMethod();
        String path = attrs.getRequest().getRequestURI();
        String aad = method + " " + path;
        return aad.getBytes(StandardCharsets.UTF_8);
    }
}
