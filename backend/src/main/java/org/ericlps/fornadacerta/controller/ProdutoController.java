package org.ericlps.fornadacerta.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.service.ProdutoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/produto")
@AllArgsConstructor
public class ProdutoController {

    private ProdutoService produtoService;

    @PostMapping
    public ResponseEntity<ProdutoResponse> cadastrarProduto(@RequestBody @Valid ProdutoRequest request){

        ProdutoResponse response = produtoService.cadastrarProduto(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponse> atualizarProduto(@PathVariable UUID id,
                                                            @RequestBody @Valid ProdutoRequest request){

        return ResponseEntity.ok(produtoService.atualizarProduto(id,request));

    }

}
