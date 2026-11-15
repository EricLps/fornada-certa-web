package org.ericlps.fornadacerta.controller;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.SugestaoResponse;
import org.ericlps.fornadacerta.service.SugestaoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/sugestao")
@RequiredArgsConstructor
public class SugestaoController {

    private final SugestaoService sugestaoService;

    @GetMapping
    public ResponseEntity<List<SugestaoResponse>> sugerir(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {

        return ResponseEntity.ok(sugestaoService.sugerir(data));

    }

}
