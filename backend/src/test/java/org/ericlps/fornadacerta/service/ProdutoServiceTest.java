package org.ericlps.fornadacerta.service;

import org.ericlps.fornadacerta.calculo.CalculadoraCusto;
import org.ericlps.fornadacerta.dto.ItemReceitaRequest;
import org.ericlps.fornadacerta.dto.ProdutoDetalheResponse;
import org.ericlps.fornadacerta.dto.ProdutoPatchRequest;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Insumo;
import org.ericlps.fornadacerta.entity.ItemReceita;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.mapper.ProdutoMapper;
import org.ericlps.fornadacerta.repository.InsumoRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    private static final Sort NOME_ASC = Sort.by(Sort.Direction.ASC, "nome");
    private static final Sort NOME_DESC = Sort.by(Sort.Direction.DESC, "nome");

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private InsumoRepository insumoRepository;

    private ProdutoService produtoService;

    private Insumo farinha;
    private Insumo ovo;
    private Insumo sacoMassa;

    @BeforeEach
    void setUp() {
        // Repositories são mocks (sem banco); mapper, ficha técnica e calculadora são os reais,
        // assim o teste confere o custo calculado de ponta a ponta.
        FichaTecnicaService fichaTecnicaService = new FichaTecnicaService(insumoRepository, new CalculadoraCusto());
        produtoService = new ProdutoService(produtoRepository, new ProdutoMapper(), fichaTecnicaService);

        farinha = insumo("Farinha de trigo", UnidadeMedida.KG, "5.00");
        ovo = insumo("Ovo", UnidadeMedida.UN, "0.80");
        sacoMassa = insumo("Saco de massa", UnidadeMedida.SACO, "48.00");
    }

    private Insumo insumo(String nome, UnidadeMedida unidade, String custo) {
        Insumo insumo = new Insumo();
        insumo.setId(UUID.randomUUID());
        insumo.setNome(nome);
        insumo.setUnidade(unidade);
        insumo.setCustoUnitario(new BigDecimal(custo));
        return insumo;
    }

    private Produto produto(UUID id, String nome, String preco, String custo, boolean status) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setPreco(new BigDecimal(preco));
        produto.setCusto(new BigDecimal(custo));
        produto.setStatus(status);
        return produto;
    }

    private ItemReceitaRequest item(Insumo insumo, String quantidade) {
        return new ItemReceitaRequest(insumo.getId(), new BigDecimal(quantidade));
    }

    private void insumosExistem(Insumo... insumos) {
        when(insumoRepository.findAllById(any())).thenReturn(List.of(insumos));
    }

    private ProdutoPatchRequest patch(String nome, String preco, Boolean status) {
        return new ProdutoPatchRequest(nome, preco == null ? null : new BigDecimal(preco), status, null, null, null);
    }

    // =====================================================================
    @Nested
    @DisplayName("cadastrarProduto")
    class Cadastrar {

        @Test
        @DisplayName("produção própria: salva com a receita e devolve o custo calculado")
        void producaoPropria() {
            UUID idGerado = UUID.randomUUID();
            when(produtoRepository.existsByNomeIgnoreCase("Rosca")).thenReturn(false);
            insumosExistem(farinha, ovo);
            when(produtoRepository.save(any(Produto.class))).thenAnswer(invocacao -> {
                Produto produto = invocacao.getArgument(0);
                produto.setId(idGerado); // simula o banco gerando o id
                return produto;
            });

            ProdutoResponse response = produtoService.cadastrarProduto(new ProdutoRequest("Rosca",
                    new BigDecimal("2.50"), OrigemProduto.PRODUCAO_PROPRIA, 12,
                    List.of(item(farinha, "1"), item(ovo, "2"))));

            assertThat(response.id()).isEqualTo(idGerado);
            assertThat(response.nome()).isEqualTo("Rosca");
            assertThat(response.custo()).isEqualByComparingTo("0.55"); // (5,00 + 1,60) / 12
            assertThat(response.origem()).isEqualTo(OrigemProduto.PRODUCAO_PROPRIA);
            assertThat(response.rendimento()).isEqualTo(12);
            assertThat(response.ativo()).isTrue();
        }

        @Test
        @DisplayName("envia ao repository uma entity nova, ativa, com a receita ligada a ela")
        void enviaEntityCorreta() {
            when(produtoRepository.existsByNomeIgnoreCase("Pão francês")).thenReturn(false);
            insumosExistem(sacoMassa);
            when(produtoRepository.save(any(Produto.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

            produtoService.cadastrarProduto(new ProdutoRequest("Pão francês", new BigDecimal("0.80"),
                    OrigemProduto.FORNECEDOR, 80, List.of(item(sacoMassa, "1"))));

            ArgumentCaptor<Produto> captor = ArgumentCaptor.forClass(Produto.class);
            verify(produtoRepository).save(captor.capture());
            Produto salvo = captor.getValue();
            assertThat(salvo.getId()).isNull();
            assertThat(salvo.getStatus()).isTrue();
            assertThat(salvo.getOrigem()).isEqualTo(OrigemProduto.FORNECEDOR);
            assertThat(salvo.getCusto()).isEqualByComparingTo("0.60");
            assertThat(salvo.getReceita()).singleElement().satisfies(i -> {
                assertThat(i.getProduto()).isSameAs(salvo);
                assertThat(i.getInsumo()).isSameAs(sacoMassa);
            });
        }

        @Test
        @DisplayName("com nome já existente: 409 sem olhar insumos e sem salvar")
        void nomeDuplicado() {
            when(produtoRepository.existsByNomeIgnoreCase("Baguete")).thenReturn(true);

            assertThatThrownBy(() -> produtoService.cadastrarProduto(new ProdutoRequest("Baguete",
                    new BigDecimal("9.90"), OrigemProduto.PRODUCAO_PROPRIA, 10, List.of(item(farinha, "1")))))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessageContaining("Baguete");

            verifyNoInteractions(insumoRepository);
            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("ficha técnica inválida (fornecedor sem saco): 422 e não salva")
        void fichaInvalida() {
            when(produtoRepository.existsByNomeIgnoreCase("Pão francês")).thenReturn(false);
            insumosExistem(farinha);

            assertThatThrownBy(() -> produtoService.cadastrarProduto(new ProdutoRequest("Pão francês",
                    new BigDecimal("0.80"), OrigemProduto.FORNECEDOR, 80, List.of(item(farinha, "1")))))
                    .isInstanceOf(RegraDeNegocioException.class);

            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("insumo inexistente: 404 e não salva")
        void insumoInexistente() {
            when(produtoRepository.existsByNomeIgnoreCase("Rosca")).thenReturn(false);
            insumosExistem();

            assertThatThrownBy(() -> produtoService.cadastrarProduto(new ProdutoRequest("Rosca",
                    new BigDecimal("2.50"), OrigemProduto.PRODUCAO_PROPRIA, 12, List.of(item(farinha, "1")))))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            verify(produtoRepository, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("detalhar")
    class Detalhar {

        @Test
        @DisplayName("produto existente: devolve dados e itens da ficha técnica")
        void existente() {
            UUID id = UUID.randomUUID();
            Produto rosca = produto(id, "Rosca", "2.50", "0.55", true);
            rosca.setRendimento(12);
            rosca.getReceita().add(new ItemReceita(rosca, farinha, BigDecimal.ONE));
            when(produtoRepository.findById(id)).thenReturn(Optional.of(rosca));

            ProdutoDetalheResponse detalhe = produtoService.detalhar(id);

            assertThat(detalhe.id()).isEqualTo(id);
            assertThat(detalhe.rendimento()).isEqualTo(12);
            assertThat(detalhe.itens()).singleElement()
                    .satisfies(i -> assertThat(i.insumoNome()).isEqualTo("Farinha de trigo"));
        }

        @Test
        @DisplayName("produto inexistente: 404")
        void inexistente() {
            UUID id = UUID.randomUUID();
            when(produtoRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> produtoService.detalhar(id))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Produto não encontrado");
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("atualizarParcialmente")
    class AtualizarParcialmente {

        private final UUID id = UUID.randomUUID();
        private Produto existente;

        @BeforeEach
        void setUp() {
            existente = produto(id, "Pão francês", "0.80", "0.40", true);
        }

        private void produtoExiste() {
            when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));
        }

        @Test
        @DisplayName("só nome: altera o nome e mantém preço, custo e status")
        void soNome() {
            produtoExiste();
            when(produtoRepository.existsByNomeIgnoreCaseAndIdNot("Pão de sal", id)).thenReturn(false);

            ProdutoResponse response = produtoService.atualizarParcialmente(id, patch("Pão de sal", null, null));

            assertThat(response.nome()).isEqualTo("Pão de sal");
            assertThat(response.preco()).isEqualByComparingTo("0.80");
            assertThat(response.custo()).isEqualByComparingTo("0.40");
            assertThat(response.ativo()).isTrue();
        }

        @Test
        @DisplayName("só preço: altera o preço e não checa nome nem insumos")
        void soPreco() {
            produtoExiste();

            ProdutoResponse response = produtoService.atualizarParcialmente(id, patch(null, "0.95", null));

            assertThat(response.preco()).isEqualByComparingTo("0.95");
            assertThat(response.nome()).isEqualTo("Pão francês");
            verify(produtoRepository, never()).existsByNomeIgnoreCaseAndIdNot(anyString(), any());
            verifyNoInteractions(insumoRepository);
        }

        @Test
        @DisplayName("só status: desativa (botão da listagem)")
        void soStatus() {
            produtoExiste();

            ProdutoResponse response = produtoService.atualizarParcialmente(id, patch(null, null, false));

            assertThat(response.ativo()).isFalse();
            assertThat(existente.getStatus()).isFalse();
        }

        @Test
        @DisplayName("só status: reativa um produto inativo")
        void reativar() {
            existente.setStatus(false);
            produtoExiste();

            assertThat(produtoService.atualizarParcialmente(id, patch(null, null, true)).ativo()).isTrue();
        }

        @Test
        @DisplayName("corpo vazio (tudo nulo): não altera nada, nem o custo manual de produto antigo")
        void nadaParaAlterar() {
            produtoExiste();

            ProdutoResponse response = produtoService.atualizarParcialmente(id, patch(null, null, null));

            assertThat(response.nome()).isEqualTo("Pão francês");
            assertThat(response.preco()).isEqualByComparingTo("0.80");
            assertThat(response.custo()).isEqualByComparingTo("0.40");
            assertThat(response.ativo()).isTrue();
            verifyNoInteractions(insumoRepository);
        }

        @Test
        @DisplayName("ficha técnica completa: troca a receita e recalcula o custo")
        void trocaFichaTecnica() {
            produtoExiste();
            insumosExistem(sacoMassa);

            ProdutoResponse response = produtoService.atualizarParcialmente(id, new ProdutoPatchRequest(
                    null, null, null, OrigemProduto.FORNECEDOR, 80, List.of(item(sacoMassa, "1"))));

            assertThat(response.origem()).isEqualTo(OrigemProduto.FORNECEDOR);
            assertThat(response.rendimento()).isEqualTo(80);
            assertThat(response.custo()).isEqualByComparingTo("0.60");
            assertThat(existente.getReceita()).hasSize(1);
        }

        @Test
        @DisplayName("ficha técnica pela metade (só rendimento ou só itens): 422 sem mexer na receita")
        void fichaIncompleta() {
            produtoExiste();

            assertThatThrownBy(() -> produtoService.atualizarParcialmente(id,
                    new ProdutoPatchRequest(null, null, null, null, 20, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("juntos");
            assertThatThrownBy(() -> produtoService.atualizarParcialmente(id,
                    new ProdutoPatchRequest(null, null, null, OrigemProduto.PRODUCAO_PROPRIA, null,
                            List.of(item(farinha, "1")))))
                    .isInstanceOf(RegraDeNegocioException.class);

            verifyNoInteractions(insumoRepository);
            assertThat(existente.getReceita()).isEmpty();
            assertThat(existente.getCusto()).isEqualByComparingTo("0.40");
        }

        @Test
        @DisplayName("mesmo nome com outra caixa: permitido, pois a checagem ignora o próprio produto")
        void mesmoNomeDoProprioProduto() {
            produtoExiste();
            when(produtoRepository.existsByNomeIgnoreCaseAndIdNot("PÃO FRANCÊS", id)).thenReturn(false);

            assertThat(produtoService.atualizarParcialmente(id, patch("PÃO FRANCÊS", null, null)).nome())
                    .isEqualTo("PÃO FRANCÊS");
        }

        @Test
        @DisplayName("nome de outro produto: 409 e não altera nada")
        void nomeDuplicado() {
            produtoExiste();
            when(produtoRepository.existsByNomeIgnoreCaseAndIdNot("Sonho", id)).thenReturn(true);

            assertThatThrownBy(() -> produtoService.atualizarParcialmente(id, patch("Sonho", "1.00", null)))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessageContaining("Sonho");

            assertThat(existente.getNome()).isEqualTo("Pão francês");
            assertThat(existente.getPreco()).isEqualByComparingTo("0.80");
        }

        @Test
        @DisplayName("id inexistente: 404 sem checar nome")
        void produtoInexistente() {
            when(produtoRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> produtoService.atualizarParcialmente(id, patch("Pão de sal", null, null)))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Produto não encontrado");

            verify(produtoRepository, never()).existsByNomeIgnoreCaseAndIdNot(anyString(), any());
        }

        @Test
        @DisplayName("não chama save: a alteração vai para o banco pelo dirty checking da transação")
        void naoChamaSave() {
            produtoExiste();

            produtoService.atualizarParcialmente(id, patch(null, "1.00", null));

            verify(produtoRepository, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listar")
    class Listar {

        private final List<Produto> produtos = List.of(
                produto(UUID.randomUUID(), "Baguete", "9.90", "4.95", true),
                produto(UUID.randomUUID(), "Sonho", "6.50", "3.25", false));

        @Test
        @DisplayName("sem status e ASC: busca todos ordenados por nome A→Z")
        void todosAsc() {
            when(produtoRepository.findAll(NOME_ASC)).thenReturn(produtos);

            List<ProdutoResponse> response = produtoService.listar(null, Sort.Direction.ASC);

            assertThat(response).extracting(ProdutoResponse::nome).containsExactly("Baguete", "Sonho");
            verify(produtoRepository, never()).findByStatus(anyBoolean(), any());
        }

        @Test
        @DisplayName("sem status e DESC: busca todos ordenados por nome Z→A")
        void todosDesc() {
            when(produtoRepository.findAll(NOME_DESC)).thenReturn(produtos.reversed());

            List<ProdutoResponse> response = produtoService.listar(null, Sort.Direction.DESC);

            assertThat(response).extracting(ProdutoResponse::nome).containsExactly("Sonho", "Baguete");
        }

        @Test
        @DisplayName("status true: busca só os ativos com a ordenação pedida")
        void soAtivos() {
            when(produtoRepository.findByStatus(true, NOME_ASC)).thenReturn(List.of(produtos.get(0)));

            List<ProdutoResponse> response = produtoService.listar(true, Sort.Direction.ASC);

            assertThat(response).hasSize(1);
            assertThat(response.get(0).ativo()).isTrue();
            verify(produtoRepository, never()).findAll(any(Sort.class));
        }

        @Test
        @DisplayName("status false: busca só os inativos com a ordenação pedida")
        void soInativos() {
            when(produtoRepository.findByStatus(false, NOME_DESC)).thenReturn(List.of(produtos.get(1)));

            List<ProdutoResponse> response = produtoService.listar(false, Sort.Direction.DESC);

            assertThat(response).hasSize(1);
            assertThat(response.get(0).ativo()).isFalse();
        }

        @Test
        @DisplayName("sem resultados: devolve lista vazia")
        void vazio() {
            when(produtoRepository.findByStatus(false, NOME_ASC)).thenReturn(List.of());

            assertThat(produtoService.listar(false, Sort.Direction.ASC)).isEmpty();
        }
    }
}
