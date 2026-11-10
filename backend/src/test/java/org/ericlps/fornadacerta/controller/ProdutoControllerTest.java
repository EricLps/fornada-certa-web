package org.ericlps.fornadacerta.controller;

import org.ericlps.fornadacerta.dto.ProdutoPatchRequest;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.service.ProdutoService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sobe só a camada web (rotas, JSON, validação), com o service simulado e sem banco.
@WebMvcTest(ProdutoController.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProdutoService produtoService;

    // =====================================================================
    @Nested
    @DisplayName("POST /produto")
    class Cadastrar {

        private void postJson(String json, int statusEsperado) throws Exception {
            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().is(statusEsperado));
        }

        @Test
        @DisplayName("dados válidos: 201 com o produto criado")
        void valido() throws Exception {
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
        @DisplayName("repassa ao service exatamente o que veio no JSON")
        void repassaRequest() throws Exception {
            when(produtoService.cadastrarProduto(any(ProdutoRequest.class)))
                    .thenReturn(new ProdutoResponse(UUID.randomUUID(), "Sonho", new BigDecimal("6.50"), true));

            postJson("""
                    { "nome": "Sonho", "preco": 6.50 }
                    """, 201);

            ArgumentCaptor<ProdutoRequest> captor = ArgumentCaptor.forClass(ProdutoRequest.class);
            verify(produtoService).cadastrarProduto(captor.capture());
            assertThat(captor.getValue().nome()).isEqualTo("Sonho");
            assertThat(captor.getValue().preco()).isEqualByComparingTo("6.50");
        }

        @Test
        @DisplayName("nome vazio, só com espaços ou ausente: 400 sem chamar o service")
        void nomeInvalido() throws Exception {
            postJson("""
                    { "nome": "", "preco": 0.80 }
                    """, 400);
            postJson("""
                    { "nome": "   ", "preco": 0.80 }
                    """, 400);
            postJson("""
                    { "preco": 0.80 }
                    """, 400);

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @DisplayName("preço ausente, zero, negativo ou texto: 400 sem chamar o service")
        void precoInvalido() throws Exception {
            postJson("""
                    { "nome": "Pão francês" }
                    """, 400);
            postJson("""
                    { "nome": "Pão francês", "preco": 0 }
                    """, 400);
            postJson("""
                    { "nome": "Pão francês", "preco": -1 }
                    """, 400);
            postJson("""
                    { "nome": "Pão francês", "preco": "abc" }
                    """, 400);

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @DisplayName("sem corpo ou JSON malformado: 400")
        void corpoInvalido() throws Exception {
            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
            postJson("{ \"nome\": \"Pão\", ", 400);

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @DisplayName("corpo que não é JSON: 415 Unsupported Media Type")
        void naoEhJson() throws Exception {
            mockMvc.perform(post("/produto").contentType(MediaType.TEXT_PLAIN).content("Pão francês"))
                    .andExpect(status().isUnsupportedMediaType());

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @Disabled("Ative quando o @RestControllerAdvice converter nome duplicado em 409")
        @DisplayName("nome duplicado: 409 Conflict")
        void nomeDuplicado() throws Exception {
            when(produtoService.cadastrarProduto(any(ProdutoRequest.class)))
                    .thenThrow(new IllegalArgumentException("Já existe um produto cadastrado como: Pão francês"));

            postJson("""
                    { "nome": "Pão francês", "preco": 0.80 }
                    """, 409);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("PATCH /produto/{id}")
    class AtualizarParcialmente {

        private final UUID id = UUID.randomUUID();

        private void patchJson(Object idNaUrl, String json, int statusEsperado) throws Exception {
            mockMvc.perform(patch("/produto/{id}", idNaUrl).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().is(statusEsperado));
        }

        @Test
        @DisplayName("só um campo: 200 com o produto atualizado")
        void soUmCampo() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenReturn(new ProdutoResponse(id, "Pão de sal", new BigDecimal("0.80"), true));

            mockMvc.perform(patch("/produto/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "nome": "Pão de sal" }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.nome").value("Pão de sal"))
                    .andExpect(jsonPath("$.valor").value(0.8))
                    .andExpect(jsonPath("$.ativo").value(true));
        }

        @Test
        @DisplayName("só status (botão da listagem): campos ausentes chegam nulos ao service")
        void soStatus() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenReturn(new ProdutoResponse(id, "Pão francês", new BigDecimal("0.80"), false));

            patchJson(id, """
                    { "status": false }
                    """, 200);

            ArgumentCaptor<ProdutoPatchRequest> captor = ArgumentCaptor.forClass(ProdutoPatchRequest.class);
            verify(produtoService).atualizarParcialmente(eq(id), captor.capture());
            assertThat(captor.getValue().status()).isFalse();
            assertThat(captor.getValue().nome()).isNull();
            assertThat(captor.getValue().preco()).isNull();
        }

        @Test
        @DisplayName("todos os campos juntos: 200")
        void todosOsCampos() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenReturn(new ProdutoResponse(id, "Pão integral", new BigDecimal("1.20"), false));

            patchJson(id, """
                    { "nome": "Pão integral", "preco": 1.20, "status": false }
                    """, 200);
        }

        @Test
        @DisplayName("corpo vazio {}: 200, pois nada para alterar é válido")
        void corpoVazio() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenReturn(new ProdutoResponse(id, "Pão francês", new BigDecimal("0.80"), true));

            patchJson(id, "{}", 200);
        }

        @Test
        @DisplayName("nome vazio ou acima de 100 caracteres: 400 sem chamar o service")
        void nomeInvalido() throws Exception {
            patchJson(id, """
                    { "nome": "" }
                    """, 400);
            patchJson(id, "{ \"nome\": \"" + "a".repeat(101) + "\" }", 400);

            verify(produtoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("preço zero, negativo ou texto: 400 sem chamar o service")
        void precoInvalido() throws Exception {
            patchJson(id, """
                    { "preco": 0 }
                    """, 400);
            patchJson(id, """
                    { "preco": -5 }
                    """, 400);
            patchJson(id, """
                    { "preco": "abc" }
                    """, 400);

            verify(produtoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("status que não é booleano: 400 sem chamar o service")
        void statusInvalido() throws Exception {
            patchJson(id, """
                    { "status": "talvez" }
                    """, 400);

            verify(produtoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("id que não é UUID: 400 sem chamar o service")
        void idInvalido() throws Exception {
            patchJson("abc", """
                    { "nome": "Pão de sal" }
                    """, 400);

            verify(produtoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("sem corpo: 400")
        void semCorpo() throws Exception {
            mockMvc.perform(patch("/produto/{id}", id).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("produto inexistente: 404")
        void inexistente() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));

            patchJson(id, """
                    { "nome": "Pão de sal" }
                    """, 404);
        }

        @Test
        @Disabled("Ative quando o @RestControllerAdvice converter nome duplicado em 409")
        @DisplayName("nome de outro produto: 409 Conflict")
        void nomeDuplicado() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenThrow(new IllegalArgumentException("Já existe um produto cadastrado como: Sonho"));

            patchJson(id, """
                    { "nome": "Sonho" }
                    """, 409);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /produto")
    class Listar {

        private final List<ProdutoResponse> lista = List.of(
                new ProdutoResponse(UUID.randomUUID(), "Baguete", new BigDecimal("9.90"), true),
                new ProdutoResponse(UUID.randomUUID(), "Sonho", new BigDecimal("6.50"), false));

        @Test
        @DisplayName("sem parâmetros: 200, todos os produtos, ordem padrão ASC")
        void semParametros() throws Exception {
            when(produtoService.listar(null, "ASC")).thenReturn(lista);

            mockMvc.perform(get("/produto"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].nome").value("Baguete"))
                    .andExpect(jsonPath("$[1].nome").value("Sonho"));

            verify(produtoService).listar(null, "ASC");
        }

        @Test
        @DisplayName("status=true: repassa o filtro de ativos")
        void soAtivos() throws Exception {
            when(produtoService.listar(true, "ASC")).thenReturn(List.of(lista.get(0)));

            mockMvc.perform(get("/produto").param("status", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].ativo").value(true));
        }

        @Test
        @DisplayName("status=false: repassa o filtro de inativos")
        void soInativos() throws Exception {
            when(produtoService.listar(false, "ASC")).thenReturn(List.of(lista.get(1)));

            mockMvc.perform(get("/produto").param("status", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].ativo").value(false));
        }

        @Test
        @DisplayName("ordem=desc com status: repassa os dois parâmetros")
        void ordemDescComStatus() throws Exception {
            when(produtoService.listar(true, "desc")).thenReturn(List.of(lista.get(0)));

            mockMvc.perform(get("/produto").param("status", "true").param("ordem", "desc"))
                    .andExpect(status().isOk());

            verify(produtoService).listar(true, "desc");
        }

        @Test
        @DisplayName("nenhum produto: 200 com lista vazia")
        void vazio() throws Exception {
            when(produtoService.listar(null, "ASC")).thenReturn(List.of());

            mockMvc.perform(get("/produto"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("status que não é booleano: 400 sem chamar o service")
        void statusInvalido() throws Exception {
            mockMvc.perform(get("/produto").param("status", "abc"))
                    .andExpect(status().isBadRequest());

            verify(produtoService, never()).listar(any(), anyString());
        }

        @Test
        @Disabled("Ative quando o @RestControllerAdvice converter ordem inválida em 400")
        @DisplayName("ordem inválida: 400 Bad Request")
        void ordemInvalida() throws Exception {
            when(produtoService.listar(null, "xyz"))
                    .thenThrow(new IllegalArgumentException("Invalid value 'xyz' for orders given"));

            mockMvc.perform(get("/produto").param("ordem", "xyz"))
                    .andExpect(status().isBadRequest());
        }
    }
}
