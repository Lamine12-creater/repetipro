package bj.repetipro.repository;

import bj.repetipro.model.CreneauDemande;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreneauDemandeRepository extends JpaRepository<CreneauDemande, Long> {
    List<CreneauDemande> findByDemandeIdOrderByJourAscHeureDebutAsc(Long demandeId);
}
