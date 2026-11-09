package org.ericlps.fornadacerta.service;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.mapper.ProdutoMapper;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoMapper produtoMapper;

    public ProdutoResponse cadastrarProduto(ProdutoRequest request){

        Produto produto = produtoMapper.toEntity(request);
        Produto salvo = produtoRepository.save(produto);
        return produtoMapper.toResponse(salvo);

    }

    @Transactional
    public ProdutoResponse atualizarProduto(UUID id, ProdutoRequest request){

        Produto produto = produtoRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));

        produtoMapper.atualizar(produto, request);

        return produtoMapper.toResponse(produto);

    }

}
