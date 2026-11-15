package org.ericlps.fornadacerta.service;

import org.ericlps.fornadacerta.dto.FornadaItemRequest;
import org.ericlps.fornadacerta.dto.FornadaLoteRequest;
import org.ericlps.fornadacerta.dto.FornadaPatchRequest;
import org.ericlps.fornadacerta.dto.FornadaResponse;
import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.Turno;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.mapper.FornadaMapper;
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
class FornadaServiceTest {

    private static final Sort POR_NOME_DO_PRODUTO = Sort.by("produto.nome");

    @Mock
    private FornadaRepository fornadaRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @Captor
    private ArgumentCaptor<List<Fornada>> loteCaptor;

    private FornadaService fornadaService;

    @BeforeEach
    void setUp() {
        // Repositories são mocks (sem banco); o mapper é o real.
        fornadaService = new FornadaService(fornadaRepository, produtoRepository, new FornadaMapper());
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    private Produto produto(String nome, boolean ativo) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome(nome);
        produto.setPreco(new BigDecimal("1.00"));
        produto.setCusto(new BigDecimal("0.50"));
        produto.setStatus(ativo);
        return produto;
    }

    private Fornada fornada(Produto produto, LocalDate data, Turno turno, int qtd, Integer pacotes) {
        Fornada fornada = new Fornada();
        fornada.setId(UUID.randomUUID());
        fornada.setProduto(produto);
        fornada.setData(data);
        fornada.setTurno(turno);
        fornada.setQtdProduzida(qtd);
        fornada.setPacotesUsados(pacotes);
        return fornada;
    }

    /** Faz o saveAll devolver a própria lista, com ids gerados, como o banco faria. */
    private void saveAllDevolveComIds() {
        when(fornadaRepository.saveAll(any())).thenAnswer(invocacao -> {
            List<Fornada> lista = invocacao.getArgument(0);
            lista.forEach(f -> f.setId(UUID.randomUUID()));
            return lista;
        });
    }

    // =====================================================================
    @Nested
    @DisplayName("registrarLote")
    class RegistrarLote {

        @Test
        @DisplayName("lote válido: salva todas as fornadas com data, turno e quantidades")
        void sucesso() {
            Produto pao = produto("Pão francês", true);
            Produto sonho = produto("Sonho", true);
            LocalDate data = hoje();
            when(produtoRepository.findById(pao.getId())).thenReturn(Optional.of(pao));
            when(produtoRepository.findById(sonho.getId())).thenReturn(Optional.of(sonho));
            when(fornadaRepository.existsByDataAndProdutoIdAndTurno(data, pao.getId(), Turno.MANHA)).thenReturn(false);
            when(fornadaRepository.existsByDataAndProdutoIdAndTurno(data, sonho.getId(), Turno.MANHA)).thenReturn(false);
            saveAllDevolveComIds();

            List<FornadaResponse> response = fornadaService.registrarLote(new FornadaLoteRequest(data, Turno.MANHA, List.of(
                    new FornadaItemRequest(pao.getId(), 250, 3),
                    new FornadaItemRequest(sonho.getId(), 40, null))));

            assertThat(response).hasSize(2);
            assertThat(response).extracting(FornadaResponse::produtoNome).containsExactly("Pão francês", "Sonho");
            assertThat(response).allSatisfy(f -> {
                assertThat(f.id()).isNotNull();
                assertThat(f.data()).isEqualTo(data);
                assertThat(f.turno()).isEqualTo(Turno.MANHA);
            });

            verify(fornadaRepository).saveAll(loteCaptor.capture());
            List<Fornada> salvas = loteCaptor.getValue();
            assertThat(salvas.get(0).getProduto()).isSameAs(pao);
            assertThat(salvas.get(0).getQtdProduzida()).isEqualTo(250);
            assertThat(salvas.get(0).getPacotesUsados()).isEqualTo(3);
            assertThat(salvas.get(1).getPacotesUsados()).isNull();
        }

        @Test
        @DisplayName("sem data: assume hoje (fuso de São Paulo)")
        void semDataAssumeHoje() {
            Produto pao = produto("Pão francês", true);
            when(produtoRepository.findById(pao.getId())).thenReturn(Optional.of(pao));
            when(fornadaRepository.existsByDataAndProdutoIdAndTurno(hoje(), pao.getId(), Turno.TARDE)).thenReturn(false);
            saveAllDevolveComIds();

            List<FornadaResponse> response = fornadaService.registrarLote(
                    new FornadaLoteRequest(null, Turno.TARDE, List.of(new FornadaItemRequest(pao.getId(), 100, null))));

            assertThat(response.get(0).data()).isEqualTo(hoje());
        }

