package org.ericlps.fornadacerta.repository;

import org.ericlps.fornadacerta.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, UUID> {


}
