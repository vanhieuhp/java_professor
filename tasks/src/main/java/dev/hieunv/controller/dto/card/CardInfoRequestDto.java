package dev.hieunv.controller.dto.card;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.hieunv.controller.dto.account.UserNameDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@ToString(exclude = "cardDetails")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardInfoRequestDto {

    @NotBlank
    private String id;

    @Valid
    private UserNameDto fullName;

    @NotNull
    private String cardDetails;
}
