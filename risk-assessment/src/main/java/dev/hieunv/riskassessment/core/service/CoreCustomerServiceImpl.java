package dev.hieunv.riskassessment.core.service;

import dev.hieunv.riskassessment.constant.CustomerStatus;
import dev.hieunv.riskassessment.constant.CustomerType;
import dev.hieunv.riskassessment.core.CoreCustomer;
import dev.hieunv.riskassessment.core.repository.CoreCustomerRepository;
import dev.hieunv.riskassessment.service.PcrtConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoreCustomerServiceImpl implements CoreCustomerService {

    public static final String KEY_SCAN_TARGET_STATUS = "core.customer.status.scan-target";
    private static final String ALL_STATUSES = "*";
    private static final List<String> UNUSED_STATUS_LIST = List.of("NUL");

    private final CoreCustomerRepository coreCustomerRepository;
    private final PcrtConfigService configService;

    /**
     * TH1 / TH3a — khách hàng cá nhân thuộc diện quét.
     */
    @Override
    public List<CoreCustomer> nextScanTargetPage(String afterId, int limit) {
        List<String> statuses = CustomerStatus.listActiveCodes();
        return coreCustomerRepository.findScanTargetsAfter(CustomerType.INDIVIDUAL.getCode(), statuses, afterId, page(limit));
    }

    /**
     * TH3b — khách hàng MỚI MỞ VÍ trong khoảng {@code [from, to)}.
     */
    @Override
    public List<CoreCustomer> nextEnrolledBetweenPage(Instant from, Instant to, String afterId, int limit) {
        List<String> statuses = CustomerStatus.listActiveCodes();
        return coreCustomerRepository.findEnrolledBetweenAfter(
                CustomerType.INDIVIDUAL.getCode(), statuses, from, to, afterId, page(limit));
    }

    /**
     * Đồng bộ bản chiếu, đường nạp toàn bộ — mọi khách hàng, không lọc gì.
     */
    public List<CoreCustomer> nextPage(String afterId, int limit) {
        return coreCustomerRepository.findAllAfter(afterId, CustomerType.INDIVIDUAL.getCode(), page(limit));
    }

    /**
     * Đồng bộ bản chiếu, đường delta — chỉ bắt được khách hàng mở ví sau {@code since}.
     */
    public List<CoreCustomer> nextEnrolledAfterPage(Instant since, String afterId, int limit) {
        return coreCustomerRepository.findEnrolledAfter(since, afterId, CustomerType.INDIVIDUAL.getCode(), page(limit));
    }

    public List<CoreCustomer> findByCifIn(List<String> cifs) {
        return cifs.isEmpty() ? List.of() : coreCustomerRepository.findByCifIn(cifs, CustomerType.INDIVIDUAL.getCode());
    }

    public long countScanTargets() {
        List<String> statuses = CustomerStatus.listActiveCodes();
        return coreCustomerRepository.countScanTargets(CustomerType.INDIVIDUAL.getCode(), statuses);
    }

    /**
     * Giờ của database Core — mốc đồng bộ phải cùng đồng hồ với dữ liệu nó so sánh.
     */
    public Instant coreNow() {
        return coreCustomerRepository.coreNow().toInstant();
    }

    public boolean isScanTarget(CoreCustomer customer) {
        if (!isIndividualType(customer.getCustomerType())) {
            return false;
        }
        return isScanTargetStatus(customer.getStatus());
    }


    public boolean isIndividualType(String customerType) {
        return CustomerType.INDIVIDUAL.getCode().equals(customerType);
    }

    /**
     * Mã trạng thái này có thuộc diện quét theo cấu hình hiện tại không.
     */
    public boolean isScanTargetStatus(String customerStatus) {
        StatusFilter status = statusFilter();
        return status.any() || status.values().contains(customerStatus);
    }

    // -----------------------------------------------------------------
    private static Pageable page(int limit) {
        return PageRequest.ofSize(limit);
    }

    private StatusFilter statusFilter() {
        String raw = configService.get(KEY_SCAN_TARGET_STATUS, ALL_STATUSES).trim();
        if (ALL_STATUSES.equals(raw)) {
            return new StatusFilter(true, Set.copyOf(UNUSED_STATUS_LIST));
        }
        Set<String> parsed = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (parsed.isEmpty()) {
            log.warn("Config {} = '{}' does not list any status — falling back to scanning every status",
                    KEY_SCAN_TARGET_STATUS, raw);
            return new StatusFilter(true, Set.copyOf(UNUSED_STATUS_LIST));
        }
        return new StatusFilter(false, parsed);
    }

    private record StatusFilter(boolean any, Set<String> values) {
    }
}
