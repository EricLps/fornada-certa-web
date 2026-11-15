package org.ericlps.fornadacerta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoRequest(@NotBlank String nome,
                             @NotNull @Positive BigDecimal preco,
                             @NotNull OrigemProduto origem,
                             @NotNull @Positive Integer rendimento,
                             @NotEmpty @Valid List<ItemReceitaRequest> itens) {
}
