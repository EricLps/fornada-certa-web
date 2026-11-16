package org.ericlps.fornadacerta.controller;

import org.ericlps.fornadacerta.calculo.CriterioSugestao;
import org.ericlps.fornadacerta.dto.SugestaoResponse;
import org.ericlps.fornadacerta.service.SugestaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SugestaoController.class)
class SugestaoControllerTest {

    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 16);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SugestaoService sugestaoService;

    @Test
    @DisplayName("com data: 200 com a sugestão de cada produto")
    void comData() throws Exception {
        when(sugestaoService.sugerir(SEGUNDA)).thenReturn(List.of(
                new SugestaoResponse(UUID.randomUUID(), "Pão francês", SEGUNDA, "segunda-feira",
                        CriterioSugestao.MESMO_DIA_DA_SEMANA, 4, new BigDecimal("100.0"), 110, null, null),
                new SugestaoResponse(UUID.randomUUID(), "Croissant", SEGUNDA, "segunda-feira",
                        CriterioSugestao.SEM_HISTORICO, 0, null, null, "Ainda não há dias fechados suficientes.", null)));

        mockMvc.perform(get("/sugestao").param("data", "2026-11-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].produtoNome").value("Pão francês"))
                .andExpect(jsonPath("$[0].data").value("2026-11-16"))
                .andExpect(jsonPath("$[0].diaDaSemana").value("segunda-feira"))
                .andExpect(jsonPath("$[0].criterio").value("MESMO_DIA_DA_SEMANA"))
                .andExpect(jsonPath("$[0].qtdSugerida").value(110))
                .andExpect(jsonPath("$[1].criterio").value("SEM_HISTORICO"))
                .andExpect(jsonPath("$[1].aviso").isNotEmpty());
    }

    @Test
    @DisplayName("sem data: repassa nulo (o service assume amanhã)")
    void semData() throws Exception {
        when(sugestaoService.sugerir(null)).thenReturn(List.of());

        mockMvc.perform(get("/sugestao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(sugestaoService).sugerir(null);
    }

    @Test
    @DisplayName("data em formato errado: 400 sem chamar o service")
    void dataInvalida() throws Exception {
        mockMvc.perform(get("/sugestao").param("data", "16/11/2026"))
                .andExpect(status().isBadRequest());

        verify(sugestaoService, never()).sugerir(any());
    }
}
