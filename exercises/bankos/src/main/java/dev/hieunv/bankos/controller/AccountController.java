package dev.hieunv.bankos.controller;

import dev.hieunv.bankos.dto.AccountResponse;
import dev.hieunv.bankos.dto.BalanceResponse;
import dev.hieunv.bankos.dto.CreateAccountRequest;
import dev.hieunv.bankos.dto.DepositRequest;
import dev.hieunv.bankos.dto.WithdrawRequest;
import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "BankOS account operations")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "List all accounts")
    public List<AccountResponse> listAll() {
        return accountService.findAll();
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get account balance (safe READ_COMMITTED)")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long id) {
        BigDecimal balance = accountService.readBalanceSafe(id);
        return ResponseEntity.ok(new BalanceResponse(id, balance));
    }

    @PostMapping
    @Operation(summary = "Create a new account")
    public ResponseEntity<AccountResponse> createAccount(
            @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(request);
        return ResponseEntity.status(201).body(AccountResponse.from(account));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw from account (pessimistic lock)")
    public ResponseEntity<BalanceResponse> withdraw(
            @PathVariable Long id,
            @RequestBody WithdrawRequest request) {
        try {
            accountService.withdrawSafe(id, request.getAmount());
            BigDecimal balance = accountService.readBalanceSafe(id);
            return ResponseEntity.ok(new BalanceResponse(id, balance));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/deposit")
    @Operation(summary = "Deposit into account")
    public ResponseEntity<BalanceResponse> deposit(
            @PathVariable Long id,
            @RequestBody DepositRequest request) {
        accountService.deposit(id, request.getAmount());
        BigDecimal balance = accountService.readBalanceSafe(id);
        return ResponseEntity.ok(new BalanceResponse(id, balance));
    }
}
