package org.ericlps.fornadacerta.mapper;

import org.ericlps.fornadacerta.dto.ItemReceitaResponse;
import org.ericlps.fornadacerta.dto.ProdutoDetalheResponse;
import org.ericlps.fornadacerta.dto.ProdutoRequest;
import org.ericlps.fornadacerta.dto.ProdutoResponse;
import org.ericlps.fornadacerta.entity.ItemReceita;
import org.ericlps.fornadacerta.entity.Produto;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Comparator;
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
                produto.getCusto(),
                produto.getStatus(),
                produto.getOrigem(),
                produto.getRendimento(),
                produto.getFatias()
        );
    }

    public List<ProdutoResponse> toResponseList(List<Produto> produtos){

        return produtos.stream().map(this::toResponse).toList();

    }

    public ProdutoDetalheResponse toDetalhe(Produto produto) {

        List<ItemReceitaResponse> itens = produto.getReceita().stream()
                .sorted(Comparator.comparing(item -> item.getInsumo().getNome()))
                .map(this::toItemResponse)
                .toList();

        return new ProdutoDetalheResponse(produto.getId(),
                produto.getNome(),
                produto.getPreco(),
                produto.getCusto(),
                produto.getStatus(),
                produto.getOrigem(),
                produto.getRendimento(),
                produto.getFatias(),
                itens
        );
    }

    private ItemReceitaResponse toItemResponse(ItemReceita item) {
        return new ItemReceitaResponse(
                item.getInsumo().getId(),
                item.getInsumo().getNome(),
                item.getInsumo().getUnidade(),
                item.getQuantidade(),
                item.getInsumo().custoPorUnidadeDeUso().setScale(2, RoundingMode.HALF_UP),
                item.getQuantidade().multiply(item.getInsumo().custoPorUnidadeDeUso()).setScale(2, RoundingMode.HALF_UP)
        );
    }

}
