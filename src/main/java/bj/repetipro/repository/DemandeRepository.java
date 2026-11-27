package bj.repetipro.repository;

import bj.repetipro.model.DemandeRepetiteur;
import bj.repetipro.model.StatutDemande;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DemandeRepository extends JpaRepository<DemandeRepetiteur, Long> {
    List<DemandeRepetiteur> findByEnfantIdOrderByIdDesc(Long enfantId);
    List<DemandeRepetiteur> findByRepetiteurIdAndStatutOrderByDateCreationAsc(Long repetiteurId, StatutDemande statut);
    List<DemandeRepetiteur> findTop20ByRepetiteurIdAndStatutNotOrderByDateDecisionDesc(Long repetiteurId, StatutDemande statut);
    long countByRepetiteurIdAndStatut(Long repetiteurId, StatutDemande statut);
    Optional<DemandeRepetiteur> findByIdAndRepetiteurId(Long id, Long repetiteurId);
    List<DemandeRepetiteur> findTop8ByOrderByDateCreationDesc();
    List<DemandeRepetiteur> findTop8ByDateDecisionNotNullOrderByDateDecisionDesc();
}
