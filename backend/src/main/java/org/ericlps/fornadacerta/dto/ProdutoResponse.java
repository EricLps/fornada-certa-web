package org.ericlps.fornadacerta.dto;

import org.ericlps.fornadacerta.entity.enums.OrigemProduto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoResponse(UUID id,
                              String nome,
                              BigDecimal preco,
                              BigDecimal custo,
                              boolean ativo,
                              OrigemProduto origem,
                              Integer rendimento,
                              Integer fatias) {
}
