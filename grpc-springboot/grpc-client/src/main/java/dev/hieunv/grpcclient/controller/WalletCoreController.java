package dev.hieunv.grpcclient.controller;

import dev.hieunv.grpcclient.dto.WalletAccountDto;
import dev.hieunv.grpcclient.service.WalletCoreService;
import dev.hieunv.grpcclient.service.WalletCoreServiceImpl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@Slf4j
@RestController
@RequestMapping("/wallet")
@Validated
public class WalletCoreController {

    private final WalletCoreService walletCoreService;

    @GetMapping("/{accountNumber}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<WalletAccountDto> getWalletAccountInfo(
            @PathVariable 
            @NotBlank(message = "Account number cannot be blank")
            @Pattern(regexp = "^[0-9]{10,20}$", message = "Account number must be 10-20 digits")
            String accountNumber) {
        
        log.info("Received request for wallet account info: {}", maskAccountNumber(accountNumber));
        
        try {
            WalletAccountDto accountInfo = walletCoreService.getWalletAccountInfo(accountNumber);
            log.info("Successfully retrieved wallet account info for account: {}", maskAccountNumber(accountNumber));
            return ResponseEntity.ok(accountInfo);
            
        } catch (WalletCoreServiceImpl.WalletServiceException e) {
            log.error("Failed to retrieve wallet account info for account: {} - {}", 
                    maskAccountNumber(accountNumber), e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid account number provided: {} - {}", maskAccountNumber(accountNumber), e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error retrieving wallet account info for account: {}", 
                    maskAccountNumber(accountNumber), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return accountNumber.substring(0, 2) + "****" + accountNumber.substring(accountNumber.length() - 2);
    }
}
