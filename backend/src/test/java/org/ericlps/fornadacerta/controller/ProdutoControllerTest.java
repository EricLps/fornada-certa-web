package org.ericlps.fornadacerta.controller;

import org.ericlps.fornadacerta.dto.ProdutoPatchRequest;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.service.ProdutoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    @DisplayName("PATCH /produto/{id} com só um campo devolve 200 e o produto atualizado")
    void atualizarParcialmenteValido() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                .thenReturn(new ProdutoResponse(id, "Pão francês", new BigDecimal("1.50"), true));

        mockMvc.perform(patch("/produto/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "preco": 1.50 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.nome").value("Pão francês"))
                .andExpect(jsonPath("$.valor").value(1.5))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("PATCH /produto/{id} repassa ao service só os campos enviados")
    void atualizarParcialmenteRepassaCampos() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                .thenReturn(new ProdutoResponse(id, "Pão francês", new BigDecimal("0.80"), false));

        mockMvc.perform(patch("/produto/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        ArgumentCaptor<ProdutoPatchRequest> captor = ArgumentCaptor.forClass(ProdutoPatchRequest.class);
        verify(produtoService).atualizarParcialmente(eq(id), captor.capture());
        assertThat(captor.getValue().nome()).isNull();
        assertThat(captor.getValue().preco()).isNull();
        assertThat(captor.getValue().status()).isFalse();
    }

    @Test
    @DisplayName("PATCH /produto/{id} com corpo vazio ({}) devolve 200")
    void atualizarParcialmenteCorpoVazio() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                .thenReturn(new ProdutoResponse(id, "Pão francês", new BigDecimal("0.80"), true));

        mockMvc.perform(patch("/produto/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /produto/{id} de produto inexistente devolve 404")
    void atualizarParcialmenteInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));

        mockMvc.perform(patch("/produto/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Pão integral" }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /produto/{id} com nome vazio devolve 400 e não chama o service")
    void atualizarParcialmenteNomeVazio() throws Exception {
        mockMvc.perform(patch("/produto/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "" }
                                """))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).atualizarParcialmente(any(), any());
    }

    @Test
    @DisplayName("PATCH /produto/{id} com preço negativo ou zero devolve 400 e não chama o service")
    void atualizarParcialmentePrecoInvalido() throws Exception {
        mockMvc.perform(patch("/produto/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "preco": -1 }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/produto/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "preco": 0 }
                                """))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).atualizarParcialmente(any(), any());
    }

    @Test
    @DisplayName("PATCH /produto/{id} com nome acima de 100 caracteres devolve 400")
    void atualizarParcialmenteNomeLongo() throws Exception {
        String nomeLongo = "a".repeat(101);

        mockMvc.perform(patch("/produto/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nome\": \"" + nomeLongo + "\" }"))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).atualizarParcialmente(any(), any());
    }

    @Test
    @DisplayName("PATCH /produto/{id} com id que não é UUID devolve 400")
    void atualizarParcialmenteIdInvalido() throws Exception {
        mockMvc.perform(patch("/produto/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "nome": "Pão integral" }
                                """))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).atualizarParcialmente(any(), any());
    }

    @Test
    @DisplayName("PATCH /produto/{id} sem corpo devolve 400")
    void atualizarParcialmenteSemCorpo() throws Exception {
        mockMvc.perform(patch("/produto/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).atualizarParcialmente(any(), any());
    }

    @Test
    @DisplayName("GET /produto sem filtro devolve 200 e a lista completa")
    void listarSemFiltro() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(produtoService.listar(null)).thenReturn(List.of(
                new ProdutoResponse(id1, "Pão francês", new BigDecimal("0.80"), true),
                new ProdutoResponse(id2, "Sonho", new BigDecimal("6.50"), false)));

        mockMvc.perform(get("/produto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(id1.toString()))
                .andExpect(jsonPath("$[0].nome").value("Pão francês"))
                .andExpect(jsonPath("$[0].valor").value(0.8))
                .andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[1].id").value(id2.toString()))
                .andExpect(jsonPath("$[1].ativo").value(false));

        verify(produtoService).listar(null);
    }

    @Test
    @DisplayName("GET /produto?status=true repassa true ao service")
    void listarSoAtivos() throws Exception {
        when(produtoService.listar(true)).thenReturn(List.of(
                new ProdutoResponse(UUID.randomUUID(), "Pão francês", new BigDecimal("0.80"), true)));

        mockMvc.perform(get("/produto").param("status", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ativo").value(true));

        verify(produtoService).listar(true);
    }

    @Test
    @DisplayName("GET /produto?status=false repassa false ao service")
    void listarSoInativos() throws Exception {
        when(produtoService.listar(false)).thenReturn(List.of(
                new ProdutoResponse(UUID.randomUUID(), "Sonho", new BigDecimal("6.50"), false)));

        mockMvc.perform(get("/produto").param("status", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ativo").value(false));

        verify(produtoService).listar(false);
    }

    @Test
    @DisplayName("GET /produto sem produtos devolve 200 e lista vazia")
    void listarVazio() throws Exception {
        when(produtoService.listar(null)).thenReturn(List.of());

        mockMvc.perform(get("/produto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /produto?status=abc devolve 400 e não chama o service")
    void listarStatusInvalido() throws Exception {
        mockMvc.perform(get("/produto").param("status", "abc"))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).listar(any());
    }
}
