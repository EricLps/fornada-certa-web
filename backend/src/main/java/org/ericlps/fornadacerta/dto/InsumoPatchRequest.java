package org.ericlps.fornadacerta.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;

import java.math.BigDecimal;

public record InsumoPatchRequest(
        @Size(min = 1, max = 100) String nome,
        UnidadeMedida unidade,
        @Positive BigDecimal custoUnitario,
        @Positive Integer itensPorSaco,
        @Positive Integer fornadasPorBotijao
) {
}
