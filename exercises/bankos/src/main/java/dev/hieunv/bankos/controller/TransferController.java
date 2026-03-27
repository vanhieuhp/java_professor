package dev.hieunv.bankos.controller;

import dev.hieunv.bankos.dto.TransferRequest;
import dev.hieunv.bankos.dto.TransferResult;
import dev.hieunv.bankos.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers", description = "BankOS fund transfer operations")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @Operation(summary = "Transfer funds between accounts (deadlock-safe)")
    public ResponseEntity<TransferResult> transfer(
            @RequestBody TransferRequest request) {
        try {
            TransferResult result = transferService.transferSafe(
                    request.getFromAccountId(),
                    request.getToAccountId(),
                    request.getAmount()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableEntity().build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().build();
        }
    }
}
