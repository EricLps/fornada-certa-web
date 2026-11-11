package org.ericlps.fornadacerta.service;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.ProdutoPatchRequest;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.mapper.ProdutoMapper;
import org.ericlps.fornadacerta.repository.ProdutoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoMapper produtoMapper;

    public ProdutoResponse cadastrarProduto(ProdutoRequest request) {

        if (produtoRepository.existsByNomeIgnoreCase(request.nome())) {

            throw new ConflitoException("Já existe um produto cadastrado como: " + request.nome());

        } else {

            Produto produto = produtoMapper.toEntity(request);
            Produto salvo = produtoRepository.save(produto);
            return produtoMapper.toResponse(salvo);
        }

    }

    @Transactional
    public ProdutoResponse atualizarParcialmente(UUID id, ProdutoPatchRequest request) {

        Produto produto = produtoRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado"));

        if (request.nome() != null) {

            if (produtoRepository.existsByNomeIgnoreCaseAndIdNot(request.nome(), id)) {
                throw new ConflitoException("Já existe um produto cadastrado como: " + request.nome());
            }

            produto.setNome(request.nome());

        }
        if (request.preco() != null) {

            produto.setPreco(request.preco());

        }
        if (request.status() != null) {

            produto.setStatus(request.status());

        }

        return produtoMapper.toResponse(produto);


    }


    public List<ProdutoResponse> listar(Boolean status, Sort.Direction ordem) {

        Sort sort = Sort.by(ordem, "nome");

        List<Produto> produtos = (status == null)
                ? produtoRepository.findAll(sort)
                : produtoRepository.findByStatus(status, sort);

        return produtoMapper.toResponseList(produtos);

    }

}
