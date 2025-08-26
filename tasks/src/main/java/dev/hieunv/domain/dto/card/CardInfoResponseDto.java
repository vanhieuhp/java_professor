package dev.hieunv.domain.dto.card;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.hieunv.domain.dto.account.UserNameDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;


@Builder
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardInfoResponseDto {
    @NotBlank
    private String id;
    @Valid
    private UserNameDto fullName;
    @Valid
    private CardDetailsResponseDto cardDetails;
}