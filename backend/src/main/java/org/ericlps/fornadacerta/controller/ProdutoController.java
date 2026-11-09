package org.ericlps.fornadacerta.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.service.ProdutoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/produto")
@AllArgsConstructor
public class ProdutoController {

    private ProdutoService produtoService;

    @PostMapping
    public ResponseEntity<ProdutoResponse> CadastrarProduto(@RequestBody @Valid ProdutoRequest request){

        ProdutoResponse response = produtoService.cadastrarProduto(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

}
