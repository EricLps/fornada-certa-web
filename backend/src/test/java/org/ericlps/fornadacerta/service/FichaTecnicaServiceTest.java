package org.ericlps.fornadacerta.service;

import org.ericlps.fornadacerta.calculo.CalculadoraCusto;
import org.ericlps.fornadacerta.dto.ItemReceitaRequest;
import org.ericlps.fornadacerta.entity.Insumo;
import org.ericlps.fornadacerta.entity.ItemReceita;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.repository.InsumoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FichaTecnicaServiceTest {

    @Mock
    private InsumoRepository insumoRepository;

    private FichaTecnicaService fichaTecnicaService;

    private Insumo farinha;
    private Insumo ovo;
    private Insumo leite;
    private Insumo sacoMassa;

    @BeforeEach
    void setUp() {
        // Repository é mock; a calculadora é a real, para o teste conferir o custo de verdade.
        fichaTecnicaService = new FichaTecnicaService(insumoRepository, new CalculadoraCusto());
        farinha = insumo("Farinha de trigo", UnidadeMedida.KG, "5.00");
        ovo = insumo("Ovo", UnidadeMedida.UN, "0.80");
        leite = insumo("Leite", UnidadeMedida.L, "6.00");
        sacoMassa = insumo("Saco de massa de pão francês", UnidadeMedida.SACO, "48.00");
        sacoMassa.setItensPorSaco(80);
    }

    private Insumo insumo(String nome, UnidadeMedida unidade, String custo) {
        Insumo insumo = new Insumo();
        insumo.setId(UUID.randomUUID());
        insumo.setNome(nome);
        insumo.setUnidade(unidade);
        insumo.setCustoUnitario(new BigDecimal(custo));
        return insumo;
    }

    private ItemReceitaRequest item(Insumo insumo, String quantidade) {
        return new ItemReceitaRequest(insumo.getId(), new BigDecimal(quantidade));
    }

    private Produto produtoNovo() {
        Produto produto = new Produto();
        produto.setNome("Bolo");
        produto.setPreco(new BigDecimal("5.00"));
        return produto;
    }

    private void insumosExistem(Insumo... insumos) {
        when(insumoRepository.findAllById(any())).thenReturn(List.of(insumos));
    }

    // =====================================================================
    @Nested
    @DisplayName("aplicar: caminhos válidos")
    class Validos {

        @Test
        @DisplayName("produção própria: monta a receita, guarda origem e rendimento e calcula o custo")
        void producaoPropria() {
            Produto produto = produtoNovo();
            insumosExistem(farinha, ovo);

            fichaTecnicaService.aplicar(produto, OrigemProduto.PRODUCAO_PROPRIA, 12, null,
                    List.of(item(farinha, "1"), item(ovo, "2")));

            assertThat(produto.getOrigem()).isEqualTo(OrigemProduto.PRODUCAO_PROPRIA);
            assertThat(produto.getRendimento()).isEqualTo(12);
            assertThat(produto.getReceita()).hasSize(2);
            assertThat(produto.getReceita()).allSatisfy(i -> assertThat(i.getProduto()).isSameAs(produto));
            assertThat(produto.getCusto()).isEqualByComparingTo("0.55"); // (5,00 + 1,60) / 12
        }

        @Test
        @DisplayName("fornecedor: um saco que rende 80 pães -> custo 0,60")
        void fornecedor() {
            Produto produto = produtoNovo();
            insumosExistem(sacoMassa);

            fichaTecnicaService.aplicar(produto, OrigemProduto.FORNECEDOR, null, null, List.of(item(sacoMassa, "1")));

            assertThat(produto.getOrigem()).isEqualTo(OrigemProduto.FORNECEDOR);
            assertThat(produto.getRendimento()).isEqualTo(80); // veio do cadastro do saco
            assertThat(produto.getReceita()).singleElement()
                    .satisfies(i -> assertThat(i.getInsumo()).isSameAs(sacoMassa));
            assertThat(produto.getCusto()).isEqualByComparingTo("0.60");
        }

        @Test
        @DisplayName("fornecedor: o rendimento sempre vem do saco, mesmo que o pedido mande outro")
        void fornecedorIgnoraRendimentoDoPedido() {
            Produto produto = produtoNovo();
            insumosExistem(sacoMassa);

            fichaTecnicaService.aplicar(produto, OrigemProduto.FORNECEDOR, 50, null, List.of(item(sacoMassa, "1")));

            assertThat(produto.getRendimento()).isEqualTo(80);
            assertThat(produto.getCusto()).isEqualByComparingTo("0.60");
        }

        @Test
        @DisplayName("vendido em fatias: custo por fatia = receita ÷ (rendimento × fatias)")
        void vendidoEmFatias() {
            Produto bolo = produtoNovo();
            insumosExistem(farinha, ovo);

            // receita de 6,60 que rende 1 bolo de 12 fatias
            fichaTecnicaService.aplicar(bolo, OrigemProduto.PRODUCAO_PROPRIA, 1, 12,
                    List.of(item(farinha, "1"), item(ovo, "2")));

            assertThat(bolo.getFatias()).isEqualTo(12);
            assertThat(bolo.getRendimento()).isEqualTo(1);
            assertThat(bolo.getCusto()).isEqualByComparingTo("0.55");
        }

        @Test
        @DisplayName("ficha nova sem fatias: o produto deixa de ser vendido em fatias")
        void tiraFatias() {
            Produto bolo = produtoNovo();
            insumosExistem(farinha);
            fichaTecnicaService.aplicar(bolo, OrigemProduto.PRODUCAO_PROPRIA, 1, 10, List.of(item(farinha, "1")));

            fichaTecnicaService.aplicar(bolo, OrigemProduto.PRODUCAO_PROPRIA, 1, null, List.of(item(farinha, "1")));

            assertThat(bolo.getFatias()).isNull();
            assertThat(bolo.getCusto()).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("produção própria também pode usar saco (massa pronta + recheio, por exemplo)")
        void producaoPropriaComSaco() {
            Produto produto = produtoNovo();
            insumosExistem(sacoMassa, leite);

            fichaTecnicaService.aplicar(produto, OrigemProduto.PRODUCAO_PROPRIA, 40, null,
                    List.of(item(sacoMassa, "1"), item(leite, "2")));

            assertThat(produto.getCusto()).isEqualByComparingTo("1.50"); // (48 + 12) / 40
        }

        @Test
        @DisplayName("alterar a receita: atualiza a quantidade dos que ficam, remove os que saíram e adiciona os novos")
        void substituiReceita() {
            Produto produto = produtoNovo();
            insumosExistem(farinha, ovo);
            fichaTecnicaService.aplicar(produto, OrigemProduto.PRODUCAO_PROPRIA, 10, null,
                    List.of(item(farinha, "1"), item(ovo, "2")));
            ItemReceita itemFarinhaOriginal = produto.getReceita().stream()
                    .filter(i -> i.getInsumo() == farinha).findFirst().orElseThrow();

            insumosExistem(farinha, leite);
            fichaTecnicaService.aplicar(produto, OrigemProduto.PRODUCAO_PROPRIA, 10, null,
                    List.of(item(farinha, "2"), item(leite, "1")));

            assertThat(produto.getReceita()).extracting(i -> i.getInsumo().getNome())
                    .containsExactlyInAnyOrder("Farinha de trigo", "Leite");
            // a farinha continua sendo o MESMO item (só mudou a quantidade): evita apagar e inserir de novo
            assertThat(produto.getReceita()).contains(itemFarinhaOriginal);
            assertThat(itemFarinhaOriginal.getQuantidade()).isEqualByComparingTo("2");
            assertThat(produto.getCusto()).isEqualByComparingTo("1.60"); // (10 + 6) / 10
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("aplicar: bloqueios")
    class Bloqueios {

        @Test
        @DisplayName("insumo repetido: 422 sem nem consultar o banco")
        void insumoRepetido() {
            assertThatThrownBy(() -> fichaTecnicaService.aplicar(produtoNovo(), OrigemProduto.PRODUCAO_PROPRIA, 10, null,
                    List.of(item(farinha, "1"), item(farinha, "2"))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("mais de uma vez");

            verifyNoInteractions(insumoRepository);
        }

        @Test
        @DisplayName("insumo que não existe: 404 e a receita não muda")
        void insumoInexistente() {
            Produto produto = produtoNovo();
            insumosExistem(farinha);

            assertThatThrownBy(() -> fichaTecnicaService.aplicar(produto, OrigemProduto.PRODUCAO_PROPRIA, 10, null,
                    List.of(item(farinha, "1"), item(ovo, "2"))))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining(ovo.getId().toString());

            assertThat(produto.getReceita()).isEmpty();
        }

        @Test
        @DisplayName("fornecedor com insumo que não é saco: 422")
        void fornecedorSemSaco() {
            insumosExistem(farinha);

            assertThatThrownBy(() -> fichaTecnicaService.aplicar(produtoNovo(), OrigemProduto.FORNECEDOR, 10, null,
                    List.of(item(farinha, "1"))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("SACO");
        }

        @Test
        @DisplayName("fornecedor com saco que não tem a quantidade de itens cadastrada: 422 pedindo para informar")
        void sacoSemItens() {
            Insumo sacoSemQuantidade = insumo("Saco de massa de baguete", UnidadeMedida.SACO, "45.60");
            insumosExistem(sacoSemQuantidade);

            assertThatThrownBy(() -> fichaTecnicaService.aplicar(produtoNovo(), OrigemProduto.FORNECEDOR, null, null,
                    List.of(item(sacoSemQuantidade, "1"))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Saco de massa de baguete");
        }

        @Test
        @DisplayName("produção própria sem rendimento: 422")
        void producaoPropriaSemRendimento() {
            Produto produto = produtoNovo();
            insumosExistem(farinha);

            assertThatThrownBy(() -> fichaTecnicaService.aplicar(produto, OrigemProduto.PRODUCAO_PROPRIA, null, null,
                    List.of(item(farinha, "1"))))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("rende");

            assertThat(produto.getReceita()).isEmpty();
        }

        @Test
        @DisplayName("fornecedor com mais de um insumo: 422")
        void fornecedorComDoisItens() {
            insumosExistem(sacoMassa, leite);

            assertThatThrownBy(() -> fichaTecnicaService.aplicar(produtoNovo(), OrigemProduto.FORNECEDOR, 80, null,
                    List.of(item(sacoMassa, "1"), item(leite, "1"))))
                    .isInstanceOf(RegraDeNegocioException.class);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("recalcularCusto")
    class RecalcularCusto {

        @Test
        @DisplayName("produto antigo, sem ficha técnica: mantém o custo digitado à mão")
        void produtoSemFicha() {
            Produto antigo = produtoNovo();
            antigo.setCusto(new BigDecimal("1.80"));

            fichaTecnicaService.recalcularCusto(antigo);

            assertThat(antigo.getCusto()).isEqualByComparingTo("1.80");
        }

        @Test
        @DisplayName("insumo mudou de preço: o custo do produto acompanha")
        void insumoMudouDePreco() {
            Produto produto = produtoNovo();
            insumosExistem(farinha);
            fichaTecnicaService.aplicar(produto, OrigemProduto.PRODUCAO_PROPRIA, 10, null, List.of(item(farinha, "2")));
            assertThat(produto.getCusto()).isEqualByComparingTo("1.00");

            farinha.setCustoUnitario(new BigDecimal("6.50"));
            fichaTecnicaService.recalcularCusto(produto);

            assertThat(produto.getCusto()).isEqualByComparingTo("1.30");
        }

        @Test
        @DisplayName("o saco passou a vir com menos itens: rendimento e custo do produto acompanham")
        void sacoVemComMenos() {
            Produto pao = produtoNovo();
            insumosExistem(sacoMassa);
            fichaTecnicaService.aplicar(pao, OrigemProduto.FORNECEDOR, null, null, List.of(item(sacoMassa, "1")));
            assertThat(pao.getCusto()).isEqualByComparingTo("0.60");

            sacoMassa.setItensPorSaco(60);
            fichaTecnicaService.recalcularCusto(pao);

            assertThat(pao.getRendimento()).isEqualTo(60);
            assertThat(pao.getCusto()).isEqualByComparingTo("0.80");
        }
    }
}
