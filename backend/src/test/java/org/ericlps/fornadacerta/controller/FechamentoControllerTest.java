package org.ericlps.fornadacerta.controller;

import org.ericlps.fornadacerta.dto.FechamentoLoteRequest;
import org.ericlps.fornadacerta.dto.FechamentoResponse;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.service.FechamentoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sobe só a camada web (rotas, JSON, validação e o advice), com o service simulado e sem banco.
@WebMvcTest(FechamentoController.class)
class FechamentoControllerTest {

    private static final LocalDate DATA = LocalDate.of(2026, 11, 14);
    private static final UUID PRODUTO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ITEM_VALIDO = "{ \"produtoId\": \"11111111-1111-1111-1111-111111111111\", \"qtdSobra\": 30 }";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FechamentoService fechamentoService;

    private FechamentoResponse resposta() {
        return new FechamentoResponse(UUID.randomUUID(), PRODUTO_ID, "Pão francês", DATA,
                30, 6, 4, 20, new BigDecimal("0.40"), new BigDecimal("8.00"), null);
    }

    private void postJson(String json, int statusEsperado) throws Exception {
        mockMvc.perform(post("/fechamento").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(statusEsperado));
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /fechamento")
    class RegistrarLote {

        @Test
        @DisplayName("lote válido: 201 com sobra, destinos, descarte e custo do descarte")
        void valido() throws Exception {
            when(fechamentoService.registrarLote(any(FechamentoLoteRequest.class))).thenReturn(List.of(resposta()));

            mockMvc.perform(post("/fechamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "data": "2026-11-14",
                                      "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111",
                                                   "qtdSobra": 30, "qtdDoada": 6, "qtdReaproveitada": 4 } ]
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$[0].produtoNome").value("Pão francês"))
                    .andExpect(jsonPath("$[0].data").value("2026-11-14"))
                    .andExpect(jsonPath("$[0].qtdSobra").value(30))
                    .andExpect(jsonPath("$[0].qtdDescartada").value(20))
                    .andExpect(jsonPath("$[0].custoDescarte").value(8.0));
        }

        @Test
        @DisplayName("repassa os itens ao service; doada e reaproveitada ausentes chegam nulas e o horário é convertido")
        void repassaRequest() throws Exception {
            when(fechamentoService.registrarLote(any(FechamentoLoteRequest.class))).thenReturn(List.of());

            postJson("""
                    { "itens": [ { "produtoId": "11111111-1111-1111-1111-111111111111",
                                   "qtdSobra": 0, "horarioEsgotou": "10:30" } ] }
                    """, 201);

            ArgumentCaptor<FechamentoLoteRequest> captor = ArgumentCaptor.forClass(FechamentoLoteRequest.class);
            verify(fechamentoService).registrarLote(captor.capture());
            FechamentoLoteRequest recebido = captor.getValue();
            assertThat(recebido.data()).isNull();
            assertThat(recebido.itens()).singleElement().satisfies(item -> {
                assertThat(item.produtoId()).isEqualTo(PRODUTO_ID);
                assertThat(item.qtdSobra()).isZero();
                assertThat(item.qtdDoada()).isNull();
                assertThat(item.doadaOuZero()).isZero();
                assertThat(item.horarioEsgotou()).isEqualTo(LocalTime.of(10, 30));
            });
        }

        @Test
        @DisplayName("itens vazio ou ausente: 400 com erros.itens")
        void semItens() throws Exception {
            mockMvc.perform(post("/fechamento").contentType(MediaType.APPLICATION_JSON).content("{ \"itens\": [] }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.itens").isNotEmpty());
            mockMvc.perform(post("/fechamento").contentType(MediaType.APPLICATION_JSON).content("{ }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.itens").isNotEmpty());

            verify(fechamentoService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("sobra ausente ou negativa: 400 apontando o item")
        void sobraInvalida() throws Exception {
            mockMvc.perform(post("/fechamento").contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"itens\": [ { \"produtoId\": \"11111111-1111-1111-1111-111111111111\" } ] }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros['itens[0].qtdSobra']").isNotEmpty());
            mockMvc.perform(post("/fechamento").contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"itens\": [ { \"produtoId\": \"11111111-1111-1111-1111-111111111111\", \"qtdSobra\": -1 } ] }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros['itens[0].qtdSobra']").isNotEmpty());

            verify(fechamentoService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("sobra zero é válida (vendeu tudo)")
        void sobraZeroValida() throws Exception {
            when(fechamentoService.registrarLote(any(FechamentoLoteRequest.class))).thenReturn(List.of());

            postJson("{ \"itens\": [ { \"produtoId\": \"11111111-1111-1111-1111-111111111111\", \"qtdSobra\": 0 } ] }", 201);
        }

        @Test
        @DisplayName("doada ou reaproveitada negativas, sem produtoId ou produtoId inválido: 400")
        void outrosCamposInvalidos() throws Exception {
            postJson("{ \"itens\": [ { \"produtoId\": \"11111111-1111-1111-1111-111111111111\", \"qtdSobra\": 5, \"qtdDoada\": -1 } ] }", 400);
            postJson("{ \"itens\": [ { \"produtoId\": \"11111111-1111-1111-1111-111111111111\", \"qtdSobra\": 5, \"qtdReaproveitada\": -1 } ] }", 400);
            postJson("{ \"itens\": [ { \"qtdSobra\": 5 } ] }", 400);
            postJson("{ \"itens\": [ { \"produtoId\": \"abc\", \"qtdSobra\": 5 } ] }", 400);

            verify(fechamentoService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("data ou horário em formato errado: 400")
        void formatosInvalidos() throws Exception {
            postJson("{ \"data\": \"14/11/2026\", \"itens\": [ " + ITEM_VALIDO + " ] }", 400);
            postJson("{ \"itens\": [ { \"produtoId\": \"11111111-1111-1111-1111-111111111111\", \"qtdSobra\": 0, \"horarioEsgotou\": \"10h30\" } ] }", 400);

            verify(fechamentoService, never()).registrarLote(any());
        }

        @Test
        @DisplayName("regra de negócio violada (ex.: sobra maior que o produzido): 422")
        void regraDeNegocio() throws Exception {
            when(fechamentoService.registrarLote(any(FechamentoLoteRequest.class)))
                    .thenThrow(new RegraDeNegocioException("A sobra de Pão francês (120) não pode passar do total produzido no dia (100)."));

            mockMvc.perform(post("/fechamento").contentType(MediaType.APPLICATION_JSON).content("{ \"itens\": [ " + ITEM_VALIDO + " ] }"))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.title").value("Regra de negócio violada"))
                    .andExpect(jsonPath("$.detail").value("A sobra de Pão francês (120) não pode passar do total produzido no dia (100)."));
        }

        @Test
        @DisplayName("dia já fechado: 409")
        void jaFechado() throws Exception {
            when(fechamentoService.registrarLote(any(FechamentoLoteRequest.class)))
                    .thenThrow(new ConflitoException("O dia 2026-11-14 já foi fechado para Pão francês."));

            postJson("{ \"itens\": [ " + ITEM_VALIDO + " ] }", 409);
        }

        @Test
        @DisplayName("produto inexistente: 404")
        void produtoInexistente() throws Exception {
            when(fechamentoService.registrarLote(any(FechamentoLoteRequest.class)))
                    .thenThrow(new RecursoNaoEncontradoException("Produto não encontrado"));

            postJson("{ \"itens\": [ " + ITEM_VALIDO + " ] }", 404);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /fechamento")
    class Listar {

        @Test
        @DisplayName("sem data: repassa nulo (o service assume hoje)")
        void semData() throws Exception {
            when(fechamentoService.listar(null)).thenReturn(List.of());

            mockMvc.perform(get("/fechamento"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(fechamentoService).listar(null);
        }

        @Test
        @DisplayName("com data: converte e repassa")
        void comData() throws Exception {
            when(fechamentoService.listar(DATA)).thenReturn(List.of(resposta()));

            mockMvc.perform(get("/fechamento").param("data", "2026-11-14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].produtoNome").value("Pão francês"));
        }

        @Test
        @DisplayName("data inválida: 400 sem chamar o service")
        void dataInvalida() throws Exception {
            mockMvc.perform(get("/fechamento").param("data", "14/11/2026"))
                    .andExpect(status().isBadRequest());

            verify(fechamentoService, never()).listar(any());
        }
    }
}
