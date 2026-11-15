package org.ericlps.fornadacerta.calculo;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class CalculadoraSugestao {

    public static final int SEMANAS_DE_HISTORICO = 4;

    public static final int DIAS_DO_CRITERIO_ALTERNATIVO = 14;

    public static final int MINIMO_DE_AMOSTRAS = 2;

    public static final BigDecimal MARGEM_DE_SEGURANCA = new BigDecimal("0.10");

    public ResultadoSugestao calcular(LocalDate dataAlvo, List<VendaDiaria> historico) {

        List<VendaDiaria> mesmoDiaDaSemana = historico.stream()
                .filter(v -> v.data().getDayOfWeek() == dataAlvo.getDayOfWeek())
                .toList();

        CriterioSugestao criterio;
        List<VendaDiaria> amostras;

        if (mesmoDiaDaSemana.size() >= MINIMO_DE_AMOSTRAS) {
            criterio = CriterioSugestao.MESMO_DIA_DA_SEMANA;
            amostras = mesmoDiaDaSemana;
        } else {
            LocalDate inicio = dataAlvo.minusDays(DIAS_DO_CRITERIO_ALTERNATIVO);
            List<VendaDiaria> recentes = historico.stream()
                    .filter(v -> !v.data().isBefore(inicio) && v.data().isBefore(dataAlvo))
                    .toList();

            if (recentes.size() < MINIMO_DE_AMOSTRAS) {
                return ResultadoSugestao.semHistorico();
            }
            criterio = CriterioSugestao.ULTIMOS_DIAS;
            amostras = recentes;
        }

        List<VendaDiaria> semEsgotar = amostras.stream().filter(v -> !v.esgotou()).toList();

        BigDecimal base;
        String aviso = null;

        if (!semEsgotar.isEmpty()) {
            base = media(semEsgotar);
            int ignorados = amostras.size() - semEsgotar.size();
            if (ignorados > 0) {
                aviso = ignorados + " dia(s) em que o produto esgotou ficaram fora da média.";
            }
        } else {
            int maiorVenda = amostras.stream().mapToInt(VendaDiaria::qtdVendida).max().orElse(0);
            base = BigDecimal.valueOf(maiorVenda);
            aviso = "Esgotou em todos os dias analisados; a procura pode ser maior que a sugestão.";
        }

        int sugerida = base.multiply(BigDecimal.ONE.add(MARGEM_DE_SEGURANCA))
                .setScale(0, RoundingMode.CEILING)
                .intValueExact();

        return new ResultadoSugestao(criterio, amostras.size(), base.setScale(1, RoundingMode.HALF_UP), sugerida, aviso);
    }

    private BigDecimal media(List<VendaDiaria> vendas) {
        int soma = vendas.stream().mapToInt(VendaDiaria::qtdVendida).sum();
        return BigDecimal.valueOf(soma).divide(BigDecimal.valueOf(vendas.size()), 2, RoundingMode.HALF_UP);
    }
}
