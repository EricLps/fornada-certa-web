package org.ericlps.fornadacerta.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProdutoPatchRequest(
        @Size(min = 1, max = 100) String nome,
        @Positive BigDecimal preco,
        Boolean status
        ) {
}
