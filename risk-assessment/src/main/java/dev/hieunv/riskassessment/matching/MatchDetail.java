package dev.hieunv.riskassessment.matching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

/**
 * Bằng chứng của một lần trùng: trùng với BẢN GHI NÀO, và trùng những TRƯỜNG NÀO.
 * <p>
 * Hai thông tin này không phải để debug — chúng là thứ phải trả lời được khi cơ quan
 * thanh tra hỏi "tại sao khóa ví khách hàng này?". Một kết quả rủi ro không chỉ ra được
 * bản ghi cụ thể thì không giải trình được.
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchDetail {

    /** {@code watchlist_entry.id} của bản ghi bị trùng. */
    private Long entryId;

    /** Các trường đã trùng với đúng bản ghi đó — không gộp từ nhiều bản ghi. */
    private Set<MatchField> matchedFields;
}
