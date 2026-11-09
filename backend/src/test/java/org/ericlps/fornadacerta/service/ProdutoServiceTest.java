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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
