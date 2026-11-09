package org.ericlps.fornadacerta.controller;

import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.service.ProdutoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sobe só a camada web (controller, JSON, validação), sem banco.
@WebMvcTest(ProdutoController.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProdutoService produtoService;

    @Test
    @DisplayName("POST /produto com dados válidos devolve 201 e o produto criado")
    void cadastrarValido() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.cadastrarProduto(any(ProdutoRequest.class)))
                .thenReturn(new ProdutoResponse(id, "Pão francês", new BigDecimal("0.80"), true));

        mockMvc.perform(post("/produto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Pão francês", "preco": 0.80 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nome").value("Pão francês"))
                .andExpect(jsonPath("$.valor").value(0.8))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("POST /produto sem nome devolve 400 e não chama o service")
    void cadastrarSemNome() throws Exception {
        mockMvc.perform(post("/produto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "", "preco": 0.80 }
                                """))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).cadastrarProduto(any());
    }

    @Test
    @DisplayName("POST /produto sem preço devolve 400 e não chama o service")
    void cadastrarSemPreco() throws Exception {
        mockMvc.perform(post("/produto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Pão francês" }
                                """))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).cadastrarProduto(any());
    }

    @Test
    @DisplayName("POST /produto com preço negativo ou zero devolve 400")
    void cadastrarPrecoInvalido() throws Exception {
        mockMvc.perform(post("/produto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Pão francês", "preco": -1 }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/produto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Pão francês", "preco": 0 }
                                """))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).cadastrarProduto(any());
    }

    @Test
    @DisplayName("POST /produto sem corpo devolve 400")
    void cadastrarSemCorpo() throws Exception {
        mockMvc.perform(post("/produto")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /produto/{id} com dados válidos devolve 200 e o produto atualizado")
    void atualizarValido() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.atualizarProduto(eq(id), any(ProdutoRequest.class)))
                .thenReturn(new ProdutoResponse(id, "Pão integral", new BigDecimal("1.20"), true));

        mockMvc.perform(put("/produto/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Pão integral", "preco": 1.20 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nome").value("Pão integral"))
                .andExpect(jsonPath("$.valor").value(1.2))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("PUT /produto/{id} de produto inexistente devolve 404")
    void atualizarInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.atualizarProduto(eq(id), any(ProdutoRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));

        mockMvc.perform(put("/produto/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Pão integral", "preco": 1.20 }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /produto/{id} com dados inválidos devolve 400 e não chama o service")
    void atualizarInvalido() throws Exception {
        mockMvc.perform(put("/produto/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "", "preco": -5 }
                                """))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).atualizarProduto(any(), any());
    }

    @Test
    @DisplayName("PUT /produto/{id} com id que não é UUID devolve 400")
    void atualizarIdInvalido() throws Exception {
        mockMvc.perform(put("/produto/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Pão integral", "preco": 1.20 }
                                """))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).atualizarProduto(any(), any());
    }
}
