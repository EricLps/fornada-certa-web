package org.ericlps.fornadacerta.repository;

import org.ericlps.fornadacerta.entity.ItemReceita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ItemReceitaRepository extends JpaRepository<ItemReceita, UUID> {

    boolean existsByInsumoId(UUID insumoId);

}
