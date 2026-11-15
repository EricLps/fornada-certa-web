package org.ericlps.fornadacerta.dto;

import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;

import java.math.BigDecimal;
import java.util.UUID;

public record InsumoResponse(
        UUID id,
        String nome,
        UnidadeMedida unidade,
        BigDecimal custoUnitario
) {
}
