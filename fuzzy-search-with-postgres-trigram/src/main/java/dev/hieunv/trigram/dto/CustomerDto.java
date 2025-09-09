package dev.hieunv.trigram.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class CustomerDto {

    private final UUID id;
    private final String contractNumber;
    private final String address;
    private final ContactDetailsDto contactDetails;
}
