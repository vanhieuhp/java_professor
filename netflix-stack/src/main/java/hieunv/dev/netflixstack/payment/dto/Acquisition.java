package hieunv.dev.netflixstack.payment.dto;

import hieunv.dev.netflixstack.common.RecoveryPoint;
import hieunv.dev.netflixstack.payment.dto.response.StoredResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Acquisition {

    private Long recordId;
    private RecoveryPoint recoveryPoint;
    private StoredResponse replay;

    public boolean isReplay() {
        return replay != null;
    }
}
