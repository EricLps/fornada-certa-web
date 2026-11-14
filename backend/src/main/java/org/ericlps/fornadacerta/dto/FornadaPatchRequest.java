package org.ericlps.fornadacerta.dto;

import jakarta.validation.constraints.Positive;

public record FornadaPatchRequest(
        @Positive Integer qtdProduzida,
        @Positive Integer pacotesUsados
) {
}
