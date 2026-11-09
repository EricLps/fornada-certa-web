package org.ericlps.fornadacerta.service;

import org.ericlps.fornadacerta.dto.ProdutoPatchRequest;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.mapper.ProdutoMapper;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    private ProdutoService produtoService;

    @BeforeEach
    void setUp() {
        // Repository é falso (mock): o teste não precisa de banco.
        // Mapper é o real: assim o teste também cobre a conversão.
        produtoService = new ProdutoService(produtoRepository, new ProdutoMapper());
    }

    @Test
    @DisplayName("cadastrarProduto salva o produto e devolve o response com o id gerado")
    void cadastrarProduto() {
        UUID idGerado = UUID.randomUUID();
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
    @DisplayName("cadastrarProduto envia ao repository uma entity nova, ativa e com os dados do request")
    void cadastrarProdutoEnviaEntityCorreta() {
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

    private Produto produtoExistente(UUID id) {
        Produto existente = new Produto();
        existente.setId(id);
        existente.setNome("Pão francês");
        existente.setPreco(new BigDecimal("0.80"));
        existente.setStatus(true);
        return existente;
    }

    @Test
    @DisplayName("atualizarParcialmente altera só o nome e mantém preço e status")
    void atualizarParcialmenteSoNome() {
        UUID id = UUID.randomUUID();
        Produto existente = produtoExistente(id);
        when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

        ProdutoResponse response = produtoService.atualizarParcialmente(
                id, new ProdutoPatchRequest("Pão de sal", null, null));

        assertThat(existente.getNome()).isEqualTo("Pão de sal");
        assertThat(existente.getPreco()).isEqualByComparingTo("0.80");
        assertThat(existente.getStatus()).isTrue();

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("Pão de sal");
        assertThat(response.valor()).isEqualByComparingTo("0.80");
        assertThat(response.ativo()).isTrue();
    }

    @Test
    @DisplayName("atualizarParcialmente altera só o preço e mantém nome e status")
    void atualizarParcialmenteSoPreco() {
        UUID id = UUID.randomUUID();
        Produto existente = produtoExistente(id);
        when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

        ProdutoResponse response = produtoService.atualizarParcialmente(
                id, new ProdutoPatchRequest(null, new BigDecimal("1.50"), null));

        assertThat(existente.getNome()).isEqualTo("Pão francês");
        assertThat(existente.getPreco()).isEqualByComparingTo("1.50");
        assertThat(existente.getStatus()).isTrue();

        assertThat(response.nome()).isEqualTo("Pão francês");
        assertThat(response.valor()).isEqualByComparingTo("1.50");
        assertThat(response.ativo()).isTrue();
    }

    @Test
    @DisplayName("atualizarParcialmente altera só o status e mantém nome e preço")
    void atualizarParcialmenteSoStatus() {
        UUID id = UUID.randomUUID();
        Produto existente = produtoExistente(id);
        when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

        ProdutoResponse response = produtoService.atualizarParcialmente(
                id, new ProdutoPatchRequest(null, null, false));

        assertThat(existente.getNome()).isEqualTo("Pão francês");
        assertThat(existente.getPreco()).isEqualByComparingTo("0.80");
        assertThat(existente.getStatus()).isFalse();

        assertThat(response.ativo()).isFalse();
    }

    @Test
    @DisplayName("atualizarParcialmente altera todos os campos enviados")
    void atualizarParcialmenteTodosOsCampos() {
        UUID id = UUID.randomUUID();
        Produto existente = produtoExistente(id);
        when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

        ProdutoResponse response = produtoService.atualizarParcialmente(
                id, new ProdutoPatchRequest("Pão integral", new BigDecimal("1.20"), false));

        assertThat(existente.getId()).isEqualTo(id);
        assertThat(existente.getNome()).isEqualTo("Pão integral");
        assertThat(existente.getPreco()).isEqualByComparingTo("1.20");
        assertThat(existente.getStatus()).isFalse();

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("Pão integral");
        assertThat(response.valor()).isEqualByComparingTo("1.20");
        assertThat(response.ativo()).isFalse();
    }

    @Test
    @DisplayName("atualizarParcialmente com corpo vazio não altera nada")
    void atualizarParcialmenteSemCampos() {
        UUID id = UUID.randomUUID();
        Produto existente = produtoExistente(id);
        when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

        ProdutoResponse response = produtoService.atualizarParcialmente(
                id, new ProdutoPatchRequest(null, null, null));

        assertThat(existente.getNome()).isEqualTo("Pão francês");
        assertThat(existente.getPreco()).isEqualByComparingTo("0.80");
        assertThat(existente.getStatus()).isTrue();

        assertThat(response.nome()).isEqualTo("Pão francês");
        assertThat(response.valor()).isEqualByComparingTo("0.80");
        assertThat(response.ativo()).isTrue();
    }

    @Test
    @DisplayName("atualizarParcialmente não cria produto novo (não salva entity sem id)")
    void atualizarParcialmenteNaoCriaNovo() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findById(id)).thenReturn(Optional.of(produtoExistente(id)));

        produtoService.atualizarParcialmente(id, new ProdutoPatchRequest("Sonho", null, null));

        ArgumentCaptor<Produto> captor = ArgumentCaptor.forClass(Produto.class);
        verify(produtoRepository, atMost(1)).save(captor.capture());
        captor.getAllValues().forEach(salvo -> assertThat(salvo.getId()).isEqualTo(id));
    }

    @Test
    @DisplayName("atualizarParcialmente com id inexistente lança 404 e não salva nada")
    void atualizarParcialmenteInexistente() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.atualizarParcialmente(
                id, new ProdutoPatchRequest("Qualquer", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(produtoRepository, never()).save(any());
    }

    private Produto produto(String nome, String preco, boolean status) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome(nome);
        produto.setPreco(new BigDecimal(preco));
        produto.setStatus(status);
        return produto;
    }

    @Test
    @DisplayName("listar sem filtro devolve todos os produtos, ativos e inativos")
    void listarSemFiltro() {
        Produto ativo = produto("Pão francês", "0.80", true);
        Produto inativo = produto("Sonho", "6.50", false);
        when(produtoRepository.findAll()).thenReturn(List.of(ativo, inativo));

        List<ProdutoResponse> response = produtoService.listar(null);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).id()).isEqualTo(ativo.getId());
        assertThat(response.get(0).nome()).isEqualTo("Pão francês");
        assertThat(response.get(0).valor()).isEqualByComparingTo("0.80");
        assertThat(response.get(0).ativo()).isTrue();
        assertThat(response.get(1).id()).isEqualTo(inativo.getId());
        assertThat(response.get(1).ativo()).isFalse();

        verify(produtoRepository, never()).findByStatus(any());
    }

    @Test
    @DisplayName("listar com status true busca só os ativos")
    void listarSoAtivos() {
        Produto ativo = produto("Pão francês", "0.80", true);
        when(produtoRepository.findByStatus(true)).thenReturn(List.of(ativo));

        List<ProdutoResponse> response = produtoService.listar(true);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).nome()).isEqualTo("Pão francês");
        assertThat(response.get(0).ativo()).isTrue();

        verify(produtoRepository, never()).findAll();
    }

    @Test
    @DisplayName("listar com status false busca só os inativos")
    void listarSoInativos() {
        Produto inativo = produto("Sonho", "6.50", false);
        when(produtoRepository.findByStatus(false)).thenReturn(List.of(inativo));

        List<ProdutoResponse> response = produtoService.listar(false);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).nome()).isEqualTo("Sonho");
        assertThat(response.get(0).ativo()).isFalse();

        verify(produtoRepository, never()).findAll();
    }

    @Test
    @DisplayName("listar sem resultados devolve lista vazia")
    void listarVazio() {
        when(produtoRepository.findByStatus(true)).thenReturn(List.of());

        assertThat(produtoService.listar(true)).isEmpty();
    }
}
