package dev.hieunv.grpcclient.service;

import dev.hieunv.grpcclient.dto.WalletAccountDto;

public interface WalletCoreService {

    WalletAccountDto getWalletAccountInfo(String accountNumber);

}
