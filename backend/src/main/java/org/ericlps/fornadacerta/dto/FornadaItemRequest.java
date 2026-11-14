package org.ericlps.fornadacerta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record FornadaItemRequest(
        @NotNull UUID produtoId,
        @NotNull @Positive Integer qtdProduzida,
        @Positive Integer pacotesUsados) {
}

