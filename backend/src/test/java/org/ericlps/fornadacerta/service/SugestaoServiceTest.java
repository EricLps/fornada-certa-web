package org.ericlps.fornadacerta.service;

import org.ericlps.fornadacerta.calculo.CalculadoraSugestao;
import org.ericlps.fornadacerta.calculo.CriterioSugestao;
import org.ericlps.fornadacerta.dto.SugestaoResponse;
import org.ericlps.fornadacerta.entity.Fechamento;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.Turno;
import org.ericlps.fornadacerta.repository.FechamentoRepository;
import org.ericlps.fornadacerta.repository.FornadaRepository;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SugestaoServiceTest {

    /** Segunda-feira alvo; as segundas anteriores são 09/11 e 02/11. */
    private static final LocalDate ALVO = LocalDate.of(2026, 11, 16);
    private static final LocalDate INICIO = ALVO.minusWeeks(4);
    private static final LocalDate FIM = ALVO.minusDays(1);
    private static final LocalDate SEGUNDA_1 = LocalDate.of(2026, 11, 9);
    private static final LocalDate SEGUNDA_2 = LocalDate.of(2026, 11, 2);

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private FornadaRepository fornadaRepository;

    @Mock
    private FechamentoRepository fechamentoRepository;

    private SugestaoService sugestaoService;

    @BeforeEach
    void setUp() {
        // Repositories são mocks; a calculadora é a real, para o teste cobrir a conta de ponta a ponta.
        sugestaoService = new SugestaoService(produtoRepository, fornadaRepository, fechamentoRepository, new CalculadoraSugestao());
    }

    private Produto produto(String nome) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome(nome);
        produto.setPreco(new BigDecimal("1.00"));
        produto.setCusto(new BigDecimal("0.50"));
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

    private Fechamento fechamento(Produto produto, LocalDate data, int sobra, LocalTime esgotou) {
        Fechamento fechamento = new Fechamento();
        fechamento.setProduto(produto);
        fechamento.setData(data);
        fechamento.setQtdSobra(sobra);
        fechamento.setQtdDoada(0);
        fechamento.setQtdReaproveitada(0);
        fechamento.setCustoUnitario(produto.getCusto());
        fechamento.setHorarioEsgotou(esgotou);
        return fechamento;
    }

    private void historico(List<Produto> ativos, List<Fornada> fornadas, List<Fechamento> fechamentos) {
        when(produtoRepository.findByStatus(true, Sort.by("nome"))).thenReturn(ativos);
        when(fornadaRepository.findByDataBetween(INICIO, FIM)).thenReturn(fornadas);
        when(fechamentoRepository.findByDataBetween(INICIO, FIM)).thenReturn(fechamentos);
    }

    @Test
    @DisplayName("venda do dia = manhã + tarde - sobra; média das segundas + 10%")
    void calculaVendaSomandoTurnos() {
        Produto pao = produto("Pão francês");
        historico(List.of(pao),
                List.of(fornada(pao, SEGUNDA_1, Turno.MANHA, 80), fornada(pao, SEGUNDA_1, Turno.TARDE, 40),  // 120
                        fornada(pao, SEGUNDA_2, Turno.MANHA, 130)),                                         // 130
                List.of(fechamento(pao, SEGUNDA_1, 20, null),   // vendeu 100
                        fechamento(pao, SEGUNDA_2, 10, null))); // vendeu 120

        List<SugestaoResponse> sugestoes = sugestaoService.sugerir(ALVO);

        assertThat(sugestoes).singleElement().satisfies(s -> {
            assertThat(s.produtoId()).isEqualTo(pao.getId());
            assertThat(s.produtoNome()).isEqualTo("Pão francês");
            assertThat(s.data()).isEqualTo(ALVO);
            assertThat(s.diaDaSemana()).isEqualTo("segunda-feira");
            assertThat(s.criterio()).isEqualTo(CriterioSugestao.MESMO_DIA_DA_SEMANA);
            assertThat(s.amostras()).isEqualTo(2);
            assertThat(s.mediaVendida()).isEqualByComparingTo("110.0");
            assertThat(s.qtdSugerida()).isEqualTo(121);
        });
    }

    @Test
    @DisplayName("dia com fornada mas sem fechamento não vira venda")
    void diaSemFechamentoNaoConta() {
        Produto pao = produto("Pão francês");
        historico(List.of(pao),
                List.of(fornada(pao, SEGUNDA_1, Turno.MANHA, 100), fornada(pao, SEGUNDA_2, Turno.MANHA, 100)),
                List.of(fechamento(pao, SEGUNDA_1, 10, null)));  // só um dia fechado

        SugestaoResponse s = sugestaoService.sugerir(ALVO).getFirst();

        assertThat(s.criterio()).isEqualTo(CriterioSugestao.SEM_HISTORICO);
        assertThat(s.qtdSugerida()).isNull();
    }

    @Test
    @DisplayName("horário de esgotou no fechamento marca o dia como esgotado (fica fora da média)")
    void esgotouVemDoFechamento() {
        Produto sonho = produto("Sonho");
        LocalDate segunda3 = LocalDate.of(2026, 10, 26);
        historico(List.of(sonho),
                List.of(fornada(sonho, SEGUNDA_1, Turno.TARDE, 40),
                        fornada(sonho, SEGUNDA_2, Turno.TARDE, 40),
                        fornada(sonho, segunda3, Turno.TARDE, 40)),
                List.of(fechamento(sonho, SEGUNDA_1, 10, null),                 // vendeu 30
                        fechamento(sonho, SEGUNDA_2, 10, null),                 // vendeu 30
                        fechamento(sonho, segunda3, 0, LocalTime.of(16, 0))));  // esgotou com 40

        SugestaoResponse s = sugestaoService.sugerir(ALVO).getFirst();

        assertThat(s.amostras()).isEqualTo(3);
        assertThat(s.mediaVendida()).isEqualByComparingTo("30.0");
        assertThat(s.qtdSugerida()).isEqualTo(33);
        assertThat(s.aviso()).contains("esgotou");
    }

    @Test
    @DisplayName("cada produto tem o seu histórico; produto sem dados aparece como SEM_HISTORICO")
    void historicoSeparadoPorProduto() {
        Produto baguete = produto("Baguete");
        Produto croissant = produto("Croissant");
        historico(List.of(baguete, croissant),
                List.of(fornada(baguete, SEGUNDA_1, Turno.MANHA, 50), fornada(baguete, SEGUNDA_2, Turno.MANHA, 50)),
                List.of(fechamento(baguete, SEGUNDA_1, 10, null), fechamento(baguete, SEGUNDA_2, 10, null)));

        List<SugestaoResponse> sugestoes = sugestaoService.sugerir(ALVO);

        assertThat(sugestoes).extracting(SugestaoResponse::produtoNome).containsExactly("Baguete", "Croissant");
        assertThat(sugestoes.get(0).qtdSugerida()).isEqualTo(44); // média 40 + 10%
        assertThat(sugestoes.get(1).criterio()).isEqualTo(CriterioSugestao.SEM_HISTORICO);
    }

    @Test
    @DisplayName("sem data: sugere para amanhã e busca as 4 semanas anteriores")
    void semDataSugereAmanha() {
        LocalDate amanha = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);
        when(produtoRepository.findByStatus(true, Sort.by("nome"))).thenReturn(List.of());
        when(fornadaRepository.findByDataBetween(amanha.minusWeeks(4), amanha.minusDays(1))).thenReturn(List.of());
        when(fechamentoRepository.findByDataBetween(amanha.minusWeeks(4), amanha.minusDays(1))).thenReturn(List.of());

        assertThat(sugestaoService.sugerir(null)).isEmpty();

        verify(fechamentoRepository).findByDataBetween(amanha.minusWeeks(4), amanha.minusDays(1));
    }

    @Test
    @DisplayName("só produtos ativos recebem sugestão")
    void soAtivos() {
        historico(List.of(), List.of(), List.of());

        assertThat(sugestaoService.sugerir(ALVO)).isEmpty();

        verify(produtoRepository).findByStatus(true, Sort.by("nome"));
    }
}
