package dev.hieunv.bankos.controller;

import dev.hieunv.bankos.model.OutboxEvent;
import dev.hieunv.bankos.repository.OutboxEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
@Tag(name = "Outbox", description = "Outbox event monitoring")
public class OutboxController {

    private final OutboxEventRepository outboxEventRepository;

    @GetMapping("/pending")
    @Operation(summary = "View pending outbox events")
    public List<OutboxEvent> getPending() {
        return outboxEventRepository.findPendingEvents();
    }

    @GetMapping
    @Operation(summary = "View all outbox events")
    public List<OutboxEvent> getAll() {
        return outboxEventRepository.findAll();
    }
}
