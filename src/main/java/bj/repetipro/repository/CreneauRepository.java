package bj.repetipro.repository;

import bj.repetipro.model.CreneauEleve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreneauRepository extends JpaRepository<CreneauEleve, Long> {
    List<CreneauEleve> findByEleveIdOrderByJourAscHeureDebutAsc(Long eleveId);
    List<CreneauEleve> findByEleveActifTrue();
    void deleteByEleveId(Long eleveId);
    long countByEleveId(Long eleveId);
}
