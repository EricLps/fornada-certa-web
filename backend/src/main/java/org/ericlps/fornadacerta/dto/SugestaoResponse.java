package org.ericlps.fornadacerta.dto;

import org.ericlps.fornadacerta.calculo.CriterioSugestao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SugestaoResponse(
        UUID produtoId,
        String produtoNome,
        LocalDate data,
        String diaDaSemana,
        CriterioSugestao criterio,
        Integer amostras,
        BigDecimal mediaVendida,
        Integer qtdSugerida,
        String aviso
) {
}
