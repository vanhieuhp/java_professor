package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.dto.request.CreateCifRequest;
import dev.hieunv.totp_bankos.dto.response.CifResponse;

import java.util.List;

public interface CifService {
    CifResponse create(CreateCifRequest request);

    CifResponse getById(Long id);

    CifResponse getByCode(String code);

    List<CifResponse> listAll();

    CifResponse deactivate(Long id);
}
