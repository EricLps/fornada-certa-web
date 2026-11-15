package org.ericlps.fornadacerta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemReceitaRequest(
        @NotNull UUID insumoId,
        @NotNull @Positive BigDecimal quantidade
) {
}
