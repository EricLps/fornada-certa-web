package org.ericlps.fornadacerta.service;

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

    @Test
    @DisplayName("atualizarProduto altera o produto existente, mantendo id e status")
    void atualizarProduto() {
        UUID id = UUID.randomUUID();
        Produto existente = new Produto();
        existente.setId(id);
        existente.setNome("Pão francês");
        existente.setPreco(new BigDecimal("0.80"));
        existente.setStatus(false);
        when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

        ProdutoResponse response = produtoService.atualizarProduto(
                id, new ProdutoRequest("Pão francês integral", new BigDecimal("1.20")));

        // o objeto que veio do banco foi alterado (é isso que vira UPDATE)
        assertThat(existente.getId()).isEqualTo(id);
        assertThat(existente.getNome()).isEqualTo("Pão francês integral");
        assertThat(existente.getPreco()).isEqualByComparingTo("1.20");
        assertThat(existente.getStatus()).isFalse();

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("Pão francês integral");
        assertThat(response.valor()).isEqualByComparingTo("1.20");
        assertThat(response.ativo()).isFalse();
    }

    @Test
    @DisplayName("atualizarProduto não cria produto novo (não salva entity sem id)")
    void atualizarProdutoNaoCriaNovo() {
        UUID id = UUID.randomUUID();
        Produto existente = new Produto();
        existente.setId(id);
        existente.setNome("Sonho");
        existente.setPreco(new BigDecimal("6.50"));
        when(produtoRepository.findById(id)).thenReturn(Optional.of(existente));

        produtoService.atualizarProduto(id, new ProdutoRequest("Sonho de creme", new BigDecimal("7.00")));

        // se alguém voltar a usar toEntity aqui, o save com um Produto novo faria um INSERT
        ArgumentCaptor<Produto> captor = ArgumentCaptor.forClass(Produto.class);
        verify(produtoRepository, atMost(1)).save(captor.capture());
        captor.getAllValues().forEach(salvo -> assertThat(salvo.getId()).isEqualTo(id));
    }

    @Test
    @DisplayName("atualizarProduto com id inexistente lança 404 e não salva nada")
    void atualizarProdutoInexistente() {
        UUID id = UUID.randomUUID();
        when(produtoRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.atualizarProduto(
                id, new ProdutoRequest("Qualquer", new BigDecimal("1.00"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(produtoRepository, never()).save(any());
    }
}
