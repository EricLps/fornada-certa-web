package org.ericlps.fornadacerta.controller;

import org.ericlps.fornadacerta.dto.InsumoPatchRequest;
import org.ericlps.fornadacerta.dto.InsumoRequest;
import org.ericlps.fornadacerta.dto.InsumoResponse;
import org.ericlps.fornadacerta.entity.enums.UnidadeMedida;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
import org.ericlps.fornadacerta.service.InsumoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsumoController.class)
class InsumoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsumoService insumoService;

    private static InsumoResponse leite(UUID id) {
        return new InsumoResponse(id, "Leite", UnidadeMedida.L, new BigDecimal("5.89"));
    }

    // =====================================================================
    @Nested
    @DisplayName("POST /insumo")
    class Cadastrar {

        private void postJson(String json, int statusEsperado) throws Exception {
            mockMvc.perform(post("/insumo").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().is(statusEsperado));
        }

        @Test
        @DisplayName("dados válidos: 201 com o insumo criado")
        void valido() throws Exception {
            UUID id = UUID.randomUUID();
            when(insumoService.cadastrar(any(InsumoRequest.class))).thenReturn(leite(id));

            mockMvc.perform(post("/insumo").contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"nome\": \"Leite\", \"unidade\": \"L\", \"custoUnitario\": 5.89 }"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.nome").value("Leite"))
                    .andExpect(jsonPath("$.unidade").value("L"))
                    .andExpect(jsonPath("$.custoUnitario").value(5.89));

            ArgumentCaptor<InsumoRequest> captor = ArgumentCaptor.forClass(InsumoRequest.class);
            verify(insumoService).cadastrar(captor.capture());
            assertThat(captor.getValue().unidade()).isEqualTo(UnidadeMedida.L);
            assertThat(captor.getValue().custoUnitario()).isEqualByComparingTo("5.89");
        }

        @Test
        @DisplayName("nome vazio ou ausente: 400 com erros.nome")
        void nomeInvalido() throws Exception {
            mockMvc.perform(post("/insumo").contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"nome\": \" \", \"unidade\": \"L\", \"custoUnitario\": 5.89 }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.nome").isNotEmpty());
            postJson("{ \"unidade\": \"L\", \"custoUnitario\": 5.89 }", 400);
            postJson("{ \"nome\": \"" + "a".repeat(101) + "\", \"unidade\": \"L\", \"custoUnitario\": 5.89 }", 400);

            verify(insumoService, never()).cadastrar(any());
        }

        @Test
        @DisplayName("unidade ausente ou desconhecida: 400")
        void unidadeInvalida() throws Exception {
            mockMvc.perform(post("/insumo").contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"nome\": \"Leite\", \"custoUnitario\": 5.89 }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.unidade").isNotEmpty());
            postJson("{ \"nome\": \"Leite\", \"unidade\": \"XICARA\", \"custoUnitario\": 5.89 }", 400);

            verify(insumoService, never()).cadastrar(any());
        }

        @Test
        @DisplayName("custo ausente, zero ou negativo: 400 com erros.custoUnitario")
        void custoInvalido() throws Exception {
            for (String custo : new String[]{"", ", \"custoUnitario\": 0", ", \"custoUnitario\": -1"}) {
                mockMvc.perform(post("/insumo").contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"nome\": \"Leite\", \"unidade\": \"L\"" + custo + " }"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.erros.custoUnitario").isNotEmpty());
            }

            verify(insumoService, never()).cadastrar(any());
        }

        @Test
        @DisplayName("nome duplicado: 409 com o detalhe do service")
        void duplicado() throws Exception {
            when(insumoService.cadastrar(any(InsumoRequest.class)))
                    .thenThrow(new ConflitoException("Já existe um insumo cadastrado como: Leite"));

            mockMvc.perform(post("/insumo").contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"nome\": \"Leite\", \"unidade\": \"L\", \"custoUnitario\": 5.89 }"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Já existe um insumo cadastrado como: Leite"));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("GET /insumo")
    class Listar {

        @Test
        @DisplayName("sem parâmetro: ordem ASC")
        void padrao() throws Exception {
            when(insumoService.listar(Sort.Direction.ASC)).thenReturn(List.of(leite(UUID.randomUUID())));

            mockMvc.perform(get("/insumo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].nome").value("Leite"));
        }

        @Test
        @DisplayName("ordem=DESC: repassa a direção")
        void desc() throws Exception {
            when(insumoService.listar(Sort.Direction.DESC)).thenReturn(List.of());

            mockMvc.perform(get("/insumo").param("ordem", "DESC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(insumoService).listar(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("ordem inválida: 400 sem chamar o service")
        void ordemInvalida() throws Exception {
            mockMvc.perform(get("/insumo").param("ordem", "xyz"))
                    .andExpect(status().isBadRequest());

            verify(insumoService, never()).listar(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("PATCH /insumo/{id}")
    class Atualizar {

        private final UUID id = UUID.randomUUID();

        private void patchJson(String json, int statusEsperado) throws Exception {
            mockMvc.perform(patch("/insumo/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().is(statusEsperado));
        }

        @Test
        @DisplayName("só custo: 200 e os outros campos chegam nulos")
        void soCusto() throws Exception {
            when(insumoService.atualizarParcialmente(eq(id), any(InsumoPatchRequest.class))).thenReturn(leite(id));

            patchJson("{ \"custoUnitario\": 6.20 }", 200);

            ArgumentCaptor<InsumoPatchRequest> captor = ArgumentCaptor.forClass(InsumoPatchRequest.class);
            verify(insumoService).atualizarParcialmente(eq(id), captor.capture());
            assertThat(captor.getValue().custoUnitario()).isEqualByComparingTo("6.20");
            assertThat(captor.getValue().nome()).isNull();
            assertThat(captor.getValue().unidade()).isNull();
        }

        @Test
        @DisplayName("corpo vazio: 200")
        void vazio() throws Exception {
            when(insumoService.atualizarParcialmente(eq(id), any(InsumoPatchRequest.class))).thenReturn(leite(id));

            patchJson("{}", 200);
        }

        @Test
        @DisplayName("nome vazio, custo zero ou unidade desconhecida: 400 sem chamar o service")
        void invalidos() throws Exception {
            patchJson("{ \"nome\": \"\" }", 400);
            patchJson("{ \"custoUnitario\": 0 }", 400);
            patchJson("{ \"unidade\": \"XICARA\" }", 400);

            verify(insumoService, never()).atualizarParcialmente(any(), any());
        }

        @Test
        @DisplayName("unidade bloqueada pelo service: 422")
        void unidadeBloqueada() throws Exception {
            when(insumoService.atualizarParcialmente(eq(id), any(InsumoPatchRequest.class)))
                    .thenThrow(new RegraDeNegocioException("A unidade de Leite não pode mudar porque ele já é usado em fichas técnicas."));

            mockMvc.perform(patch("/insumo/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"unidade\": \"KG\" }"))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.title").value("Regra de negócio violada"));
        }

        @Test
        @DisplayName("inexistente: 404")
        void inexistente() throws Exception {
            when(insumoService.atualizarParcialmente(eq(id), any(InsumoPatchRequest.class)))
                    .thenThrow(new RecursoNaoEncontradoException("Insumo não encontrado"));

            mockMvc.perform(patch("/insumo/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"nome\": \"Leite integral\" }"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Insumo não encontrado"));
        }

        @Test
        @DisplayName("id que não é UUID: 400")
        void idInvalido() throws Exception {
            mockMvc.perform(patch("/insumo/{id}", "abc").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());

            verify(insumoService, never()).atualizarParcialmente(any(), any());
        }
    }
}
