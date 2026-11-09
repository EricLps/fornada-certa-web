package org.ericlps.fornadacerta.service;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.mapper.ProdutoMapper;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.springframework.stereotype.Service;

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

}
