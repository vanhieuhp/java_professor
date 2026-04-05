package dev.hieunv.two_databases.controller;

import dev.hieunv.two_databases.domain.primary.TransferSaga;
import dev.hieunv.two_databases.dto.TransferRequest;
import dev.hieunv.two_databases.service.InterBankTransferService;
import dev.hieunv.two_databases.service.SagaOrchestrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    @Autowired
    private InterBankTransferService interBankTransferService;

    @Autowired
    private SagaOrchestrationService sagaOrchestrationService;

    /**
     * Demonstrates the broken dual-write problem.
     * Use simulateCrash=true to see money debited but never credited.
     */
    @PostMapping("/broken")
    public ResponseEntity<Map<String, String>> transferBroken(
            @RequestBody TransferRequest request,
            @RequestParam(defaultValue = "false") boolean simulateCrash) {

        interBankTransferService.transferBroken(
                request.fromAccountId(), request.toAccountId(),
                request.amount(), simulateCrash);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "message", "Dual-write transfer done (no atomicity guarantee)"));
    }

    /**
     * Demonstrates the outbox pattern — debit + outbox event committed atomically.
     * A background relay (@Scheduled) picks up the event and credits the secondary DB.
     */
    @PostMapping("/outbox")
    public ResponseEntity<Map<String, String>> transferWithOutbox(
            @RequestBody TransferRequest request) {

        String transferId = interBankTransferService.transferWithOutbox(
                request.fromAccountId(), request.toAccountId(), request.amount());

        return ResponseEntity.ok(Map.of(
                "status", "accepted",
                "transferId", transferId,
                "message", "Debit + outbox event committed atomically; credit will follow"));
    }

    /**
     * Starts a Saga-based transfer.
     * Use simulateCreditFailure=true to trigger compensation (refund).
     */
    @PostMapping("/saga")
    public ResponseEntity<Map<String, Object>> sagaTransfer(
            @RequestBody TransferRequest request,
            @RequestParam(defaultValue = "false") boolean simulateCreditFailure) {

        TransferSaga saga = sagaOrchestrationService.startTransfer(
                request.fromAccountId(), request.toAccountId(), request.amount());

        return ResponseEntity.ok(Map.of(
                "sagaId", saga.getId().toString(),
                "status", saga.getStatus().name(),
                "simulateCreditFailure", simulateCreditFailure,
                "message", "Saga started; outbox relay will process the credit step"));
    }
}