        @Test
        @DisplayName("data de amanhã é permitida (fornada da manhã preparada na noite anterior)")
        void amanhaPermitido() {
            Produto pao = produto("Pão francês", true);
            LocalDate amanha = hoje().plusDays(1);
            when(produtoRepository.findById(pao.getId())).thenReturn(Optional.of(pao));
            when(fornadaRepository.existsByDataAndProdutoIdAndTurno(amanha, pao.getId(), Turno.MANHA)).thenReturn(false);
            saveAllDevolveComIds();

            List<FornadaResponse> response = fornadaService.registrarLote(
                    new FornadaLoteRequest(amanha, Turno.MANHA, List.of(new FornadaItemRequest(pao.getId(), 250, null))));

            assertThat(response.get(0).data()).isEqualTo(amanha);
        }

        @Test
        @DisplayName("data no passado é permitida (fornada esquecida)")
        void passadoPermitido() {
            Produto pao = produto("Pão francês", true);
            LocalDate semanaPassada = hoje().minusDays(7);
            when(produtoRepository.findById(pao.getId())).thenReturn(Optional.of(pao));
            when(fornadaRepository.existsByDataAndProdutoIdAndTurno(semanaPassada, pao.getId(), Turno.TARDE)).thenReturn(false);
            saveAllDevolveComIds();

            List<FornadaResponse> response = fornadaService.registrarLote(
                    new FornadaLoteRequest(semanaPassada, Turno.TARDE, List.of(new FornadaItemRequest(pao.getId(), 80, null))));

            assertThat(response.get(0).data()).isEqualTo(semanaPassada);
        }

