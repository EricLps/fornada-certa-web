package org.ericlps.fornadacerta.service;

import org.ericlps.fornadacerta.dto.ProdutoPatchRequest;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.mapper.ProdutoMapper;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    private static final Sort NOME_ASC = Sort.by(Sort.Direction.ASC, "nome");
    private static final Sort NOME_DESC = Sort.by(Sort.Direction.DESC, "nome");

    @Mock
    private ProdutoRepository produtoRepository;

    private ProdutoService produtoService;

    @BeforeEach
    void setUp() {
        // Repository é mock (sem banco); mapper é o real, para cobrir a conversão também.
        produtoService = new ProdutoService(produtoRepository, new ProdutoMapper());
    }

    private Produto produto(UUID id, String nome, String preco, boolean status) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setPreco(new BigDecimal(preco));
        produto.setStatus(status);
        return produto;
    }

    // =====================================================================
    @Nested
    @DisplayName("cadastrarProduto")
    class Cadastrar {

        @Test
        @DisplayName("com nome novo: salva e devolve o response com o id gerado")
        void sucesso() {
            UUID idGerado = UUID.randomUUID();
            when(produtoRepository.existsByNomeIgnoreCase("Baguete")).thenReturn(false);
            when(produtoRepository.save(any(Produto.class))).thenAnswer(invocacao -> {
                Produto produto = invocacao.getArgument(0);
                produto.setId(idGerado); // simula o banco gerando o id
                return produto;
            });

            ProdutoResponse response = produtoService.cadastrarProduto(
                    new ProdutoRequest("Baguete", new BigDecimal("9.90")));

            assertThat(response.id()).isEqualTo(idGerado);
            assertThat(response.nome()).isEqualTo("Baguete");
            assertThat(response.valor()).isEqualByComparingTo("9.90");
            assertThat(response.ativo()).isTrue();
        }

        @Test
        @DisplayName("envia ao repository uma entity nova, ativa e com os dados do request")
        void enviaEntityCorreta() {
            when(produtoRepository.existsByNomeIgnoreCase("Croissant")).thenReturn(false);
            when(produtoRepository.save(any(Produto.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

            produtoService.cadastrarProduto(new ProdutoRequest("Croissant", new BigDecimal("7.00")));

            ArgumentCaptor<Produto> captor = ArgumentCaptor.forClass(Produto.class);
            verify(produtoRepository).save(captor.capture());
            Produto salvo = captor.getValue();

            assertThat(salvo.getId()).isNull();
            assertThat(salvo.getNome()).isEqualTo("Croissant");
            assertThat(salvo.getPreco()).isEqualByComparingTo("7.00");
            assertThat(salvo.getStatus()).isTrue();
        }

        @Test
        @DisplayName("com nome já existente: lança exceção e não salva nada")
        void nomeDuplicado() {
            when(produtoRepository.existsByNomeIgnoreCase("Pão francês")).thenReturn(true);

            assertThatThrownBy(() -> produtoService.cadastrarProduto(
                    new ProdutoRequest("Pão francês", new BigDecimal("0.80"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Pão francês");

            verify(produtoRepository, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("atualizarParcialmente")
    class AtualizarParcialmente {

        private UUID id;
        private Produto existente;

        @BeforeEach
        void setUp() {
            id = UUID.randomUUID();
            existente = produto(id, "Pão francês", "0.80", true);
        }

        @Test
        @DisplayName("só nome: altera o nome e mantém preço e status")
        void soNome() {
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));
            when(produtoRepository.existsByNomeIgnoreCaseAndIdNot("Pão de sal", id)).thenReturn(false);

            ProdutoResponse response = produtoService.atualizarParcialmente(
                    id, new ProdutoPatchRequest("Pão de sal", null, null));

            assertThat(existente.getNome()).isEqualTo("Pão de sal");
            assertThat(existente.getPreco()).isEqualByComparingTo("0.80");
            assertThat(existente.getStatus()).isTrue();
            assertThat(response.nome()).isEqualTo("Pão de sal");
        }

        @Test
        @DisplayName("só preço: altera o preço, mantém o resto e não checa nome")
        void soPreco() {
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

            ProdutoResponse response = produtoService.atualizarParcialmente(
                    id, new ProdutoPatchRequest(null, new BigDecimal("1.50"), null));

            assertThat(existente.getNome()).isEqualTo("Pão francês");
            assertThat(existente.getPreco()).isEqualByComparingTo("1.50");
            assertThat(existente.getStatus()).isTrue();
            assertThat(response.valor()).isEqualByComparingTo("1.50");
            verify(produtoRepository, never()).existsByNomeIgnoreCaseAndIdNot(anyString(), any());
        }

        @Test
        @DisplayName("só status: desativa sem mexer no preço (botão da listagem)")
        void soStatus() {
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

            ProdutoResponse response = produtoService.atualizarParcialmente(
                    id, new ProdutoPatchRequest(null, null, false));

            assertThat(existente.getStatus()).isFalse();
            assertThat(existente.getPreco()).isEqualByComparingTo("0.80");
            assertThat(existente.getNome()).isEqualTo("Pão francês");
            assertThat(response.ativo()).isFalse();
        }

        @Test
        @DisplayName("só status: reativa um produto inativo")
        void reativar() {
            existente.setStatus(false);
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

            ProdutoResponse response = produtoService.atualizarParcialmente(
                    id, new ProdutoPatchRequest(null, null, true));

            assertThat(existente.getStatus()).isTrue();
            assertThat(response.ativo()).isTrue();
        }

        @Test
        @DisplayName("todos os campos juntos: altera os três")
        void todosOsCampos() {
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));
            when(produtoRepository.existsByNomeIgnoreCaseAndIdNot("Pão integral", id)).thenReturn(false);

            ProdutoResponse response = produtoService.atualizarParcialmente(
                    id, new ProdutoPatchRequest("Pão integral", new BigDecimal("1.20"), false));

            assertThat(response.id()).isEqualTo(id);
            assertThat(response.nome()).isEqualTo("Pão integral");
            assertThat(response.valor()).isEqualByComparingTo("1.20");
            assertThat(response.ativo()).isFalse();
        }

        @Test
        @DisplayName("corpo vazio (tudo nulo): não altera nada")
        void nadaParaAlterar() {
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

            ProdutoResponse response = produtoService.atualizarParcialmente(
                    id, new ProdutoPatchRequest(null, null, null));

            assertThat(existente.getNome()).isEqualTo("Pão francês");
            assertThat(existente.getPreco()).isEqualByComparingTo("0.80");
            assertThat(existente.getStatus()).isTrue();
            assertThat(response.id()).isEqualTo(id);
            verify(produtoRepository, never()).existsByNomeIgnoreCaseAndIdNot(anyString(), any());
        }

        @Test
        @DisplayName("mesmo nome com outra caixa: permitido, pois a checagem ignora o próprio produto")
        void mesmoNomeDoProprioProduto() {
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));
            when(produtoRepository.existsByNomeIgnoreCaseAndIdNot("PÃO FRANCÊS", id)).thenReturn(false);

            produtoService.atualizarParcialmente(id, new ProdutoPatchRequest("PÃO FRANCÊS", null, null));

            assertThat(existente.getNome()).isEqualTo("PÃO FRANCÊS");
        }

        @Test
        @DisplayName("nome de outro produto: lança exceção e não altera nada")
        void nomeDuplicado() {
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));
            when(produtoRepository.existsByNomeIgnoreCaseAndIdNot("Sonho", id)).thenReturn(true);

            assertThatThrownBy(() -> produtoService.atualizarParcialmente(
                    id, new ProdutoPatchRequest("Sonho", new BigDecimal("5.00"), false)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Sonho");

            // a checagem acontece antes de qualquer alteração
            assertThat(existente.getNome()).isEqualTo("Pão francês");
            assertThat(existente.getPreco()).isEqualByComparingTo("0.80");
            assertThat(existente.getStatus()).isTrue();
        }

        @Test
        @DisplayName("id inexistente: lança 404 sem checar nome nem salvar")
        void produtoInexistente() {
            when(produtoRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> produtoService.atualizarParcialmente(
                    id, new ProdutoPatchRequest("Qualquer", null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));

            verify(produtoRepository, never()).existsByNomeIgnoreCaseAndIdNot(anyString(), any());
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("nunca cria produto novo (se salvar, é o mesmo id)")
        void naoCriaProdutoNovo() {
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));
            when(produtoRepository.existsByNomeIgnoreCaseAndIdNot("Sonho de creme", id)).thenReturn(false);

            produtoService.atualizarParcialmente(id, new ProdutoPatchRequest("Sonho de creme", null, null));

            ArgumentCaptor<Produto> captor = ArgumentCaptor.forClass(Produto.class);
            verify(produtoRepository, atMost(1)).save(captor.capture());
            captor.getAllValues().forEach(salvo -> assertThat(salvo.getId()).isEqualTo(id));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listar")
    class Listar {

        private final List<Produto> produtos = List.of(
                produto(UUID.randomUUID(), "Baguete", "9.90", true),
                produto(UUID.randomUUID(), "Sonho", "6.50", false));

        @Test
        @DisplayName("sem status e ordem asc: busca todos ordenados por nome A→Z")
        void todosAsc() {
            when(produtoRepository.findAll(NOME_ASC)).thenReturn(produtos);

            List<ProdutoResponse> response = produtoService.listar(null, "asc");

            assertThat(response).extracting(ProdutoResponse::nome).containsExactly("Baguete", "Sonho");
            verify(produtoRepository, never()).findByStatus(anyBoolean(), any());
        }

        @Test
        @DisplayName("sem status e ordem desc: busca todos ordenados por nome Z→A")
        void todosDesc() {
            when(produtoRepository.findAll(NOME_DESC)).thenReturn(produtos.reversed());

            List<ProdutoResponse> response = produtoService.listar(null, "desc");

            assertThat(response).extracting(ProdutoResponse::nome).containsExactly("Sonho", "Baguete");
        }

        @Test
        @DisplayName("status true: busca só os ativos com a ordenação pedida")
        void soAtivos() {
            List<Produto> ativos = List.of(produtos.get(0));
            when(produtoRepository.findByStatus(true, NOME_ASC)).thenReturn(ativos);

            List<ProdutoResponse> response = produtoService.listar(true, "asc");

            assertThat(response).hasSize(1);
            assertThat(response.get(0).ativo()).isTrue();
            verify(produtoRepository, never()).findAll(any(Sort.class));
        }

        @Test
        @DisplayName("status false: busca só os inativos com a ordenação pedida")
        void soInativos() {
            List<Produto> inativos = List.of(produtos.get(1));
            when(produtoRepository.findByStatus(false, NOME_DESC)).thenReturn(inativos);

            List<ProdutoResponse> response = produtoService.listar(false, "desc");

            assertThat(response).hasSize(1);
            assertThat(response.get(0).ativo()).isFalse();
        }

        @Test
        @DisplayName("ordem aceita maiúsculas e minúsculas (ASC, Desc...)")
        void ordemIgnoraCaixa() {
            when(produtoRepository.findAll(NOME_ASC)).thenReturn(produtos);
            when(produtoRepository.findAll(NOME_DESC)).thenReturn(produtos.reversed());

            assertThat(produtoService.listar(null, "ASC")).hasSize(2);
            assertThat(produtoService.listar(null, "Desc")).hasSize(2);
        }

        @Test
        @DisplayName("sem resultados: devolve lista vazia")
        void vazio() {
            when(produtoRepository.findByStatus(false, NOME_ASC)).thenReturn(List.of());

            assertThat(produtoService.listar(false, "asc")).isEmpty();
        }

        @Test
        @DisplayName("ordem inválida: lança exceção sem consultar o banco")
        void ordemInvalida() {
            assertThatThrownBy(() -> produtoService.listar(null, "xyz"))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(produtoRepository, never()).findAll(any(Sort.class));
            verify(produtoRepository, never()).findByStatus(anyBoolean(), any());
        }
    }
}
