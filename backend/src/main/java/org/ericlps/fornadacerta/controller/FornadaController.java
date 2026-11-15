package org.ericlps.fornadacerta.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.FornadaLoteRequest;
import org.ericlps.fornadacerta.dto.FornadaPatchRequest;
import org.ericlps.fornadacerta.dto.FornadaResponse;
import org.ericlps.fornadacerta.entity.enums.Turno;
import org.ericlps.fornadacerta.service.FornadaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fornada")
@RequiredArgsConstructor
public class FornadaController {

    private final FornadaService fornadaService;

    @PostMapping
    public ResponseEntity<List<FornadaResponse>> registrarLote(@RequestBody @Valid FornadaLoteRequest request){

        return ResponseEntity.status(HttpStatus.CREATED).body(fornadaService.registrarLote(request));

    }

    @GetMapping
    public ResponseEntity<List<FornadaResponse>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) Turno turno){

        return ResponseEntity.ok(fornadaService.listar(data,turno));

    }

    @PatchMapping("/{id}")
    public ResponseEntity<FornadaResponse> atualizarParcialmente(@PathVariable UUID id,
                                                                 @RequestBody @Valid FornadaPatchRequest request){

        return ResponseEntity.ok(fornadaService.atualizarParcialmente(id, request));

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id){

        fornadaService.excluir(id);
        return ResponseEntity.noContent().build();

    }

}
