package org.ericlps.fornadacerta.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.InsumoPatchRequest;
import org.ericlps.fornadacerta.dto.InsumoRequest;
import org.ericlps.fornadacerta.dto.InsumoResponse;
import org.ericlps.fornadacerta.service.InsumoService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/insumo")
@RequiredArgsConstructor
public class InsumoController {

    private final InsumoService insumoService;

    @PostMapping
    public ResponseEntity<InsumoResponse> cadastrar(@RequestBody @Valid InsumoRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(insumoService.cadastrar(request));

    }

    @GetMapping
    public ResponseEntity<List<InsumoResponse>> listar(@RequestParam(defaultValue = "ASC") Sort.Direction ordem) {

        return ResponseEntity.ok(insumoService.listar(ordem));

    }

    @PatchMapping("/{id}")
    public ResponseEntity<InsumoResponse> atualizarParcialmente(@PathVariable UUID id,
                                                                @RequestBody @Valid InsumoPatchRequest request) {

        return ResponseEntity.ok(insumoService.atualizarParcialmente(id, request));

    }

}
