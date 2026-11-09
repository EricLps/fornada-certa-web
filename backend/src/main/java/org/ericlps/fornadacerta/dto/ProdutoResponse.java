package org.ericlps.fornadacerta.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoResponse(UUID id,
                              String nome,
                              BigDecimal valor,
                              boolean ativo) {
}
