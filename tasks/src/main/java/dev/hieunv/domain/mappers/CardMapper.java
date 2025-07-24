package dev.hieunv.domain.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hieunv.domain.model.UserName;
import dev.hieunv.controller.dto.account.UserNameDto;
import dev.hieunv.domain.model.CardDetails;
import dev.hieunv.domain.model.CardInfo;
import dev.hieunv.controller.dto.card.CardInfoRequestDto;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static java.util.Optional.ofNullable;

@RequiredArgsConstructor
@Component
public class CardMapper {

    private final ObjectMapper objectMapper;

    public CardInfo toModel(@NonNull CardInfoRequestDto dto) {
        final UserNameDto userName = dto.getFullName();
        return CardInfo.builder()
                .id(dto.getId())
                .userName(UserName.builder()
                        .firstName(ofNullable(userName).map(UserNameDto::getFirstName).orElse(null))
                        .lastName(ofNullable(userName).map(UserNameDto::getLastName).orElse(null))
                        .build())
                .cardDetails(getDecryptedCardDetails(dto.getCardDetails()))
                .build();
    }

    private CardDetails getDecryptedCardDetails(@NonNull String cardDetails) {
        try {
            return objectMapper.readValue(cardDetails, CardDetails.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Card details string cannot be transformed to Json object", e);
        }
    }
}
