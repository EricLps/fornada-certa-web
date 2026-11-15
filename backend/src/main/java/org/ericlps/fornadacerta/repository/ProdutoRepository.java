package org.ericlps.fornadacerta.repository;

import org.ericlps.fornadacerta.entity.Produto;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, UUID> {
    
    List<Produto> findByStatus(Boolean status, Sort sort);

    List<Produto> findAllByNomeIgnoreCase(String nome);

    List<Produto> findAllByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    @Query("SELECT DISTINCT i.produto FROM ItemReceita i WHERE i.insumo.id = :insumoId")
    List<Produto> findUsandoInsumo(@Param("insumoId") UUID insumoId);

}
