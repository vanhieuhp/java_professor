package dev.hieunv.domain.dto.card;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;


@Builder
@Getter
@ToString(exclude = {"cvv"})
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardDetailsResponseDto {
    @NotBlank
    private String pan;
    @NotBlank
    private String cvv;
}