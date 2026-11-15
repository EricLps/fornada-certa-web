package org.ericlps.fornadacerta.calculo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraCustoTest {

    private final CalculadoraCusto calculadora = new CalculadoraCusto();

    private static CalculadoraCusto.Componente componente(String quantidade, String custoUnitario) {
        return new CalculadoraCusto.Componente(new BigDecimal(quantidade), new BigDecimal(custoUnitario));
    }

    @Test
    @DisplayName("receita própria: 1 kg de farinha (5,00) + 2 ovos (0,80) = 6,60; rende 12 -> 0,55 por item")
    void receitaPropria() {
        BigDecimal custo = calculadora.custoUnitario(List.of(
                componente("1", "5.00"),
                componente("2", "0.80")), 12);

        assertThat(custo).isEqualByComparingTo("0.55");
    }

    @Test
    @DisplayName("produto de fornecedor: 1 saco de 48,00 que rende 80 pães -> 0,60 por pão")
    void sacoDeFornecedor() {
        assertThat(calculadora.custoUnitario(List.of(componente("1", "48.00")), 80)).isEqualByComparingTo("0.60");
    }

    @Test
    @DisplayName("quantidades fracionadas: 0,250 kg a 5,49 = 1,3725; rende 10 -> 0,14 (arredonda para o centavo)")
    void quantidadeFracionada() {
        assertThat(calculadora.custoUnitario(List.of(componente("0.250", "5.49")), 10)).isEqualByComparingTo("0.14");
    }

    @Test
    @DisplayName("arredondamento meio para cima: 1,00/3 = 0,33 e 2,00/3 = 0,67")
    void arredondamento() {
        assertThat(calculadora.custoUnitario(List.of(componente("1", "1.00")), 3)).isEqualByComparingTo("0.33");
        assertThat(calculadora.custoUnitario(List.of(componente("1", "2.00")), 3)).isEqualByComparingTo("0.67");
    }

    @Test
    @DisplayName("o resultado sempre tem 2 casas decimais, mesmo quando a conta é exata")
    void duasCasas() {
        assertThat(calculadora.custoUnitario(List.of(componente("2", "3.00")), 2).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("sem componentes: custo zero")
    void semComponentes() {
        assertThat(calculadora.custoUnitario(List.of(), 10)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("rendimento zero ou negativo: IllegalArgumentException")
    void rendimentoInvalido() {
        assertThatThrownBy(() -> calculadora.custoUnitario(List.of(componente("1", "1.00")), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculadora.custoUnitario(List.of(componente("1", "1.00")), -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("custo do componente = quantidade x custo unitário")
    void custoDoComponente() {
        assertThat(componente("0.5", "8.00").custo()).isEqualByComparingTo("4.00");
    }
}
