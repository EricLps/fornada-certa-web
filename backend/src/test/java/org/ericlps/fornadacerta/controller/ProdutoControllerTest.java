package org.ericlps.fornadacerta.controller;

import org.ericlps.fornadacerta.dto.ItemReceitaResponse;
import org.ericlps.fornadacerta.dto.ProdutoDetalheResponse;
import org.ericlps.fornadacerta.dto.ProdutoPatchRequest;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.enums.OrigemProduto;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.service.ProdutoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sobe só a camada web (rotas, JSON, validação), com o service simulado e sem banco.
@WebMvcTest(ProdutoController.class)
class ProdutoControllerTest {

    private static final UUID INSUMO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // Item de ficha técnica válido, para reaproveitar nos JSONs
    private static final String ITENS_OK = "[{ \"insumoId\": \"" + INSUMO + "\", \"quantidade\": 1 }]";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProdutoService produtoService;

    private static ProdutoResponse response(UUID id, String nome, String preco, String custo, boolean ativo) {
        return new ProdutoResponse(id, nome, new BigDecimal(preco), new BigDecimal(custo), ativo,
                OrigemProduto.PRODUCAO_PROPRIA, 10);
    }

    // Monta um JSON de cadastro; passe null para deixar um campo de fora
    private static String cadastroJson(String nome, String preco, String origem, String rendimento, String itens) {
        StringBuilder json = new StringBuilder("{");
        if (nome != null) json.append("\"nome\": ").append(nome).append(",");
        if (preco != null) json.append("\"preco\": ").append(preco).append(",");
        if (origem != null) json.append("\"origem\": ").append(origem).append(",");
        if (rendimento != null) json.append("\"rendimento\": ").append(rendimento).append(",");
        if (itens != null) json.append("\"itens\": ").append(itens).append(",");
        if (json.charAt(json.length() - 1) == ',') json.setLength(json.length() - 1);
        return json.append("}").toString();
    }

