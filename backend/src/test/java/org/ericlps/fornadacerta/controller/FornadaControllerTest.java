package org.ericlps.fornadacerta.controller;

import org.ericlps.fornadacerta.dto.FornadaLoteRequest;
import org.ericlps.fornadacerta.dto.FornadaPatchRequest;
import org.ericlps.fornadacerta.dto.FornadaResponse;
import org.ericlps.fornadacerta.entity.enums.Turno;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.service.FornadaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sobe só a camada web (rotas, JSON, validação e o advice), com o service simulado e sem banco.
@WebMvcTest(FornadaController.class)
class FornadaControllerTest {

    private static final LocalDate DATA = LocalDate.of(2026, 11, 15);
    private static final UUID PRODUTO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FornadaService fornadaService;

    private FornadaResponse resposta(UUID id, String nome, Turno turno, int qtd) {
        return new FornadaResponse(id, PRODUTO_ID, nome, DATA, turno, qtd, null);
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /fornada")
    class RegistrarLote {

        private void postJson(String json, int statusEsperado) throws Exception {
            mockMvc.perform(post("/fornada").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().is(statusEsperado));
        }

        @Test
        @DisplayName("lote válido: 201 com a lista de fornadas criadas")
        void valido() throws Exception {
            UUID id = UUID.randomUUID();
            when(fornadaService.registrarLote(any(FornadaLoteRequest.class)))
                    .thenReturn(List.of(resposta(id, "Pão francês", Turno.MANHA, 250)));

            mockMvc.perform(post("/fornada")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "data": "2026-11-15",
                                      "turno": "MANHA",
                                      "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 250, "pacotesUsados": 3 } ]
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(id.toString()))
                    .andExpect(jsonPath("$[0].produtoId").value(PRODUTO_ID.toString()))
                    .andExpect(jsonPath("$[0].produtoNome").value("Pão francês"))
                    .andExpect(jsonPath("$[0].data").value("2026-11-15"))
                    .andExpect(jsonPath("$[0].turno").value("MANHA"))
                    .andExpect(jsonPath("$[0].qtdProduzida").value(250));
        }

        @Test
        @DisplayName("repassa ao service data, turno e itens exatamente como vieram")
        void repassaRequest() throws Exception {
            when(fornadaService.registrarLote(any(FornadaLoteRequest.class))).thenReturn(List.of());

            postJson("""
                    {
                      "data": "2026-11-15",
                      "turno": "TARDE",
                      "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 40 } ]
                    }
                    """, 201);

            ArgumentCaptor<FornadaLoteRequest> captor = ArgumentCaptor.forClass(FornadaLoteRequest.class);
            verify(fornadaService).registrarLote(captor.capture());
            FornadaLoteRequest recebido = captor.getValue();
            assertThat(recebido.data()).isEqualTo(DATA);
            assertThat(recebido.turno()).isEqualTo(Turno.TARDE);
            assertThat(recebido.itens()).singleElement().satisfies(item -> {
                assertThat(item.produtoId()).isEqualTo(PRODUTO_ID);
                assertThat(item.qtdProduzida()).isEqualTo(40);
                assertThat(item.pacotesUsados()).isNull();
            });
        }

        @Test
        @DisplayName("sem data: aceito, e a data chega nula ao service (que assume hoje)")
        void semData() throws Exception {
            when(fornadaService.registrarLote(any(FornadaLoteRequest.class))).thenReturn(List.of());

            postJson("""
                    { "turno": "MANHA", "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 10 } ] }
                    """, 201);

            ArgumentCaptor<FornadaLoteRequest> captor = ArgumentCaptor.forClass(FornadaLoteRequest.class);
            verify(fornadaService).registrarLote(captor.capture());
            assertThat(captor.getValue().data()).isNull();
        }

        @Test
        @DisplayName("sem turno: 400 com erros.turno")
        void semTurno() throws Exception {
            mockMvc.perform(post("/fornada")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 10 } ] }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.turno").isNotEmpty());

            verify(fornadaService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("turno que não existe (NOITE): 400 sem chamar o service")
        void turnoInvalido() throws Exception {
            postJson("""
                    { "turno": "NOITE", "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 10 } ] }
                    """, 400);

            verify(fornadaService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("itens vazio ou ausente: 400 com erros.itens")
        void semItens() throws Exception {
            mockMvc.perform(post("/fornada").contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"turno\": \"MANHA\", \"itens\": [] }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.itens").isNotEmpty());

            mockMvc.perform(post("/fornada").contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"turno\": \"MANHA\" }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.itens").isNotEmpty());

            verify(fornadaService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("item inválido: 400 apontando o item exato (itens[1].qtdProduzida)")
        void itemInvalidoApontaALinha() throws Exception {
            mockMvc.perform(post("/fornada")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "turno": "MANHA",
                                      "itens": [
                                        { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 10 },
                                        { "produtoId": "22222222-2222-2222-2222-222222222222", "qtdProduzida": 0 }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros['itens[1].qtdProduzida']").isNotEmpty())
                    .andExpect(jsonPath("$.erros['itens[0].qtdProduzida']").doesNotExist());

            verify(fornadaService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("item sem produtoId, sem quantidade, quantidade negativa ou pacotes zero: 400")
        void itensInvalidos() throws Exception {
            postJson("{ \"turno\": \"MANHA\", \"itens\": [ { \"qtdProduzida\": 10 } ] }", 400);
            postJson("{ \"turno\": \"MANHA\", \"itens\": [ { \"produtoId\": \"11111111-1111-1111-1111-111111111111\" } ] }", 400);
            postJson("{ \"turno\": \"MANHA\", \"itens\": [ { \"produtoId\": \"11111111-1111-1111-1111-111111111111\", \"qtdProduzida\": -5 } ] }", 400);
            postJson("{ \"turno\": \"MANHA\", \"itens\": [ { \"produtoId\": \"11111111-1111-1111-1111-111111111111\", \"qtdProduzida\": 10, \"pacotesUsados\": 0 } ] }", 400);
            postJson("{ \"turno\": \"MANHA\", \"itens\": [ { \"produtoId\": \"nao-e-uuid\", \"qtdProduzida\": 10 } ] }", 400);

            verify(fornadaService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("data em formato errado (15/11/2026): 400")
        void dataMalformada() throws Exception {
            postJson("""
                    { "data": "15/11/2026", "turno": "MANHA", "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 10 } ] }
                    """, 400);

            verify(fornadaService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("regra de negócio violada (ex.: produto desativado): 422 com ProblemDetail")
        void regraDeNegocio() throws Exception {
            when(fornadaService.registrarLote(any(FornadaLoteRequest.class)))
                    .thenThrow(new RegraDeNegocioException("O produto Croissant está desativado"));

            mockMvc.perform(post("/fornada")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "turno": "MANHA", "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 10 } ] }
                                    """))
                    .andExpect(status().is(422))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.title").value("Regra de negócio violada"))
                    .andExpect(jsonPath("$.detail").value("O produto Croissant está desativado"));
        }

        @Test
        @DisplayName("fornada já registrada: 409")
        void jaRegistrada() throws Exception {
            when(fornadaService.registrarLote(any(FornadaLoteRequest.class)))
                    .thenThrow(new ConflitoException("A fornada de Pão francês já foi registrada."));

            mockMvc.perform(post("/fornada")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "turno": "MANHA", "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 10 } ] }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflito"));
        }

        @Test
        @DisplayName("produto inexistente: 404")
        void produtoInexistente() throws Exception {
            when(fornadaService.registrarLote(any(FornadaLoteRequest.class)))
                    .thenThrow(new RecursoNaoEncontradoException("Produto não encontrado"));

            postJson("""
                    { "turno": "MANHA", "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111", "qtdProduzida": 10 } ] }
                    """, 404);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /fornada")
    class Listar {

        @Test
        @DisplayName("sem parâmetros: repassa data e turno nulos (o service assume hoje)")
        void semParametros() throws Exception {
            when(fornadaService.listar(null, null)).thenReturn(List.of());

            mockMvc.perform(get("/fornada"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(fornadaService).listar(null, null);
        }

        @Test
        @DisplayName("data e turno: converte e repassa os dois")
        void comDataETurno() throws Exception {
            when(fornadaService.listar(DATA, Turno.MANHA))
                    .thenReturn(List.of(resposta(UUID.randomUUID(), "Baguete", Turno.MANHA, 60)));

            mockMvc.perform(get("/fornada").param("data", "2026-11-15").param("turno", "MANHA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].produtoNome").value("Baguete"))
                    .andExpect(jsonPath("$[0].turno").value("MANHA"));

            verify(fornadaService).listar(DATA, Turno.MANHA);
        }

        @Test
        @DisplayName("data ou turno inválidos: 400 sem chamar o service")
        void parametrosInvalidos() throws Exception {
            mockMvc.perform(get("/fornada").param("data", "15/11/2026"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/fornada").param("turno", "NOITE"))
                    .andExpect(status().isBadRequest());

            verify(fornadaService, never()).listar(any(), any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("PATCH /fornada/{id}")
    class AtualizarParcialmente {

        private final UUID id = UUID.randomUUID();

        @Test
        @DisplayName("só quantidade: 200, e pacotes chega nulo ao service")
        void soQuantidade() throws Exception {
            when(fornadaService.atualizarParcialmente(eq(id), any(FornadaPatchRequest.class)))
                    .thenReturn(resposta(id, "Pão francês", Turno.MANHA, 230));

            mockMvc.perform(patch("/fornada/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"qtdProduzida\": 230 }"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.qtdProduzida").value(230));

            ArgumentCaptor<FornadaPatchRequest> captor = ArgumentCaptor.forClass(FornadaPatchRequest.class);
            verify(fornadaService).atualizarParcialmente(eq(id), captor.capture());
            assertThat(captor.getValue().qtdProduzida()).isEqualTo(230);
            assertThat(captor.getValue().pacotesUsados()).isNull();
        }

        @Test
        @DisplayName("quantidade ou pacotes zero/negativos: 400 sem chamar o service")
        void valoresInvalidos() throws Exception {
            mockMvc.perform(patch("/fornada/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{ \"qtdProduzida\": 0 }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.qtdProduzida").isNotEmpty());
            mockMvc.perform(patch("/fornada/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{ \"pacotesUsados\": -1 }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.pacotesUsados").isNotEmpty());

            verify(fornadaService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("id que não é UUID: 400")
        void idInvalido() throws Exception {
            mockMvc.perform(patch("/fornada/abc").contentType(MediaType.APPLICATION_JSON).content("{ \"qtdProduzida\": 10 }"))
                    .andExpect(status().isBadRequest());

            verify(fornadaService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("fornada inexistente: 404")
        void inexistente() throws Exception {
            when(fornadaService.atualizarParcialmente(eq(id), any(FornadaPatchRequest.class)))
                    .thenThrow(new RecursoNaoEncontradoException("Fornada não encontrada"));

            mockMvc.perform(patch("/fornada/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{ \"qtdProduzida\": 10 }"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Fornada não encontrada"));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("DELETE /fornada/{id}")
    class Excluir {

        @Test
        @DisplayName("fornada existente: 204 sem corpo")
        void existente() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(delete("/fornada/{id}", id))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(fornadaService).excluir(id);
        }

        @Test
        @DisplayName("fornada inexistente: 404")
        void inexistente() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(new RecursoNaoEncontradoException("Fornada não encontrada")).when(fornadaService).excluir(id);

            mockMvc.perform(delete("/fornada/{id}", id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("id que não é UUID: 400 sem chamar o service")
        void idInvalido() throws Exception {
            mockMvc.perform(delete("/fornada/abc"))
                    .andExpect(status().isBadRequest());

            verify(fornadaService, never()).excluir(any());
        }
    }
}
