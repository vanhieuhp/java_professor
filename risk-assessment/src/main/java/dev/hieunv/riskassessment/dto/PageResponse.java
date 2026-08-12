package dev.hieunv.riskassessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Vỏ phân trang chung cho các API báo cáo.
 *
 * <h2>Vì sao không trả thẳng {@code Page} của Spring</h2>
 * {@code PageImpl} tuần tự hóa ra một JSON có {@code pageable}, {@code sort.unsorted},
 * {@code numberOfElements}… — hình dạng của nó là chi tiết nội bộ của Spring Data và đã đổi
 * giữa các phiên bản. Web admin thì phải bám vào một hợp đồng ổn định.
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;

    /** Bắt đầu từ 0. */
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    /** Rỗng = web hiển thị bảng trống kèm dòng chữ "Không có dữ liệu". */
    private boolean empty;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return PageResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .empty(page.isEmpty())
                .build();
    }
}
