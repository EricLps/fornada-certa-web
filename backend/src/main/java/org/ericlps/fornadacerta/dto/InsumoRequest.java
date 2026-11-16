package org.ericlps.fornadacerta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;

import java.math.BigDecimal;

public record InsumoRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull UnidadeMedida unidade,
        @NotNull @Positive BigDecimal custoUnitario,
        @Positive Integer itensPorSaco,
        @Positive Integer fornadasPorBotijao
) {
}
