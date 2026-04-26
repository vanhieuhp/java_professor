package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.Cif;
import dev.hieunv.totp_bankos.dto.request.CreateCifRequest;
import dev.hieunv.totp_bankos.dto.response.CifResponse;
import dev.hieunv.totp_bankos.exception.BadRequestException;
import dev.hieunv.totp_bankos.exception.NotFoundException;
import dev.hieunv.totp_bankos.mapper.CifMapper;
import dev.hieunv.totp_bankos.repository.CifRepository;
import dev.hieunv.totp_bankos.service.CifService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CifServiceImpl implements CifService {

    private final CifRepository cifRepository;
    private final CifMapper     cifMapper;

    @Override
    @Transactional
    public CifResponse create(CreateCifRequest request) {
        if (cifRepository.existsByCode(request.getCode())) {
            throw new BadRequestException("CIF code already exists: " + request.getCode());
        }
        Cif saved = cifRepository.save(cifMapper.toEntity(request));
        return cifMapper.toResponse(saved);
    }

    @Override
    public CifResponse getById(Long id) {
        return cifMapper.toResponse(findById(id));
    }

    @Override
    public CifResponse getByCode(String code) {
        Cif cif = cifRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("CIF not found: " + code));
        return cifMapper.toResponse(cif);
    }

    @Override
    public List<CifResponse> listAll() {
        return cifRepository.findAll()
                .stream()
                .map(cifMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CifResponse deactivate(Long id) {
        Cif cif = findById(id);
        cif.setActive(false);
        cif.setUpdatedAt(LocalDateTime.now());
        return cifMapper.toResponse(cifRepository.save(cif));
    }

    // ── private ───────────────────────────────────────────────

    private Cif findById(Long id) {
        return cifRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CIF not found"));
    }
}