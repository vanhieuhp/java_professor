package dev.hieunv.totp_bankos.mapper;

import dev.hieunv.totp_bankos.domain.AccountWallet;
import dev.hieunv.totp_bankos.dto.request.CreateWalletRequest;
import dev.hieunv.totp_bankos.dto.response.WalletSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface AccountWalletMapper {

    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "balance",   ignore = true)
    @Mapping(target = "isActive",  constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AccountWallet toEntity(CreateWalletRequest request);

    WalletSummaryResponse toSummaryResponse(AccountWallet wallet);

    List<WalletSummaryResponse> toSummaryResponseList(List<AccountWallet> wallets);
}