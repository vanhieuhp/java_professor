package dev.hieunv.service;

import dev.hieunv.domain.dto.card.CardInfoRequestDto;
import dev.hieunv.domain.dto.card.CardInfoResponseDto;
import lombok.NonNull;

public interface CardService {

    void createCard(@NonNull CardInfoRequestDto request);

    CardInfoResponseDto getCard(@NonNull String id);
}
