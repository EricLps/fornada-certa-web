package org.ericlps.fornadacerta.service;

import org.ericlps.fornadacerta.dto.FechamentoItemRequest;
import org.ericlps.fornadacerta.dto.FechamentoLoteRequest;
import org.ericlps.fornadacerta.dto.FechamentoResponse;
import org.ericlps.fornadacerta.entity.Fechamento;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.Insumo;
import org.ericlps.fornadacerta.entity.ItemReceita;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.mapper.FechamentoMapper;
import org.ericlps.fornadacerta.repository.FechamentoRepository;
import org.ericlps.fornadacerta.repository.FornadaRepository;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FechamentoServiceTest {

    private static final Sort POR_NOME_DO_PRODUTO = Sort.by("produto.nome");

    @Mock
    private FechamentoRepository fechamentoRepository;

    @Mock
    private FornadaRepository fornadaRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Captor
    private ArgumentCaptor<List<Fechamento>> loteCaptor;

    private FechamentoService fechamentoService;

    @BeforeEach
    void setUp() {
        fechamentoService = new FechamentoService(fechamentoRepository, fornadaRepository, produtoRepository, new FechamentoMapper());
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    private Produto produto(String nome, String custo, boolean ativo) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome(nome);
        produto.setPreco(new BigDecimal("1.00"));
        produto.setCusto(new BigDecimal(custo));
        produto.setStatus(ativo);
        return produto;
    }

    private FechamentoLoteRequest lote(LocalDate data, FechamentoItemRequest... itens) {
        return new FechamentoLoteRequest(data, List.of(itens));
    }

    /** Prepara um produto que existe, ainda não foi fechado e teve a produção informada no dia. */
    private void prepararProduto(Produto produto, LocalDate data, long produzido) {
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(fechamentoRepository.existsByDataAndProdutoId(data, produto.getId())).thenReturn(false);
        when(fornadaRepository.somarProducaoDoDia(produto.getId(), data)).thenReturn(produzido);
    }

    /** Pão de fornecedor: um saco de R$ 28,00 que normalmente rende 80 pães (custo cadastrado 0,35). */
    private Produto paoDeFornecedor() {
        Insumo saco = new Insumo();
        saco.setId(UUID.randomUUID());
        saco.setNome("Saco de massa de pão francês");
        saco.setUnidade(UnidadeMedida.SACO);
        saco.setCustoUnitario(new BigDecimal("28.00"));
        saco.setItensPorSaco(80);
        Produto pao = produto("Pão francês", "0.35", true);
        pao.setOrigem(OrigemProduto.FORNECEDOR);
        pao.setRendimento(80);
        pao.getReceita().add(new ItemReceita(pao, saco, BigDecimal.ONE));
        return pao;
    }

    private Fornada fornada(int qtdProduzida, Integer pacotesUsados) {
        Fornada fornada = new Fornada();
        fornada.setQtdProduzida(qtdProduzida);
        fornada.setPacotesUsados(pacotesUsados);
        return fornada;
    }

    private void saveAllDevolveComIds() {
        when(fechamentoRepository.saveAll(any())).thenAnswer(invocacao -> {
            List<Fechamento> lista = invocacao.getArgument(0);
            lista.forEach(f -> f.setId(UUID.randomUUID()));
            return lista;
        });
    }

    // =====================================================================
    @Nested
    @DisplayName("registrarLote: caminhos válidos")
    class Validos {

        @Test
        @DisplayName("lote válido: salva os fechamentos com sobra, destinos e custo do produto")
        void sucesso() {
            Produto pao = produto("Pão francês", "0.40", true);
            Produto sonho = produto("Sonho", "2.00", true);
            LocalDate data = hoje();
            prepararProduto(pao, data, 300);
            prepararProduto(sonho, data, 40);
            saveAllDevolveComIds();

            List<FechamentoResponse> response = fechamentoService.registrarLote(lote(data,
                    new FechamentoItemRequest(pao.getId(), 30, 10, 5, null),
                    new FechamentoItemRequest(sonho.getId(), 0, null, null, LocalTime.of(15, 0))));

            assertThat(response).hasSize(2);
            assertThat(response.get(0).qtdDescartada()).isEqualTo(15);
            assertThat(response.get(0).custoDescarte()).isEqualByComparingTo("6.00");
            assertThat(response.get(1).horarioEsgotou()).isEqualTo(LocalTime.of(15, 0));

            verify(fechamentoRepository).saveAll(loteCaptor.capture());
            Fechamento salvo = loteCaptor.getValue().get(0);
            assertThat(salvo.getData()).isEqualTo(data);
            assertThat(salvo.getCustoUnitario()).isEqualByComparingTo("0.40");
        }

        @Test
        @DisplayName("fornecedor com sacos informados: custo do dia = sacos × preço do saco ÷ pães que saíram")
        void custoRealComSacos() {
            Produto pao = paoDeFornecedor();
            LocalDate data = hoje();
            prepararProduto(pao, data, 140);
            // 2 sacos que deveriam dar 160 pães, mas um veio com menos: saíram 140
            when(fornadaRepository.findByProdutoIdAndData(pao.getId(), data)).thenReturn(List.of(fornada(140, 2)));
            saveAllDevolveComIds();

            fechamentoService.registrarLote(lote(data, new FechamentoItemRequest(pao.getId(), 10, null, null, null)));

            verify(fechamentoRepository).saveAll(loteCaptor.capture());
            // 2 × 28,00 = 56,00 ÷ 140 = 0,40 (e não os 0,35 do cadastro)
            assertThat(loteCaptor.getValue().get(0).getCustoUnitario()).isEqualByComparingTo("0.40");
        }

        @Test
        @DisplayName("fornecedor com um turno sem sacos informados: esse turno entra pelo custo do cadastro")
        void custoMisturado() {
            Produto pao = paoDeFornecedor();
            LocalDate data = hoje();
            prepararProduto(pao, data, 220);
            when(fornadaRepository.findByProdutoIdAndData(pao.getId(), data))
                    .thenReturn(List.of(fornada(150, 2), fornada(70, null)));
            saveAllDevolveComIds();

            fechamentoService.registrarLote(lote(data, new FechamentoItemRequest(pao.getId(), 20, null, null, null)));

            verify(fechamentoRepository).saveAll(loteCaptor.capture());
            // (2 × 28,00 + 70 × 0,35) ÷ 220 = 80,50 ÷ 220 = 0,3659 → 0,37
            assertThat(loteCaptor.getValue().get(0).getCustoUnitario()).isEqualByComparingTo("0.37");
        }

        @Test
        @DisplayName("produto de produção própria: usa o custo do cadastro, sem consultar as fornadas")
        void producaoPropriaUsaCadastro() {
            Produto sonho = produto("Sonho", "2.00", true);
            LocalDate data = hoje();
            prepararProduto(sonho, data, 40);
            saveAllDevolveComIds();

            fechamentoService.registrarLote(lote(data, new FechamentoItemRequest(sonho.getId(), 5, null, null, null)));

            verify(fornadaRepository, never()).findByProdutoIdAndData(any(), any());
            verify(fechamentoRepository).saveAll(loteCaptor.capture());
            assertThat(loteCaptor.getValue().get(0).getCustoUnitario()).isEqualByComparingTo("2.00");
        }

        @Test
        @DisplayName("sem data: fecha o dia de hoje")
        void semDataUsaHoje() {
            Produto pao = produto("Pão francês", "0.40", true);
            prepararProduto(pao, hoje(), 100);
            saveAllDevolveComIds();

            List<FechamentoResponse> response = fechamentoService.registrarLote(
                    lote(null, new FechamentoItemRequest(pao.getId(), 10, null, null, null)));

            assertThat(response.get(0).data()).isEqualTo(hoje());
        }

        @Test
        @DisplayName("dia no passado: permitido (dia que esqueceram de fechar)")
        void passadoPermitido() {
            Produto pao = produto("Pão francês", "0.40", true);
            LocalDate ontem = hoje().minusDays(1);
            prepararProduto(pao, ontem, 100);
            saveAllDevolveComIds();

            List<FechamentoResponse> response = fechamentoService.registrarLote(
                    lote(ontem, new FechamentoItemRequest(pao.getId(), 10, null, null, null)));

            assertThat(response.get(0).data()).isEqualTo(ontem);
        }

        @Test
        @DisplayName("sobra igual ao produzido (não vendeu nada): permitido")
        void sobraIgualAoProduzido() {
            Produto pao = produto("Pão francês", "0.40", true);
            prepararProduto(pao, hoje(), 50);
            saveAllDevolveComIds();

            assertThat(fechamentoService.registrarLote(
                    lote(hoje(), new FechamentoItemRequest(pao.getId(), 50, null, null, null)))).hasSize(1);
        }

        @Test
        @DisplayName("doado + reaproveitado igual à sobra (nada descartado): permitido")
        void destinoIgualASobra() {
            Produto pao = produto("Pão francês", "0.40", true);
            prepararProduto(pao, hoje(), 100);
            saveAllDevolveComIds();

            List<FechamentoResponse> response = fechamentoService.registrarLote(
                    lote(hoje(), new FechamentoItemRequest(pao.getId(), 20, 15, 5, null)));

            assertThat(response.get(0).qtdDescartada()).isZero();
        }

        @Test
        @DisplayName("produto desativado depois de assado: o fechamento é permitido")
        void produtoDesativadoPodeFechar() {
            Produto croissant = produto("Croissant", "3.00", false);
            prepararProduto(croissant, hoje(), 20);
            saveAllDevolveComIds();

            assertThat(fechamentoService.registrarLote(
                    lote(hoje(), new FechamentoItemRequest(croissant.getId(), 5, null, null, null)))).hasSize(1);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("registrarLote: bloqueios")
    class Bloqueios {

        @Test
        @DisplayName("data de amanhã: RegraDeNegocioException sem consultar nem salvar nada")
        void amanhaBloqueado() {
            assertThatThrownBy(() -> fechamentoService.registrarLote(
                    lote(hoje().plusDays(1), new FechamentoItemRequest(UUID.randomUUID(), 0, null, null, null))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("ainda não aconteceu");

            verifyNoInteractions(produtoRepository, fornadaRepository, fechamentoRepository);
        }

        @Test
        @DisplayName("mesmo produto duas vezes: RegraDeNegocioException sem consultar nada")
        void produtoRepetido() {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> fechamentoService.registrarLote(lote(hoje(),
                    new FechamentoItemRequest(id, 1, null, null, null),
                    new FechamentoItemRequest(id, 2, null, null, null))))
                    .isInstanceOf(RegraDeNegocioException.class);

            verifyNoInteractions(produtoRepository, fornadaRepository, fechamentoRepository);
        }

        @Test
        @DisplayName("produto inexistente: RecursoNaoEncontradoException")
        void produtoInexistente() {
            UUID id = UUID.randomUUID();
            when(produtoRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fechamentoService.registrarLote(
                    lote(hoje(), new FechamentoItemRequest(id, 1, null, null, null))))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            verify(fechamentoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("doado + reaproveitado maior que a sobra: 422 sem nem consultar fornadas")
        void destinoMaiorQueSobra() {
            Produto pao = produto("Pão francês", "0.40", true);
            when(produtoRepository.findById(pao.getId())).thenReturn(Optional.of(pao));

            assertThatThrownBy(() -> fechamentoService.registrarLote(
                    lote(hoje(), new FechamentoItemRequest(pao.getId(), 10, 8, 5, null))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Pão francês")
                    .hasMessageContaining("13");

            verifyNoInteractions(fornadaRepository);
            verify(fechamentoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("horário de esgotou com sobra maior que zero: 422")
        void esgotouComSobra() {
            Produto pao = produto("Pão francês", "0.40", true);
            when(produtoRepository.findById(pao.getId())).thenReturn(Optional.of(pao));

            assertThatThrownBy(() -> fechamentoService.registrarLote(
                    lote(hoje(), new FechamentoItemRequest(pao.getId(), 3, null, null, LocalTime.of(11, 0)))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("esgotou");

            verify(fechamentoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("dia já fechado para o produto: ConflitoException")
        void jaFechado() {
            Produto pao = produto("Pão francês", "0.40", true);
            when(produtoRepository.findById(pao.getId())).thenReturn(Optional.of(pao));
            when(fechamentoRepository.existsByDataAndProdutoId(hoje(), pao.getId())).thenReturn(true);

            assertThatThrownBy(() -> fechamentoService.registrarLote(
                    lote(hoje(), new FechamentoItemRequest(pao.getId(), 10, null, null, null))))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessageContaining("Pão francês");

            verify(fechamentoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("produto sem nenhuma fornada no dia: 422")
        void semFornada() {
            Produto sonho = produto("Sonho", "2.00", true);
            prepararProduto(sonho, hoje(), 0);

            assertThatThrownBy(() -> fechamentoService.registrarLote(
                    lote(hoje(), new FechamentoItemRequest(sonho.getId(), 0, null, null, null))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Não houve fornada de Sonho");

            verify(fechamentoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("sobra maior que o produzido no dia: 422 informando os dois números")
        void sobraMaiorQueProduzido() {
            Produto pao = produto("Pão francês", "0.40", true);
            prepararProduto(pao, hoje(), 100);

            assertThatThrownBy(() -> fechamentoService.registrarLote(
                    lote(hoje(), new FechamentoItemRequest(pao.getId(), 120, null, null, null))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("120")
                    .hasMessageContaining("100");

            verify(fechamentoRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("tudo ou nada: se o segundo item falha, o primeiro também não é salvo")
        void tudoOuNada() {
            Produto pao = produto("Pão francês", "0.40", true);
            Produto sonho = produto("Sonho", "2.00", true);
            prepararProduto(pao, hoje(), 100);
            when(produtoRepository.findById(sonho.getId())).thenReturn(Optional.of(sonho));

            assertThatThrownBy(() -> fechamentoService.registrarLote(lote(hoje(),
                    new FechamentoItemRequest(pao.getId(), 10, null, null, null),
                    new FechamentoItemRequest(sonho.getId(), 2, 5, null, null))))
                    .isInstanceOf(RegraDeNegocioException.class);

            verify(fechamentoRepository, never()).saveAll(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("com data: busca os fechamentos daquele dia, ordenados pelo nome do produto")
        void comData() {
            LocalDate data = LocalDate.of(2026, 11, 14);
            when(fechamentoRepository.findByData(data, POR_NOME_DO_PRODUTO)).thenReturn(List.of());

            assertThat(fechamentoService.listar(data)).isEmpty();
            verify(fechamentoRepository).findByData(data, POR_NOME_DO_PRODUTO);
        }

        @Test
        @DisplayName("sem data: lista os de hoje")
        void semData() {
            when(fechamentoRepository.findByData(hoje(), POR_NOME_DO_PRODUTO)).thenReturn(List.of());

            fechamentoService.listar(null);

            verify(fechamentoRepository).findByData(hoje(), POR_NOME_DO_PRODUTO);
        }
    }
}
