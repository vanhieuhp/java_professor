package dev.hieunv.grpcclient.service;

import com.epay.services.wallet.enterprise_client_api.grpc.v1.wallet.AccountGrpcServiceGrpc;
import com.epay.services.wallet.enterprise_client_api.grpc.v1.wallet.GetAccountInfoRequest;
import common.v1.GetAccountInfoResponse;
import dev.hieunv.grpcclient.dto.WalletAccountDto;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletCoreServiceImpl implements WalletCoreService {

    @GrpcClient("wallet-core-grpc-server")
    AccountGrpcServiceGrpc.AccountGrpcServiceBlockingStub blockingStub;

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Cacheable(value = "walletAccountInfo", key = "#accountNumber", unless = "#result == null")
    @Retryable(
            value = {StatusRuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public WalletAccountDto getWalletAccountInfo(String accountNumber) {
        final String methodName = "getWalletAccountInfo";
        final Instant startTime = Instant.now();

        log.info("Starting {} for account: {}", methodName, maskAccountNumber(accountNumber));

        try {
            GetAccountInfoRequest request = GetAccountInfoRequest.newBuilder()
                    .setAccountNumber(accountNumber)
                    .build();

            log.debug("Sending gRPC request for account: {}", maskAccountNumber(accountNumber));

            GetAccountInfoResponse grpcResponse = blockingStub
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .getAccountInfo(request)
                    .getData()
                    .getAccountInfo();

            final Duration duration = Duration.between(startTime, Instant.now());
            log.info("Successfully retrieved wallet account info for account: {} in {}ms",
                    maskAccountNumber(accountNumber), duration.toMillis());

            WalletAccountDto response = new WalletAccountDto();
            BeanUtils.copyProperties(grpcResponse, response);
            return response;

        } catch (StatusRuntimeException e) {
            final Duration duration = Duration.between(startTime, Instant.now());
            log.error("gRPC call failed for account: {} after {}ms. Status: {}, Message: {}",
                    maskAccountNumber(accountNumber), duration.toMillis(), e.getStatus(), e.getMessage());

            throw new WalletServiceException(
                    String.format("Failed to retrieve wallet account info for account: %s", maskAccountNumber(accountNumber)),
                    e
            );
        } catch (Exception e) {
            final Duration duration = Duration.between(startTime, Instant.now());
            log.error("Unexpected error occurred for account: {} after {}ms",
                    maskAccountNumber(accountNumber), duration.toMillis(), e);

            throw new WalletServiceException(
                    String.format("Unexpected error while retrieving wallet account info for account: %s",
                            maskAccountNumber(accountNumber)),
                    e
            );
        }
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return accountNumber.substring(0, 2) + "****" + accountNumber.substring(accountNumber.length() - 2);
    }

    public static class WalletServiceException extends RuntimeException {
        public WalletServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
