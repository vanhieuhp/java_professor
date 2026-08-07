package dev.hieunv.riskassessment.client;

import dev.hieunv.riskassessment.dto.CoreRiskUpdateAck;
import dev.hieunv.riskassessment.dto.CoreRiskUpdateRequest;
import dev.hieunv.riskassessment.service.PcrtConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Client gọi Core ví.
 *
 * <h2>Retry — dùng {@code @Retryable} sẵn có của Spring Framework 7</h2>
 * Không cần Resilience4j: từ Spring Framework 7, {@code org.springframework.resilience} có
 * sẵn retry với backoff mũ và jitter.
 * <p>
 * <b>Jitter là thứ hay bị bỏ quên.</b> Nếu 500 kết quả cùng lỗi tại một thời điểm (Core vừa
 * sập) và tất cả cùng chờ đúng 300ms rồi 600ms rồi 1200ms, chúng sẽ dội vào Core thành từng
 * đợt đồng pha — đúng cái mà backoff định tránh. Jitter làm lệch ngẫu nhiên các mốc đó.
 *
 * <h2>Timeout phải nhỏ hơn nhiều so với chu kỳ job</h2>
 * Connect 2s + read 3s, tối đa 4 lần thử → xấu nhất ~20s cho một kết quả. Chu kỳ job là 5s
 * và pool chỉ một luồng, nên timeout dài hơn sẽ khiến các lần chạy chồng lên nhau.
 * Không đặt timeout mới là lỗi tệ nhất: mặc định của JDK là chờ vô hạn, một Core treo sẽ
 * giữ luồng gửi mãi mãi và toàn bộ hàng đợi đứng im mà không có lỗi nào xuất hiện.
 */
@Slf4j
@Component
public class CoreWalletClient {

    private final RestClient restClient;
    private final PcrtConfigService configService;

    public CoreWalletClient(PcrtConfigService configService) {
        this.configService = configService;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Gửi kết quả rủi ro sang Core.
     * <p>
     * Chỉ thử lại {@link CoreTransientException}. {@link CorePermanentException} (4xx) ném
     * thẳng ra ngoài — lệnh sai thì gửi lại một nghìn lần vẫn sai.
     */
    @Retryable(
            includes = CoreTransientException.class,
            maxRetries = 3,
            delay = 300,
            multiplier = 2.0,
            maxDelay = 3000,
            jitter = 150)
    public CoreRiskUpdateAck sendRiskAssessment(CoreRiskUpdateRequest request) {
        String baseUrl = configService.get("core.base-url", "http://localhost:8080/mock-core");
        try {
            return restClient.post()
                    .uri(baseUrl + "/api/v1/customers/risk-assessment")
                    .body(request)
                    .exchange((req, res) -> {
                        HttpStatusCode status = res.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            return res.bodyTo(CoreRiskUpdateAck.class);
                        }
                        String body = readBody(res);
                        if (status.is4xxClientError()) {
                            throw new CorePermanentException("Core từ chối (" + status.value() + "): " + body);
                        }
                        throw new CoreTransientException("Core lỗi (" + status.value() + "): " + body);
                    });
        } catch (ResourceAccessException e) {
            // Timeout, connection refused, DNS hỏng — mạng, không phải nghiệp vụ.
            throw new CoreTransientException("Không gọi được Core: " + e.getMessage(), e);
        }
    }

    private static String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res) {
        try {
            String body = res.bodyTo(String.class);
            return body == null ? "" : body.substring(0, Math.min(body.length(), 300));
        } catch (RuntimeException e) {
            return "<không đọc được body>";
        }
    }
}