    private static String cadastroValido() {
        return cadastroJson("\"Pão francês\"", "0.80", "\"FORNECEDOR\"", "80", ITENS_OK);
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /produto")
    class Cadastrar {

        private void postJson(String json, int statusEsperado) throws Exception {
            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().is(statusEsperado));
        }

        @Test
        @DisplayName("dados válidos: 201 com o produto criado, origem e rendimento")
        void valido() throws Exception {
            UUID id = UUID.randomUUID();
            when(produtoService.cadastrarProduto(any(ProdutoRequest.class)))
                    .thenReturn(new ProdutoResponse(id, "Pão francês", new BigDecimal("0.80"), new BigDecimal("0.60"),
                            true, OrigemProduto.FORNECEDOR, 80));

            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON).content(cadastroValido()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.nome").value("Pão francês"))
                    .andExpect(jsonPath("$.preco").value(0.8))
                    .andExpect(jsonPath("$.custo").value(0.6))
                    .andExpect(jsonPath("$.ativo").value(true))
                    .andExpect(jsonPath("$.origem").value("FORNECEDOR"))
                    .andExpect(jsonPath("$.rendimento").value(80));
        }

        @Test
        @DisplayName("repassa ao service exatamente o que veio no JSON, incluindo os itens")
        void repassaRequest() throws Exception {
            when(produtoService.cadastrarProduto(any(ProdutoRequest.class)))
                    .thenReturn(response(UUID.randomUUID(), "Sonho", "6.50", "3.25", true));

            postJson(cadastroJson("\"Sonho\"", "6.50", "\"PRODUCAO_PROPRIA\"", "20",
                    "[{ \"insumoId\": \"" + INSUMO + "\", \"quantidade\": 0.250 }]"), 201);

            ArgumentCaptor<ProdutoRequest> captor = ArgumentCaptor.forClass(ProdutoRequest.class);
            verify(produtoService).cadastrarProduto(captor.capture());
            ProdutoRequest request = captor.getValue();
            assertThat(request.nome()).isEqualTo("Sonho");
            assertThat(request.preco()).isEqualByComparingTo("6.50");
            assertThat(request.origem()).isEqualTo(OrigemProduto.PRODUCAO_PROPRIA);
            assertThat(request.rendimento()).isEqualTo(20);
            assertThat(request.itens()).singleElement().satisfies(item -> {
                assertThat(item.insumoId()).isEqualTo(INSUMO);
                assertThat(item.quantidade()).isEqualByComparingTo("0.25");
            });
        }

        @Test
        @DisplayName("nome vazio, só com espaços ou ausente: 400 sem chamar o service")
        void nomeInvalido() throws Exception {
            postJson(cadastroJson("\"\"", "0.80", "\"FORNECEDOR\"", "80", ITENS_OK), 400);
            postJson(cadastroJson("\"   \"", "0.80", "\"FORNECEDOR\"", "80", ITENS_OK), 400);
            postJson(cadastroJson(null, "0.80", "\"FORNECEDOR\"", "80", ITENS_OK), 400);

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @DisplayName("preço ausente, zero, negativo ou texto: 400 sem chamar o service")
        void precoInvalido() throws Exception {
            postJson(cadastroJson("\"Pão\"", null, "\"FORNECEDOR\"", "80", ITENS_OK), 400);
            postJson(cadastroJson("\"Pão\"", "0", "\"FORNECEDOR\"", "80", ITENS_OK), 400);
            postJson(cadastroJson("\"Pão\"", "-1", "\"FORNECEDOR\"", "80", ITENS_OK), 400);
            postJson(cadastroJson("\"Pão\"", "\"abc\"", "\"FORNECEDOR\"", "80", ITENS_OK), 400);

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @DisplayName("origem ausente ou fora do enum: 400 sem chamar o service")
        void origemInvalida() throws Exception {
            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON)
                            .content(cadastroJson("\"Pão\"", "0.80", null, "80", ITENS_OK)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.origem").isNotEmpty());
            postJson(cadastroJson("\"Pão\"", "0.80", "\"PADARIA\"", "80", ITENS_OK), 400);

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @DisplayName("rendimento ausente, zero ou negativo: 400 com erros.rendimento")
        void rendimentoInvalido() throws Exception {
            for (String rendimento : new String[]{null, "0", "-3"}) {
                mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON)
                                .content(cadastroJson("\"Pão\"", "0.80", "\"FORNECEDOR\"", rendimento, ITENS_OK)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.erros.rendimento").isNotEmpty())
                        .andExpect(jsonPath("$.erros.nome").doesNotExist());
            }

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @DisplayName("sem itens ou lista vazia: 400 com erros.itens")
        void semItens() throws Exception {
            for (String itens : new String[]{null, "[]"}) {
                mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON)
                                .content(cadastroJson("\"Pão\"", "0.80", "\"FORNECEDOR\"", "80", itens)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.erros.itens").isNotEmpty());
            }

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @DisplayName("item sem insumo ou com quantidade zero/negativa: 400 apontando o campo do item")
        void itemInvalido() throws Exception {
            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON)
                            .content(cadastroJson("\"Pão\"", "0.80", "\"FORNECEDOR\"", "80", "[{ \"quantidade\": 1 }]")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros['itens[0].insumoId']").isNotEmpty());
            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON)
                            .content(cadastroJson("\"Pão\"", "0.80", "\"FORNECEDOR\"", "80",
                                    "[{ \"insumoId\": \"" + INSUMO + "\", \"quantidade\": 0 }]")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros['itens[0].quantidade']").isNotEmpty());
            postJson(cadastroJson("\"Pão\"", "0.80", "\"FORNECEDOR\"", "80",
                    "[{ \"insumoId\": \"" + INSUMO + "\", \"quantidade\": -1 }]"), 400);
            postJson(cadastroJson("\"Pão\"", "0.80", "\"FORNECEDOR\"", "80",
                    "[{ \"insumoId\": \"nao-e-uuid\", \"quantidade\": 1 }]"), 400);

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
        @DisplayName("nome duplicado: 409 com ProblemDetail (título fixo, detalhe vindo do service)")
        void nomeDuplicado() throws Exception {
            when(produtoService.cadastrarProduto(any(ProdutoRequest.class)))
                    .thenThrow(new ConflitoException("Já existe um produto cadastrado como: Pão francês"));

            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON).content(cadastroValido()))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Conflito"))
                    .andExpect(jsonPath("$.detail").value("Já existe um produto cadastrado como: Pão francês"));
        }

        @Test
        @DisplayName("ficha técnica recusada pelo service: 422 com a mensagem da regra")
        void fichaRecusada() throws Exception {
            when(produtoService.cadastrarProduto(any(ProdutoRequest.class)))
                    .thenThrow(new RegraDeNegocioException("Produto de fornecedor usa exatamente um insumo com unidade SACO."));

            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON).content(cadastroValido()))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.title").value("Regra de negócio violada"))
                    .andExpect(jsonPath("$.detail").value("Produto de fornecedor usa exatamente um insumo com unidade SACO."));
        }

        @Test
        @DisplayName("insumo inexistente: 404 com o detalhe vindo do service")
        void insumoInexistente() throws Exception {
            when(produtoService.cadastrarProduto(any(ProdutoRequest.class)))
                    .thenThrow(new RecursoNaoEncontradoException("Insumo não encontrado: " + INSUMO));

            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON).content(cadastroValido()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Insumo não encontrado: " + INSUMO));
        }

        @Test
        @DisplayName("campos inválidos: 400 com o mapa 'erros', uma entrada por campo")
        void errosDeValidacaoPorCampo() throws Exception {
            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON)
                            .content(cadastroJson("\"\"", "-1", "\"FORNECEDOR\"", "80", ITENS_OK)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Dados inválidos"))
                    .andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos."))
                    .andExpect(jsonPath("$.erros.nome").isNotEmpty())
                    .andExpect(jsonPath("$.erros.preco").isNotEmpty())
                    .andExpect(jsonPath("$.erros.rendimento").doesNotExist());

            verify(produtoService, never()).cadastrarProduto(any());
        }

        @Test
        @DisplayName("conflito no banco (DataIntegrityViolation): 409 com mensagem genérica, sem vazar SQL")
        void violacaoDeIntegridade() throws Exception {
            when(produtoService.cadastrarProduto(any(ProdutoRequest.class)))
                    .thenThrow(new DataIntegrityViolationException(
                            "duplicate key value violates unique constraint \"produto_nome_key\" [insert into produto ...]"));

            mockMvc.perform(post("/produto").contentType(MediaType.APPLICATION_JSON).content(cadastroValido()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflito"))
                    .andExpect(jsonPath("$.detail").value("Os dados conflitam com um registro existente."))
                    .andExpect(content().string(not(containsString("produto_nome_key"))))
                    .andExpect(content().string(not(containsString("insert into"))));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /produto/{id}")
    class Detalhar {

        @Test
        @DisplayName("existente: 200 com os itens da ficha técnica")
        void existente() throws Exception {
            UUID id = UUID.randomUUID();
            when(produtoService.detalhar(id)).thenReturn(new ProdutoDetalheResponse(id, "Bolo", new BigDecimal("4.00"),
                    new BigDecimal("1.20"), true, OrigemProduto.PRODUCAO_PROPRIA, 10, List.of(
                    new ItemReceitaResponse(INSUMO, "Fubá", UnidadeMedida.KG, new BigDecimal("0.500"),
                            new BigDecimal("4.90"), new BigDecimal("2.45")))));

            mockMvc.perform(get("/produto/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Bolo"))
                    .andExpect(jsonPath("$.rendimento").value(10))
                    .andExpect(jsonPath("$.itens.length()").value(1))
                    .andExpect(jsonPath("$.itens[0].insumoId").value(INSUMO.toString()))
                    .andExpect(jsonPath("$.itens[0].insumoNome").value("Fubá"))
                    .andExpect(jsonPath("$.itens[0].unidade").value("KG"))
                    .andExpect(jsonPath("$.itens[0].custoParcial").value(2.45));
        }

        @Test
        @DisplayName("inexistente: 404")
        void inexistente() throws Exception {
            UUID id = UUID.randomUUID();
            when(produtoService.detalhar(id)).thenThrow(new RecursoNaoEncontradoException("Produto não encontrado"));

            mockMvc.perform(get("/produto/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Produto não encontrado"));
        }

        @Test
        @DisplayName("id que não é UUID: 400 sem chamar o service")
        void idInvalido() throws Exception {
            mockMvc.perform(get("/produto/{id}", "abc"))
                    .andExpect(status().isBadRequest());

            verify(produtoService, never()).detalhar(any());
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
                    .thenReturn(response(id, "Pão de sal", "0.80", "0.40", true));

            mockMvc.perform(patch("/produto/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"nome\": \"Pão de sal\" }"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.nome").value("Pão de sal"))
                    .andExpect(jsonPath("$.ativo").value(true));
        }

        @Test
        @DisplayName("só status (botão da listagem): campos ausentes chegam nulos ao service")
        void soStatus() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenReturn(response(id, "Pão francês", "0.80", "0.40", false));

            patchJson(id, "{ \"status\": false }", 200);

            ArgumentCaptor<ProdutoPatchRequest> captor = ArgumentCaptor.forClass(ProdutoPatchRequest.class);
            verify(produtoService).atualizarParcialmente(eq(id), captor.capture());
            ProdutoPatchRequest request = captor.getValue();
            assertThat(request.status()).isFalse();
            assertThat(request.nome()).isNull();
            assertThat(request.preco()).isNull();
            assertThat(request.alteraFichaTecnica()).isFalse();
        }

        @Test
        @DisplayName("ficha técnica completa: chega ao service com origem, rendimento e itens")
        void fichaTecnica() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenReturn(response(id, "Pão francês", "0.80", "0.60", true));

            patchJson(id, "{ \"origem\": \"FORNECEDOR\", \"rendimento\": 80, \"itens\": " + ITENS_OK + " }", 200);

            ArgumentCaptor<ProdutoPatchRequest> captor = ArgumentCaptor.forClass(ProdutoPatchRequest.class);
            verify(produtoService).atualizarParcialmente(eq(id), captor.capture());
            assertThat(captor.getValue().fichaTecnicaCompleta()).isTrue();
            assertThat(captor.getValue().itens()).hasSize(1);
        }

        @Test
        @DisplayName("ficha técnica pela metade: o service responde 422")
        void fichaIncompleta() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenThrow(new RegraDeNegocioException("Para alterar a ficha técnica, envie origem, rendimento e itens juntos."));

            mockMvc.perform(patch("/produto/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"rendimento\": 20 }"))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.detail").value("Para alterar a ficha técnica, envie origem, rendimento e itens juntos."));
        }

        @Test
        @DisplayName("itens vazios, rendimento zero ou item inválido: 400 sem chamar o service")
        void fichaInvalida() throws Exception {
            patchJson(id, "{ \"origem\": \"FORNECEDOR\", \"rendimento\": 80, \"itens\": [] }", 400);
            patchJson(id, "{ \"origem\": \"FORNECEDOR\", \"rendimento\": 0, \"itens\": " + ITENS_OK + " }", 400);
            patchJson(id, "{ \"origem\": \"FORNECEDOR\", \"rendimento\": 80, \"itens\": [{ \"quantidade\": 1 }] }", 400);
            patchJson(id, "{ \"origem\": \"OUTRA\" }", 400);

            verify(produtoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("corpo vazio {}: 200, pois nada para alterar é válido")
        void corpoVazio() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenReturn(response(id, "Pão francês", "0.80", "0.40", true));

            patchJson(id, "{}", 200);
        }

        @Test
        @DisplayName("nome vazio ou acima de 100 caracteres: 400 sem chamar o service")
        void nomeInvalido() throws Exception {
            patchJson(id, "{ \"nome\": \"\" }", 400);
            patchJson(id, "{ \"nome\": \"" + "a".repeat(101) + "\" }", 400);

            verify(produtoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("preço zero, negativo ou texto: 400 sem chamar o service")
        void precoInvalido() throws Exception {
            patchJson(id, "{ \"preco\": 0 }", 400);
            patchJson(id, "{ \"preco\": -5 }", 400);
            patchJson(id, "{ \"preco\": \"abc\" }", 400);

            verify(produtoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("status que não é booleano: 400 sem chamar o service")
        void statusInvalido() throws Exception {
            patchJson(id, "{ \"status\": \"talvez\" }", 400);

            verify(produtoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("id que não é UUID: 400 sem chamar o service")
        void idInvalido() throws Exception {
            patchJson("abc", "{ \"nome\": \"Pão de sal\" }", 400);

            verify(produtoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("sem corpo: 400")
        void semCorpo() throws Exception {
            mockMvc.perform(patch("/produto/{id}", id).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("produto inexistente: 404 com ProblemDetail")
        void inexistente() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenThrow(new RecursoNaoEncontradoException("Produto não encontrado"));

            mockMvc.perform(patch("/produto/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"nome\": \"Pão de sal\" }"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                    .andExpect(jsonPath("$.detail").value("Produto não encontrado"));
        }

        @Test
        @DisplayName("nome de outro produto: 409 com o detalhe vindo do service")
        void nomeDuplicado() throws Exception {
            when(produtoService.atualizarParcialmente(eq(id), any(ProdutoPatchRequest.class)))
                    .thenThrow(new ConflitoException("Já existe um produto cadastrado como: Sonho"));

            mockMvc.perform(patch("/produto/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"nome\": \"Sonho\" }"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflito"))
                    .andExpect(jsonPath("$.detail").value("Já existe um produto cadastrado como: Sonho"));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /produto")
    class Listar {

        private final List<ProdutoResponse> lista = List.of(
                response(UUID.randomUUID(), "Baguete", "9.90", "4.95", true),
                response(UUID.randomUUID(), "Sonho", "6.50", "3.25", false));

        @Test
        @DisplayName("sem parâmetros: 200, todos os produtos, ordem padrão ASC")
        void semParametros() throws Exception {
            when(produtoService.listar(null, Sort.Direction.ASC)).thenReturn(lista);

            mockMvc.perform(get("/produto"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].nome").value("Baguete"))
                    .andExpect(jsonPath("$[0].origem").value("PRODUCAO_PROPRIA"))
                    .andExpect(jsonPath("$[1].nome").value("Sonho"));
        }

        @Test
        @DisplayName("status=true: repassa o filtro de ativos")
        void soAtivos() throws Exception {
            when(produtoService.listar(true, Sort.Direction.ASC)).thenReturn(List.of(lista.get(0)));

            mockMvc.perform(get("/produto").param("status", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].ativo").value(true));
        }

        @Test
        @DisplayName("status=false: repassa o filtro de inativos")
        void soInativos() throws Exception {
            when(produtoService.listar(false, Sort.Direction.ASC)).thenReturn(List.of(lista.get(1)));

            mockMvc.perform(get("/produto").param("status", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].ativo").value(false));
        }

        @Test
        @DisplayName("ordem=DESC com status: converte para Sort.Direction e repassa os dois")
        void ordemDescComStatus() throws Exception {
            when(produtoService.listar(true, Sort.Direction.DESC)).thenReturn(List.of(lista.get(0)));

            mockMvc.perform(get("/produto").param("status", "true").param("ordem", "DESC"))
                    .andExpect(status().isOk());

            verify(produtoService).listar(true, Sort.Direction.DESC);
        }

        @Test
        @DisplayName("nenhum produto: 200 com lista vazia")
        void vazio() throws Exception {
            when(produtoService.listar(null, Sort.Direction.ASC)).thenReturn(List.of());

            mockMvc.perform(get("/produto"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("status que não é booleano: 400 sem chamar o service")
        void statusInvalido() throws Exception {
            mockMvc.perform(get("/produto").param("status", "abc"))
                    .andExpect(status().isBadRequest());

            verify(produtoService, never()).listar(any(), any());
        }

        @Test
        @DisplayName("ordem inválida: 400 em ProblemDetail, sem chamar o service")
        void ordemInvalida() throws Exception {
            mockMvc.perform(get("/produto").param("ordem", "xyz"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));

            verify(produtoService, never()).listar(any(), any());
        }
    }
}
