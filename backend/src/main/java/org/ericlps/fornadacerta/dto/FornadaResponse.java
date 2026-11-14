package org.ericlps.fornadacerta.dto;

import org.ericlps.fornadacerta.entity.enums.Turno;

import java.time.LocalDate;
import java.util.UUID;

public record FornadaResponse(
        UUID id,
        UUID produtoId,
        String produtoNome,
        LocalDate data,
        Turno turno,
        Integer qtdProduzida,
        Integer pacotesUsados
        ) {
}
