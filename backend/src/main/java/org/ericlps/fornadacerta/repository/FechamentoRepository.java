package org.ericlps.fornadacerta.repository;

import org.ericlps.fornadacerta.entity.Fechamento;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FechamentoRepository extends JpaRepository<Fechamento, UUID> {

    boolean existsByDataAndProdutoId(LocalDate data, UUID produtoId);

    Optional<Fechamento> findByDataAndProdutoId(LocalDate data, UUID produtoId);

    @EntityGraph(attributePaths = "produto")
    List<Fechamento> findByData(LocalDate data, Sort sort);

}
