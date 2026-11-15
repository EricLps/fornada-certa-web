package org.ericlps.fornadacerta.dto;

import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemReceitaResponse(
        UUID insumoId,
        String insumoNome,
        UnidadeMedida unidade,
        BigDecimal quantidade,
        BigDecimal custoUnitario,
        BigDecimal custoParcial
) {
}
