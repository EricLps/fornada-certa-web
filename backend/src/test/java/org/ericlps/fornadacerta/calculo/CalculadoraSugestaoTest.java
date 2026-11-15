package org.ericlps.fornadacerta.calculo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da regra de sugestão. O dia alvo é sempre a segunda-feira 16/11/2026;
 * as segundas anteriores são 09/11, 02/11, 26/10 e 19/10.
 */
class CalculadoraSugestaoTest {

    private final CalculadoraSugestao calculadora = new CalculadoraSugestao();

    private static final LocalDate SEGUNDA_ALVO = LocalDate.of(2026, 11, 16);

    /** A segunda-feira de N semanas antes do alvo. */
    private static LocalDate segundaAnterior(int semanas) {
        return SEGUNDA_ALVO.minusWeeks(semanas);
    }

    private static VendaDiaria vendeu(LocalDate data, int qtd) {
        return new VendaDiaria(data, qtd, false);
    }

    private static VendaDiaria esgotou(LocalDate data, int qtd) {
        return new VendaDiaria(data, qtd, true);
    }

    /** Vendas nas segundas anteriores, da mais recente para a mais antiga. */
    private static List<VendaDiaria> segundas(int... vendas) {
        List<VendaDiaria> lista = new ArrayList<>();
        for (int i = 0; i < vendas.length; i++) {
            lista.add(vendeu(segundaAnterior(i + 1), vendas[i]));
        }
        return lista;
    }

    @Test
    @DisplayName("confere que o dia alvo dos testes é mesmo uma segunda-feira")
    void diaAlvoEhSegunda() {
        assertThat(SEGUNDA_ALVO.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    // =====================================================================
    @Nested
    @DisplayName("média do mesmo dia da semana")
    class MesmoDiaDaSemana {

        @Test
        @DisplayName("4 segundas com 100, 110, 90 e 100: média 100, sugestão 110 (+10%)")
        void mediaSimples() {
            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, segundas(100, 110, 90, 100));

            assertThat(r.criterio()).isEqualTo(CriterioSugestao.MESMO_DIA_DA_SEMANA);
            assertThat(r.amostras()).isEqualTo(4);
            assertThat(r.mediaVendida()).isEqualByComparingTo("100.0");
            assertThat(r.qtdSugerida()).isEqualTo(110);
            assertThat(r.aviso()).isNull();
        }

        @Test
        @DisplayName("outros dias da semana não entram na média")
        void ignoraOutrosDias() {
            List<VendaDiaria> historico = new ArrayList<>(segundas(100, 100));
            historico.add(vendeu(SEGUNDA_ALVO.minusDays(2), 500)); // sábado
            historico.add(vendeu(SEGUNDA_ALVO.minusDays(6), 10));  // terça

            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, historico);

            assertThat(r.amostras()).isEqualTo(2);
            assertThat(r.qtdSugerida()).isEqualTo(110);
        }

        @Test
        @DisplayName("exatamente 2 segundas já bastam (o mínimo de amostras)")
        void minimoDeAmostras() {
            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, segundas(80, 120));

            assertThat(r.criterio()).isEqualTo(CriterioSugestao.MESMO_DIA_DA_SEMANA);
            assertThat(r.mediaVendida()).isEqualByComparingTo("100.0");
            assertThat(r.qtdSugerida()).isEqualTo(110);
        }

        @Test
        @DisplayName("arredonda sempre para cima: média 100,5 vira 110,55 e a sugestão é 111")
        void arredondaParaCima() {
            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, segundas(100, 101));

