package org.ericlps.fornadacerta.service;

import lombok.RequiredArgsConstructor;
import org.ericlps.fornadacerta.dto.ProdutoDetalheResponse;
import org.ericlps.fornadacerta.dto.ProdutoPatchRequest;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Produto;
import org.ericlps.fornadacerta.exception.ConflitoException;
import org.ericlps.fornadacerta.exception.RecursoNaoEncontradoException;
import org.ericlps.fornadacerta.exception.RegraDeNegocioException;
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
    private final FichaTecnicaService fichaTecnicaService;

    @Transactional
    public ProdutoResponse cadastrarProduto(ProdutoRequest request) {

        if (produtoRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new ConflitoException("Já existe um produto cadastrado como: " + request.nome());
        }

        Produto produto = produtoMapper.toEntity(request);
        fichaTecnicaService.aplicar(produto, request.origem(), request.rendimento(), request.fatias(), request.itens());

        return produtoMapper.toResponse(produtoRepository.save(produto));
    }

    @Transactional(readOnly = true)
    public ProdutoDetalheResponse detalhar(UUID id) {

        return produtoMapper.toDetalhe(buscar(id));
    }

    @Transactional
    public ProdutoResponse atualizarParcialmente(UUID id, ProdutoPatchRequest request) {

        Produto produto = buscar(id);

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

        if (request.alteraFichaTecnica()) {

            if (!request.fichaTecnicaCompleta()) {
                throw new RegraDeNegocioException("Para alterar a ficha técnica, envie origem, rendimento e itens juntos.");
            }

            fichaTecnicaService.aplicar(produto, request.origem(), request.rendimento(), request.fatias(), request.itens());

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

    private Produto buscar(UUID id) {

        return produtoRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado"));

    }

}
