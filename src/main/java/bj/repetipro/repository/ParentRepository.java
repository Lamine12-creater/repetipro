package bj.repetipro.repository;

import bj.repetipro.model.CompteParent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParentRepository extends JpaRepository<CompteParent, Long> {
    Optional<CompteParent> findByTelephone(String telephone);
    boolean existsByTelephone(String telephone);
    List<CompteParent> findTop8ByOrderByDateInscriptionDesc();
}
