package org.ericlps.fornadacerta.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.FechamentoLoteRequest;
import org.ericlps.fornadacerta.dto.FechamentoResponse;
import org.ericlps.fornadacerta.service.FechamentoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/fechamento")
@RequiredArgsConstructor
public class FechamentoController {

    private final FechamentoService fechamentoService;

    @PostMapping
    public ResponseEntity<List<FechamentoResponse>> registrarLote(@RequestBody @Valid FechamentoLoteRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(fechamentoService.registrarLote(request));

    }

    @GetMapping
    public ResponseEntity<List<FechamentoResponse>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {

        return ResponseEntity.ok(fechamentoService.listar(data));

    }

}
