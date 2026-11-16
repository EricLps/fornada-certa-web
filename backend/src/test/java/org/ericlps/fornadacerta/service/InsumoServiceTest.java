package org.ericlps.fornadacerta.service;

import org.ericlps.fornadacerta.calculo.CalculadoraCusto;
import org.ericlps.fornadacerta.dto.InsumoPatchRequest;
import org.ericlps.fornadacerta.dto.InsumoRequest;
import org.ericlps.fornadacerta.dto.InsumoResponse;
import org.ericlps.fornadacerta.entity.Insumo;
import org.ericlps.fornadacerta.entity.ItemReceita;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.mapper.InsumoMapper;
import org.ericlps.fornadacerta.repository.InsumoRepository;
import org.ericlps.fornadacerta.repository.ItemReceitaRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsumoServiceTest {

    @Mock
    private InsumoRepository insumoRepository;

    @Mock
    private ItemReceitaRepository itemReceitaRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    private InsumoService insumoService;

    @BeforeEach
    void setUp() {
        // Repositories são mocks; mapper e ficha técnica são reais para ver o custo dos produtos mudar.
        FichaTecnicaService fichaTecnicaService = new FichaTecnicaService(insumoRepository, new CalculadoraCusto());
        insumoService = new InsumoService(insumoRepository, itemReceitaRepository, produtoRepository,
                fichaTecnicaService, new InsumoMapper());
    }

    private Insumo insumo(UUID id, String nome, UnidadeMedida unidade, String custo) {
        Insumo insumo = new Insumo();
        insumo.setId(id);
        insumo.setNome(nome);
        insumo.setUnidade(unidade);
        insumo.setCustoUnitario(new BigDecimal(custo));
        return insumo;
    }

    // =====================================================================
    @Nested
    @DisplayName("cadastrar")
    class Cadastrar {

        @Test
        @DisplayName("nome novo: salva e devolve com o id gerado")
        void sucesso() {
            UUID idGerado = UUID.randomUUID();
            when(insumoRepository.existsByNomeIgnoreCase("Leite")).thenReturn(false);
            when(insumoRepository.save(any(Insumo.class))).thenAnswer(invocacao -> {
                Insumo insumo = invocacao.getArgument(0);
                insumo.setId(idGerado);
                return insumo;
            });

            InsumoResponse response = insumoService.cadastrar(
                    new InsumoRequest("Leite", UnidadeMedida.L, new BigDecimal("5.89"), null, null));

            assertThat(response.id()).isEqualTo(idGerado);
            assertThat(response.nome()).isEqualTo("Leite");
            assertThat(response.unidade()).isEqualTo(UnidadeMedida.L);
            assertThat(response.custoUnitario()).isEqualByComparingTo("5.89");
        }

        @Test
        @DisplayName("envia ao repository uma entity nova com os dados do request")
        void enviaEntityCorreta() {
            when(insumoRepository.existsByNomeIgnoreCase("Ovo")).thenReturn(false);
            when(insumoRepository.save(any(Insumo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

            insumoService.cadastrar(new InsumoRequest("Ovo", UnidadeMedida.UN, new BigDecimal("0.80"), null, null));

            ArgumentCaptor<Insumo> captor = ArgumentCaptor.forClass(Insumo.class);
            verify(insumoRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isNull();
            assertThat(captor.getValue().getUnidade()).isEqualTo(UnidadeMedida.UN);
        }

        @Test
        @DisplayName("saco: guarda quantos itens vêm em cada saco")
        void saco() {
            when(insumoRepository.existsByNomeIgnoreCase("Saco de massa")).thenReturn(false);
            when(insumoRepository.save(any(Insumo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

            InsumoResponse response = insumoService.cadastrar(
                    new InsumoRequest("Saco de massa", UnidadeMedida.SACO, new BigDecimal("28.00"), 80, null));

            assertThat(response.itensPorSaco()).isEqualTo(80);
        }

        @Test
        @DisplayName("saco sem itens por saco: 422 e não salva")
        void sacoSemItens() {
            when(insumoRepository.existsByNomeIgnoreCase("Saco de massa")).thenReturn(false);

            assertThatThrownBy(() -> insumoService.cadastrar(
                    new InsumoRequest("Saco de massa", UnidadeMedida.SACO, new BigDecimal("28.00"), null, null)))
                    .isInstanceOf(RegraDeNegocioException.class);

            verify(insumoRepository, never()).save(any());
        }

        @Test
        @DisplayName("itens por saco em ingrediente comum: descartado")
        void itensEmIngrediente() {
            when(insumoRepository.existsByNomeIgnoreCase("Ovo")).thenReturn(false);
            when(insumoRepository.save(any(Insumo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

            InsumoResponse response = insumoService.cadastrar(
                    new InsumoRequest("Ovo", UnidadeMedida.UN, new BigDecimal("0.80"), 12, null));

            assertThat(response.itensPorSaco()).isNull();
        }

        @Test
        @DisplayName("gás: guarda o preço do botijão e quantas fornadas ele rende")
        void gas() {
            when(insumoRepository.existsByNomeIgnoreCase("Gás")).thenReturn(false);
            when(insumoRepository.save(any(Insumo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

            InsumoResponse response = insumoService.cadastrar(
                    new InsumoRequest("Gás", UnidadeMedida.GAS, new BigDecimal("120.00"), null, 20));

            assertThat(response.fornadasPorBotijao()).isEqualTo(20);
            assertThat(response.itensPorSaco()).isNull();
        }

        @Test
        @DisplayName("gás sem fornadas por botijão: 422 e não salva")
        void gasSemFornadas() {
            when(insumoRepository.existsByNomeIgnoreCase("Gás")).thenReturn(false);

            assertThatThrownBy(() -> insumoService.cadastrar(
                    new InsumoRequest("Gás", UnidadeMedida.GAS, new BigDecimal("120.00"), null, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("botijão");

            verify(insumoRepository, never()).save(any());
        }

        @Test
        @DisplayName("nome já existente: 409 e não salva")
        void nomeDuplicado() {
            when(insumoRepository.existsByNomeIgnoreCase("leite")).thenReturn(true);

            assertThatThrownBy(() -> insumoService.cadastrar(
                    new InsumoRequest("leite", UnidadeMedida.L, new BigDecimal("5.89"), null, null)))
                    .isInstanceOf(ConflitoException.class)
                    .hasMessage("Já existe um insumo cadastrado como: leite");

            verify(insumoRepository, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("repassa a ordenação por nome e converte todos")
        void ordena() {
            when(insumoRepository.findAll(Sort.by(Sort.Direction.DESC, "nome"))).thenReturn(List.of(
                    insumo(UUID.randomUUID(), "Ovo", UnidadeMedida.UN, "0.80"),
                    insumo(UUID.randomUUID(), "Leite", UnidadeMedida.L, "5.89")));

            List<InsumoResponse> response = insumoService.listar(Sort.Direction.DESC);

            assertThat(response).extracting(InsumoResponse::nome).containsExactly("Ovo", "Leite");
        }

        @Test
        @DisplayName("sem insumos: lista vazia")
        void vazio() {
            when(insumoRepository.findAll(Sort.by(Sort.Direction.ASC, "nome"))).thenReturn(List.of());

            assertThat(insumoService.listar(Sort.Direction.ASC)).isEmpty();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("atualizarParcialmente")
    class AtualizarParcialmente {

        private final UUID id = UUID.randomUUID();
        private Insumo farinha;

        @BeforeEach
        void setUp() {
            farinha = insumo(id, "Farinha de trigo", UnidadeMedida.KG, "5.00");
        }

        private void insumoExiste() {
            when(insumoRepository.findById(id)).thenReturn(Optional.of(farinha));
        }

        private Produto produtoQueUsaFarinha(String quantidade, int rendimento) {
            Produto produto = new Produto();
            produto.setId(UUID.randomUUID());
            produto.setNome("Pão caseiro");
            produto.setPreco(new BigDecimal("8.00"));
            produto.setRendimento(rendimento);
            produto.getReceita().add(new ItemReceita(produto, farinha, new BigDecimal(quantidade)));
            produto.setCusto(new BigDecimal("1.00"));
            return produto;
        }

        @Test
        @DisplayName("só nome: renomeia sem tocar nos produtos")
        void soNome() {
            insumoExiste();
            when(insumoRepository.existsByNomeIgnoreCaseAndIdNot("Farinha especial", id)).thenReturn(false);

            InsumoResponse response = insumoService.atualizarParcialmente(id,
                    new InsumoPatchRequest("Farinha especial", null, null, null, null));

            assertThat(response.nome()).isEqualTo("Farinha especial");
            assertThat(response.custoUnitario()).isEqualByComparingTo("5.00");
            verify(produtoRepository, never()).findUsandoInsumo(any());
        }

        @Test
        @DisplayName("nome de outro insumo: 409 e não altera")
        void nomeDuplicado() {
            insumoExiste();
            when(insumoRepository.existsByNomeIgnoreCaseAndIdNot("Leite", id)).thenReturn(true);

            assertThatThrownBy(() -> insumoService.atualizarParcialmente(id, new InsumoPatchRequest("Leite", null, null, null, null)))
                    .isInstanceOf(ConflitoException.class);

            assertThat(farinha.getNome()).isEqualTo("Farinha de trigo");
        }

        @Test
        @DisplayName("custo novo: atualiza o insumo e recalcula o custo dos produtos que o usam")
        void custoRecalculaProdutos() {
            insumoExiste();
            Produto pao = produtoQueUsaFarinha("2", 10);
            when(produtoRepository.findUsandoInsumo(id)).thenReturn(List.of(pao));

            InsumoResponse response = insumoService.atualizarParcialmente(id,
                    new InsumoPatchRequest(null, null, new BigDecimal("6.50"), null, null));

            assertThat(response.custoUnitario()).isEqualByComparingTo("6.50");
            assertThat(pao.getCusto()).isEqualByComparingTo("1.30"); // 2 kg x 6,50 / 10
        }

        @Test
        @DisplayName("custo igual ao atual (5 = 5.00): não procura produtos para recalcular")
        void custoIgual() {
            insumoExiste();

            insumoService.atualizarParcialmente(id, new InsumoPatchRequest(null, null, new BigDecimal("5"), null, null));

            verify(produtoRepository, never()).findUsandoInsumo(any());
        }

        @Test
        @DisplayName("unidade nova em insumo que ninguém usa: muda")
        void unidadeLivre() {
            insumoExiste();
            when(itemReceitaRepository.existsByInsumoId(id)).thenReturn(false);

            InsumoResponse response = insumoService.atualizarParcialmente(id,
                    new InsumoPatchRequest(null, UnidadeMedida.SACO, null, 80, null));

            assertThat(response.unidade()).isEqualTo(UnidadeMedida.SACO);
            assertThat(response.itensPorSaco()).isEqualTo(80);
        }

        @Test
        @DisplayName("virar SACO sem informar itens por saco: 422")
        void viraSacoSemItens() {
            insumoExiste();
            when(itemReceitaRepository.existsByInsumoId(id)).thenReturn(false);

            assertThatThrownBy(() -> insumoService.atualizarParcialmente(id,
                    new InsumoPatchRequest(null, UnidadeMedida.SACO, null, null, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("itens");
        }

        @Test
        @DisplayName("itens por saco em insumo que não é saco: ignorado (fica nulo)")
        void itensPorSacoForaDeSaco() {
            insumoExiste();

            InsumoResponse response = insumoService.atualizarParcialmente(id, new InsumoPatchRequest(null, null, null, 30, null));

            assertThat(response.itensPorSaco()).isNull();
        }

        @Test
        @DisplayName("saco passa a vir com menos itens: o custo dos produtos de fornecedor sobe")
        void sacoComMenosItens() {
            Insumo saco = insumo(id, "Saco de massa", UnidadeMedida.SACO, "48.00");
            saco.setItensPorSaco(80);
            when(insumoRepository.findById(id)).thenReturn(Optional.of(saco));
            Produto pao = new Produto();
            pao.setNome("Pão francês");
            pao.setPreco(new BigDecimal("0.80"));
            pao.setOrigem(OrigemProduto.FORNECEDOR);
            pao.setRendimento(80);
            pao.getReceita().add(new ItemReceita(pao, saco, BigDecimal.ONE));
            pao.setCusto(new BigDecimal("0.60"));
            when(produtoRepository.findUsandoInsumo(id)).thenReturn(List.of(pao));

            InsumoResponse response = insumoService.atualizarParcialmente(id, new InsumoPatchRequest(null, null, null, 60, null));

            assertThat(response.itensPorSaco()).isEqualTo(60);
            assertThat(pao.getRendimento()).isEqualTo(60);
            assertThat(pao.getCusto()).isEqualByComparingTo("0.80"); // 48,00 ÷ 60
        }

        @Test
        @DisplayName("unidade nova em insumo já usado em ficha técnica: 422 e mantém KG")
        void unidadeBloqueada() {
            insumoExiste();
            when(itemReceitaRepository.existsByInsumoId(id)).thenReturn(true);

            assertThatThrownBy(() -> insumoService.atualizarParcialmente(id,
                    new InsumoPatchRequest(null, UnidadeMedida.UN, null, null, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Farinha de trigo");

            assertThat(farinha.getUnidade()).isEqualTo(UnidadeMedida.KG);
        }

        @Test
        @DisplayName("mesma unidade que já tem: nem consulta as fichas técnicas")
        void mesmaUnidade() {
            insumoExiste();

            insumoService.atualizarParcialmente(id, new InsumoPatchRequest(null, UnidadeMedida.KG, null, null, null));

            verify(itemReceitaRepository, never()).existsByInsumoId(any());
        }

        @Test
        @DisplayName("corpo vazio: não altera nada")
        void nada() {
            insumoExiste();

            InsumoResponse response = insumoService.atualizarParcialmente(id, new InsumoPatchRequest(null, null, null, null, null));

            assertThat(response.nome()).isEqualTo("Farinha de trigo");
            verify(insumoRepository, never()).existsByNomeIgnoreCaseAndIdNot(anyString(), any());
            verify(produtoRepository, never()).findUsandoInsumo(any());
        }

        @Test
        @DisplayName("botijão passa a render menos fornadas: o custo dos produtos que usam gás sobe")
        void botijaoRendeMenos() {
            Insumo gas = insumo(id, "Gás", UnidadeMedida.GAS, "120.00");
            gas.setFornadasPorBotijao(20);
            when(insumoRepository.findById(id)).thenReturn(Optional.of(gas));
            Produto pao = new Produto();
            pao.setNome("Pão caseiro");
            pao.setPreco(new BigDecimal("8.00"));
            pao.setRendimento(10);
            pao.getReceita().add(new ItemReceita(pao, gas, BigDecimal.ONE));
            pao.setCusto(new BigDecimal("0.60"));
            when(produtoRepository.findUsandoInsumo(id)).thenReturn(List.of(pao));

            insumoService.atualizarParcialmente(id, new InsumoPatchRequest(null, null, null, null, 15));

            assertThat(pao.getCusto()).isEqualByComparingTo("0.80"); // 120 ÷ 15 = 8,00 por fornada ÷ 10 itens
        }

        @Test
        @DisplayName("id inexistente: 404")
        void inexistente() {
            when(insumoRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> insumoService.atualizarParcialmente(id, new InsumoPatchRequest("X", null, null, null, null)))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessage("Insumo não encontrado");
        }
    }
}
