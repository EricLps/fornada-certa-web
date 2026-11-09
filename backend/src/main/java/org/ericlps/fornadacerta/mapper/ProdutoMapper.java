package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.Produto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProdutoMapper {

    public Produto toEntity(ProdutoRequest request){
        Produto produto = new Produto();
        produto.setNome(request.nome());
        produto.setPreco(request.preco());
        return produto;
    }

    public ProdutoResponse toResponse (Produto produto){
        return new ProdutoResponse(produto.getId(),
                produto.getNome(),
                produto.getPreco(),
                produto.getStatus()
        );
    }

    public void atualizar(Produto produto, ProdutoRequest request){
        produto.setNome(request.nome());
        produto.setPreco(request.preco());
    }

    public List<ProdutoResponse> toResponseList(List<Produto> produtos){

        return produtos.stream().map(this::toResponse).toList();

    }

}
