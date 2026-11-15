package org.ericlps.fornadacerta.service;

import org.ericlps.fornadacerta.dto.RelatorioResponse;
import org.ericlps.fornadacerta.entity.Fechamento;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.Turno;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.repository.FechamentoRepository;
import org.ericlps.fornadacerta.repository.FornadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

    private static final LocalDate SEG = LocalDate.of(2026, 11, 9);
    private static final LocalDate TER = LocalDate.of(2026, 11, 10);
    private static final LocalDate QUA = LocalDate.of(2026, 11, 11);

    @Mock
    private FornadaRepository fornadaRepository;

    @Mock
    private FechamentoRepository fechamentoRepository;

    private RelatorioService relatorioService;

    private Produto pao;
    private Produto sonho;

    @BeforeEach
    void setUp() {
        relatorioService = new RelatorioService(fornadaRepository, fechamentoRepository);
        pao = produto("Pão francês", "0.35");
        sonho = produto("Sonho", "2.00");
    }

    private Produto produto(String nome, String custo) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome(nome);
        produto.setPreco(new BigDecimal("1.00"));
        produto.setCusto(new BigDecimal(custo));
        return produto;
    }

    private Fornada fornada(Produto produto, LocalDate data, Turno turno, int qtd) {
        Fornada fornada = new Fornada();
        fornada.setProduto(produto);
        fornada.setData(data);
        fornada.setTurno(turno);
        fornada.setQtdProduzida(qtd);
        return fornada;
    }

    private Fechamento fechamento(Produto produto, LocalDate data, int sobra, int doada, int reaproveitada, String custoUnitario) {
        Fechamento fechamento = new Fechamento();
        fechamento.setProduto(produto);
        fechamento.setData(data);
        fechamento.setQtdSobra(sobra);
        fechamento.setQtdDoada(doada);
        fechamento.setQtdReaproveitada(reaproveitada);
        fechamento.setCustoUnitario(new BigDecimal(custoUnitario));
        fechamento.setHorarioEsgotou(sobra == 0 ? LocalTime.of(15, 0) : null);
        return fechamento;
    }

    /*
     * Cenário usado nos testes (segunda a quarta):
     *  - Pão francês, segunda: 200 manhã + 100 tarde = 300; sobra 30 (10 doados, 5 reaproveitados) -> descarte 15
     *    O custo do fechamento é 0,40 (o da época), diferente do custo atual do produto (0,35).
     *  - Pão francês, terça: 250; esgotou (sobra 0) -> descarte 0
     *  - Sonho, segunda: 40; sobra 10, nada doado -> descarte 10 a 2,00 = 20,00
     *  - Sonho, quarta: 40 assados, mas o dia NÃO foi fechado -> não entra no relatório
     */
    private RelatorioResponse gerarCenario() {
        when(fornadaRepository.findByDataBetween(SEG, QUA)).thenReturn(List.of(
                fornada(pao, SEG, Turno.MANHA, 200),
                fornada(pao, SEG, Turno.TARDE, 100),
                fornada(pao, TER, Turno.MANHA, 250),
                fornada(sonho, SEG, Turno.TARDE, 40),
                fornada(sonho, QUA, Turno.TARDE, 40)));
        when(fechamentoRepository.findByDataBetween(SEG, QUA)).thenReturn(List.of(
                fechamento(pao, SEG, 30, 10, 5, "0.40"),
                fechamento(pao, TER, 0, 0, 0, "0.40"),
                fechamento(sonho, SEG, 10, 0, 0, "2.00")));

        return relatorioService.gerar(SEG, QUA);
    }

    // =====================================================================
    @Nested
    @DisplayName("totais do período")
    class TotaisDoPeriodo {

        @Test
        @DisplayName("soma só os dias fechados: produzido, vendido, sobra, destinos e descarte")
        void totais() {
            RelatorioResponse.Totais t = gerarCenario().total();

            assertThat(t.produzido()).isEqualTo(590);      // 300 + 250 + 40 (a quarta do sonho não conta)
            assertThat(t.sobra()).isEqualTo(40);
            assertThat(t.vendido()).isEqualTo(550);
            assertThat(t.doado()).isEqualTo(10);
            assertThat(t.reaproveitado()).isEqualTo(5);
            assertThat(t.descartado()).isEqualTo(25);
        }

        @Test
        @DisplayName("custo do desperdício usa o custo da época gravado no fechamento")
        void custoDaEpoca() {
            RelatorioResponse.Totais t = gerarCenario().total();

            assertThat(t.custoDescarte()).isEqualByComparingTo("26.00"); // 15 x 0,40 + 10 x 2,00
        }

        @Test
        @DisplayName("taxa de desperdício = descartado / produzido, com 4 casas (25/590 = 0,0424)")
        void taxa() {
            assertThat(gerarCenario().total().taxaDesperdicio()).isEqualByComparingTo("0.0424");
        }

        @Test
        @DisplayName("conta os dias distintos que tiveram fechamento")
        void diasFechados() {
            assertThat(gerarCenario().total().diasFechados()).isEqualTo(2);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("por produto")
    class PorProduto {

        @Test
        @DisplayName("ordena do maior custo de desperdício para o menor")
        void ordenadoPorCusto() {
            assertThat(gerarCenario().porProduto())
                    .extracting(RelatorioResponse.PorProduto::produtoNome)
                    .containsExactly("Sonho", "Pão francês");
        }

        @Test
        @DisplayName("cada produto tem os próprios totais e a própria taxa")
        void totaisPorProduto() {
            List<RelatorioResponse.PorProduto> produtos = gerarCenario().porProduto();

            RelatorioResponse.PorProduto sonhoResumo = produtos.get(0);
            assertThat(sonhoResumo.produtoId()).isEqualTo(sonho.getId());
            assertThat(sonhoResumo.produzido()).isEqualTo(40);
            assertThat(sonhoResumo.vendido()).isEqualTo(30);
            assertThat(sonhoResumo.descartado()).isEqualTo(10);
            assertThat(sonhoResumo.custoDescarte()).isEqualByComparingTo("20.00");
            assertThat(sonhoResumo.taxaDesperdicio()).isEqualByComparingTo("0.25");

            RelatorioResponse.PorProduto paoResumo = produtos.get(1);
            assertThat(paoResumo.produzido()).isEqualTo(550);
            assertThat(paoResumo.vendido()).isEqualTo(520);
            assertThat(paoResumo.descartado()).isEqualTo(15);
            assertThat(paoResumo.custoDescarte()).isEqualByComparingTo("6.00");
            assertThat(paoResumo.taxaDesperdicio()).isEqualByComparingTo("0.0273"); // 15/550
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("por dia")
    class PorDia {

        @Test
        @DisplayName("tem um item para cada dia do período, em ordem, inclusive os sem fechamento")
        void todosOsDias() {
            assertThat(gerarCenario().porDia())
                    .extracting(RelatorioResponse.PorDia::data)
                    .containsExactly(SEG, TER, QUA);
        }

        @Test
        @DisplayName("soma os produtos de cada dia; dia sem fechamento fica zerado")
        void somaPorDia() {
            List<RelatorioResponse.PorDia> dias = gerarCenario().porDia();

            assertThat(dias.get(0).produzido()).isEqualTo(340);   // 300 pães + 40 sonhos
            assertThat(dias.get(0).vendido()).isEqualTo(300);
            assertThat(dias.get(0).descartado()).isEqualTo(25);

            assertThat(dias.get(1).produzido()).isEqualTo(250);
            assertThat(dias.get(1).descartado()).isZero();

            assertThat(dias.get(2).produzido()).isZero();         // quarta: fornada sem fechamento
            assertThat(dias.get(2).vendido()).isZero();
        }

        @Test
        @DisplayName("traz o dia da semana abreviado em português")
        void diaDaSemana() {
            assertThat(gerarCenario().porDia().get(0).diaDaSemana()).startsWith("seg");
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("período")
    class Periodo {

        @Test
        @DisplayName("sem datas: últimos 7 dias, terminando ontem")
        void padraoUltimos7Dias() {
            LocalDate ontem = LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);
            LocalDate inicio = ontem.minusDays(6);
            when(fornadaRepository.findByDataBetween(inicio, ontem)).thenReturn(List.of());
            when(fechamentoRepository.findByDataBetween(inicio, ontem)).thenReturn(List.of());

            RelatorioResponse r = relatorioService.gerar(null, null);

            assertThat(r.inicio()).isEqualTo(inicio);
            assertThat(r.fim()).isEqualTo(ontem);
            assertThat(r.porDia()).hasSize(7);
            verify(fechamentoRepository).findByDataBetween(inicio, ontem);
        }

        @Test
        @DisplayName("período sem nenhum dado: tudo zerado e taxa zero (sem divisão por zero)")
        void semDados() {
            when(fornadaRepository.findByDataBetween(SEG, QUA)).thenReturn(List.of());
            when(fechamentoRepository.findByDataBetween(SEG, QUA)).thenReturn(List.of());

            RelatorioResponse r = relatorioService.gerar(SEG, QUA);

            assertThat(r.total().produzido()).isZero();
            assertThat(r.total().taxaDesperdicio()).isEqualByComparingTo("0");
            assertThat(r.total().custoDescarte()).isEqualByComparingTo("0");
            assertThat(r.porProduto()).isEmpty();
            assertThat(r.porDia()).hasSize(3);
        }

        @Test
        @DisplayName("um dia só (início igual ao fim) é válido")
        void umDiaSo() {
            when(fornadaRepository.findByDataBetween(SEG, SEG)).thenReturn(List.of());
            when(fechamentoRepository.findByDataBetween(SEG, SEG)).thenReturn(List.of());

            assertThat(relatorioService.gerar(SEG, SEG).porDia()).hasSize(1);
        }

        @Test
        @DisplayName("início depois do fim: RegraDeNegocioException sem consultar o banco")
        void inicioDepoisDoFim() {
            assertThatThrownBy(() -> relatorioService.gerar(QUA, SEG))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("inicial");

            verifyNoInteractions(fornadaRepository, fechamentoRepository);
        }
    }
}
