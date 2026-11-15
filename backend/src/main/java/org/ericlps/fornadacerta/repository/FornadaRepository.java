package org.ericlps.fornadacerta.repository;

import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.enums.Turno;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface FornadaRepository extends JpaRepository<Fornada, UUID> {

    boolean existsByDataAndProdutoIdAndTurno(LocalDate data, UUID produtoId, Turno turno);

    @EntityGraph(attributePaths = "produto")
    List<Fornada> findByData(LocalDate data, Sort sort);

    @EntityGraph(attributePaths = "produto")
    List<Fornada> findByDataAndTurno(LocalDate data, Turno turno, Sort sort);

    @Query("""
            SELECT COALESCE(SUM(f.qtdProduzida), 0)
            FROM Fornada f
            WHERE f.produto.id = :produtoId AND f.data = :data
            """)
    long somarProducaoDoDia(@Param("produtoId") UUID produtoId, @Param("data") LocalDate data);

    List<Fornada> findByDataBetween(LocalDate inicio, LocalDate fim);
}
