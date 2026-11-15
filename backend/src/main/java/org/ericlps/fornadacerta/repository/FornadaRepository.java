package org.ericlps.fornadacerta.repository;

import org.ericlps.fornadacerta.entity.Fornada;
import org.ericlps.fornadacerta.entity.enums.Turno;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
