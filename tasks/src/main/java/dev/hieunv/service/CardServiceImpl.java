package dev.hieunv.service;

import dev.hieunv.controller.CardInfoConverter;
import dev.hieunv.domain.dto.card.CardInfoRequestDto;
import dev.hieunv.domain.dto.card.CardInfoResponseDto;
import dev.hieunv.domain.mappers.CardInfoEntityMapper;
import dev.hieunv.domain.model.CardInfo;
import dev.hieunv.repository.CardInfoRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class CardServiceImpl implements CardService {

    private final CardInfoRepository cardInfoRepository;
    private final CardInfoEntityMapper mapper;
    private final CardInfoConverter cardInfoConverter;

    @Override
    public void createCard(@NonNull CardInfoRequestDto request) {
        CardInfo cardInfo = cardInfoConverter.toModel(request);
        if (cardInfoRepository.existsById(cardInfo.getId())) {
            return;
        }
        cardInfoRepository.save(mapper.toEntity(cardInfo));
    }

    @Override
    public CardInfoResponseDto getCard(@NonNull String id) {
        CardInfo cardInfo = cardInfoRepository.findById(id)
                .map(mapper::toCardInfoModel)
                .orElseThrow(() -> new IllegalArgumentException(String.format("Card not found for id %s", id)));

        return cardInfoConverter.toDto(cardInfo);
    }
}
