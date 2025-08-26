package dev.hieunv.controller;

import dev.hieunv.domain.dto.card.CardInfoRequestDto;
import dev.hieunv.domain.dto.card.CardInfoResponseDto;
import dev.hieunv.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cards")
public class CardController {

    private final CardService cardService;

    @PostMapping
    @ResponseStatus(CREATED)
    public void createCard(@Valid @RequestBody CardInfoRequestDto cardInfoRequest) {
        cardService.createCard(cardInfoRequest);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CardInfoResponseDto> getCard(@PathVariable("id") String id) {
        return ResponseEntity.ok(cardService.getCard(id));
    }
}