            assertThat(r.mediaVendida()).isEqualByComparingTo("100.5");
            assertThat(r.qtdSugerida()).isEqualTo(111);
        }

        @Test
        @DisplayName("média com dízima (301/3 = 100,33): sugestão 111 e média exibida com uma casa")
        void mediaComDizima() {
            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, segundas(100, 100, 101));

            assertThat(r.mediaVendida()).isEqualByComparingTo("100.3");
            assertThat(r.qtdSugerida()).isEqualTo(111);
        }

        @Test
        @DisplayName("produto que não vendeu nada: sugestão zero")
        void vendaZero() {
            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, segundas(0, 0));

            assertThat(r.qtdSugerida()).isZero();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("dias em que o produto esgotou")
    class Esgotou {

        @Test
        @DisplayName("dia que esgotou fica fora da média quando há outros dias")
        void esgotadoIgnorado() {
            List<VendaDiaria> historico = new ArrayList<>(segundas(100, 110, 90));
            historico.add(esgotou(segundaAnterior(4), 120));

            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, historico);

            assertThat(r.amostras()).isEqualTo(4);
            assertThat(r.mediaVendida()).isEqualByComparingTo("100.0");
            assertThat(r.qtdSugerida()).isEqualTo(110);
            assertThat(r.aviso()).contains("1 dia(s)");
        }

        @Test
        @DisplayName("todos esgotaram: usa a maior venda (95) e avisa que a procura pode ser maior")
        void todosEsgotaram() {
            List<VendaDiaria> historico = List.of(
                    esgotou(segundaAnterior(1), 80),
                    esgotou(segundaAnterior(2), 95));

            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, historico);

            assertThat(r.mediaVendida()).isEqualByComparingTo("95.0");
            assertThat(r.qtdSugerida()).isEqualTo(105); // 95 x 1,10 = 104,5 -> 105
            assertThat(r.aviso()).contains("Esgotou em todos");
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("pouco histórico")
    class PoucoHistorico {

        @Test
        @DisplayName("só 1 segunda: usa todos os dias das últimas 2 semanas")
        void usaUltimosDias() {
            List<VendaDiaria> historico = List.of(
                    vendeu(segundaAnterior(1), 100),           // 09/11 (segunda)
                    vendeu(SEGUNDA_ALVO.minusDays(6), 80),     // 10/11
                    vendeu(SEGUNDA_ALVO.minusDays(4), 90),     // 12/11
                    vendeu(SEGUNDA_ALVO.minusDays(27), 500));  // 20/10, fora das 2 semanas

            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, historico);

            assertThat(r.criterio()).isEqualTo(CriterioSugestao.ULTIMOS_DIAS);
            assertThat(r.amostras()).isEqualTo(3);
            assertThat(r.mediaVendida()).isEqualByComparingTo("90.0");
            assertThat(r.qtdSugerida()).isEqualTo(99);
        }

        @Test
        @DisplayName("histórico vazio: SEM_HISTORICO, sem número e com aviso")
        void semHistorico() {
            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, List.of());

            assertThat(r.criterio()).isEqualTo(CriterioSugestao.SEM_HISTORICO);
            assertThat(r.amostras()).isZero();
            assertThat(r.mediaVendida()).isNull();
            assertThat(r.qtdSugerida()).isNull();
            assertThat(r.aviso()).isNotBlank();
        }

        @Test
        @DisplayName("só 1 dia nas últimas 2 semanas: SEM_HISTORICO")
        void umDiaSoNaoBasta() {
            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, List.of(vendeu(SEGUNDA_ALVO.minusDays(3), 100)));

            assertThat(r.criterio()).isEqualTo(CriterioSugestao.SEM_HISTORICO);
        }

        @Test
        @DisplayName("dias antigos (fora das 2 semanas) não salvam o critério alternativo")
        void diasAntigosNaoContam() {
            List<VendaDiaria> historico = List.of(
                    vendeu(SEGUNDA_ALVO.minusDays(20), 100),
                    vendeu(SEGUNDA_ALVO.minusDays(25), 100));

            ResultadoSugestao r = calculadora.calcular(SEGUNDA_ALVO, historico);

            assertThat(r.criterio()).isEqualTo(CriterioSugestao.SEM_HISTORICO);
        }
    }
}
