package org.ericlps.fornadacerta.calculo;

import java.math.BigDecimal;

public record ResultadoSugestao(
        CriterioSugestao criterio,
        int amostras,
        BigDecimal mediaVendida,
        Integer qtdSugerida,
        String aviso
) {

    public static ResultadoSugestao semHistorico() {
        return new ResultadoSugestao(CriterioSugestao.SEM_HISTORICO, 0, null, null,
                "Ainda não há dias fechados suficientes para sugerir uma quantidade.");
    }
}
