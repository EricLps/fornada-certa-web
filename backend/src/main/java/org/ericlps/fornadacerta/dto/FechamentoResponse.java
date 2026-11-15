package org.ericlps.fornadacerta.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record FechamentoResponse(
        UUID id,
        UUID produtoId,
        String produtoNome,
        LocalDate data,
        Integer qtdSobra,
        Integer qtdDoada,
        Integer qtdReaproveitada,
        Integer qtdDescartada,
        BigDecimal custoUnitario,
        BigDecimal custoDescarte,
        LocalTime horarioEsgotou
) {
}