        @Test
        @DisplayName("data depois de amanhã: RegraDeNegocioException sem consultar nem salvar nada")
        void depoisDeAmanhaBloqueado() {
            UUID qualquer = UUID.randomUUID();

            assertThatThrownBy(() -> fornadaService.registrarLote(new FornadaLoteRequest(
                    hoje().plusDays(2), Turno.MANHA, List.of(new FornadaItemRequest(qualquer, 10, null)))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("amanhã");

            verifyNoInteractions(produtoRepository, fornadaRepository);
        }

        @Test
        @DisplayName("mesmo produto duas vezes no lote: RegraDeNegocioException sem consultar nem salvar")
        void produtoRepetidoNoLote() {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> fornadaService.registrarLote(new FornadaLoteRequest(hoje(), Turno.MANHA, List.of(
                    new FornadaItemRequest(id, 10, null),
                    new FornadaItemRequest(id, 20, null)))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("mais de uma vez");

            verifyNoInteractions(produtoRepository, fornadaRepository);
        }

        @Test
        @DisplayName("produto inexistente: RecursoNaoEncontradoException e nada é salvo")
        void produtoInexistente() {
            UUID id = UUID.randomUUID();
            when(produtoRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fornadaService.registrarLote(new FornadaLoteRequest(
                    hoje(), Turno.MANHA, List.of(new FornadaItemRequest(id, 10, null)))))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            verify(fornadaRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("produto desativado: RegraDeNegocioException com o nome do produto e nada é salvo")
        void produtoDesativado() {
            Produto croissant = produto("Croissant", false);
            when(produtoRepository.findById(croissant.getId())).thenReturn(Optional.of(croissant));

            assertThatThrownBy(() -> fornadaService.registrarLote(new FornadaLoteRequest(
                    hoje(), Turno.MANHA, List.of(new FornadaItemRequest(croissant.getId(), 10, null)))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Croissant");

            verify(fornadaRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("fornada já registrada no mesmo dia e turno: ConflitoException e nada é salvo")
        void fornadaJaRegistrada() {
            Produto pao = produto("Pão francês", true);
            LocalDate data = hoje();
            when(produtoRepository.findById(pao.getId())).thenReturn(Optional.of(pao));
            when(fornadaRepository.existsByDataAndProdutoIdAndTurno(data, pao.getId(), Turno.MANHA)).thenReturn(true);

            assertThatThrownBy(() -> fornadaService.registrarLote(new FornadaLoteRequest(
                    data, Turno.MANHA, List.of(new FornadaItemRequest(pao.getId(), 250, null)))))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessageContaining("Pão francês");

            verify(fornadaRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("tudo ou nada: se o segundo item falha, o primeiro também não é salvo")
        void tudoOuNada() {
            Produto pao = produto("Pão francês", true);
            Produto croissant = produto("Croissant", false);
            LocalDate data = hoje();
            when(produtoRepository.findById(pao.getId())).thenReturn(Optional.of(pao));
            when(produtoRepository.findById(croissant.getId())).thenReturn(Optional.of(croissant));
            when(fornadaRepository.existsByDataAndProdutoIdAndTurno(data, pao.getId(), Turno.MANHA)).thenReturn(false);

            assertThatThrownBy(() -> fornadaService.registrarLote(new FornadaLoteRequest(data, Turno.MANHA, List.of(
                    new FornadaItemRequest(pao.getId(), 250, null),
                    new FornadaItemRequest(croissant.getId(), 30, null)))))
                    .isInstanceOf(RegraDeNegocioException.class);

            verify(fornadaRepository, never()).saveAll(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("com data e sem turno: busca o dia inteiro ordenado pelo nome do produto")
        void diaInteiro() {
            LocalDate data = LocalDate.of(2026, 11, 15);
            Produto baguete = produto("Baguete", true);
            when(fornadaRepository.findByData(data, POR_NOME_DO_PRODUTO)).thenReturn(List.of(
                    fornada(baguete, data, Turno.MANHA, 60, null),
                    fornada(baguete, data, Turno.TARDE, 40, null)));

            List<FornadaResponse> response = fornadaService.listar(data, null);

            assertThat(response).hasSize(2);
            verify(fornadaRepository, never()).findByDataAndTurno(any(), any(), any());
        }

        @Test
        @DisplayName("com data e turno: busca só aquele turno")
        void soUmTurno() {
            LocalDate data = LocalDate.of(2026, 11, 15);
            when(fornadaRepository.findByDataAndTurno(data, Turno.MANHA, POR_NOME_DO_PRODUTO)).thenReturn(List.of(
                    fornada(produto("Sonho", true), data, Turno.MANHA, 40, null)));

            List<FornadaResponse> response = fornadaService.listar(data, Turno.MANHA);

            assertThat(response).singleElement().satisfies(f -> assertThat(f.turno()).isEqualTo(Turno.MANHA));
            verify(fornadaRepository, never()).findByData(any(), any());
        }

        @Test
        @DisplayName("sem data: lista as fornadas de hoje")
        void semDataUsaHoje() {
            when(fornadaRepository.findByData(hoje(), POR_NOME_DO_PRODUTO)).thenReturn(List.of());

            assertThat(fornadaService.listar(null, null)).isEmpty();
            verify(fornadaRepository).findByData(hoje(), POR_NOME_DO_PRODUTO);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("atualizarParcialmente")
    class AtualizarParcialmente {

        private Fornada existente;

        @BeforeEach
        void setUp() {
            existente = fornada(produto("Pão francês", true), hoje(), Turno.MANHA, 250, 3);
        }

        @Test
        @DisplayName("só quantidade: altera a quantidade e mantém os pacotes")
        void soQuantidade() {
            when(fornadaRepository.findById(existente.getId())).thenReturn(Optional.of(existente));

            FornadaResponse response = fornadaService.atualizarParcialmente(
                    existente.getId(), new FornadaPatchRequest(230, null));

            assertThat(existente.getQtdProduzida()).isEqualTo(230);
            assertThat(existente.getPacotesUsados()).isEqualTo(3);
            assertThat(response.qtdProduzida()).isEqualTo(230);
        }

        @Test
        @DisplayName("só pacotes: altera os pacotes e mantém a quantidade")
        void soPacotes() {
            when(fornadaRepository.findById(existente.getId())).thenReturn(Optional.of(existente));

            fornadaService.atualizarParcialmente(existente.getId(), new FornadaPatchRequest(null, 4));

            assertThat(existente.getPacotesUsados()).isEqualTo(4);
            assertThat(existente.getQtdProduzida()).isEqualTo(250);
        }

        @Test
        @DisplayName("os dois campos: altera ambos; produto, data e turno nunca mudam")
        void osDois() {
            Produto produtoOriginal = existente.getProduto();
            when(fornadaRepository.findById(existente.getId())).thenReturn(Optional.of(existente));

            fornadaService.atualizarParcialmente(existente.getId(), new FornadaPatchRequest(200, 2));

            assertThat(existente.getQtdProduzida()).isEqualTo(200);
            assertThat(existente.getPacotesUsados()).isEqualTo(2);
            assertThat(existente.getProduto()).isSameAs(produtoOriginal);
            assertThat(existente.getData()).isEqualTo(hoje());
            assertThat(existente.getTurno()).isEqualTo(Turno.MANHA);
        }

        @Test
        @DisplayName("corpo vazio: não altera nada")
        void nadaParaAlterar() {
            when(fornadaRepository.findById(existente.getId())).thenReturn(Optional.of(existente));

            fornadaService.atualizarParcialmente(existente.getId(), new FornadaPatchRequest(null, null));

            assertThat(existente.getQtdProduzida()).isEqualTo(250);
            assertThat(existente.getPacotesUsados()).isEqualTo(3);
        }

        @Test
        @DisplayName("fornada inexistente: RecursoNaoEncontradoException")
        void inexistente() {
            UUID id = UUID.randomUUID();
            when(fornadaRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fornadaService.atualizarParcialmente(id, new FornadaPatchRequest(10, null)))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Fornada não encontrada");
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("excluir")
    class Excluir {

        @Test
        @DisplayName("fornada existente: é removida")
        void existente() {
            Fornada fornada = fornada(produto("Sonho", true), hoje(), Turno.TARDE, 40, null);
            when(fornadaRepository.findById(fornada.getId())).thenReturn(Optional.of(fornada));

            fornadaService.excluir(fornada.getId());

            verify(fornadaRepository).delete(fornada);
        }

        @Test
        @DisplayName("fornada inexistente: RecursoNaoEncontradoException e nada é removido")
        void inexistente() {
            UUID id = UUID.randomUUID();
            when(fornadaRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fornadaService.excluir(id))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            verify(fornadaRepository, never()).delete(any());
        }
    }
}
