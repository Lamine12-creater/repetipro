package bj.repetipro.repository;

import bj.repetipro.model.Facture;
import bj.repetipro.model.StatutFacture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FactureRepository extends JpaRepository<Facture, Long> {
    List<Facture> findByRepetiteurIdAndAnneeAndMoisOrderByIdAsc(Long repetiteurId, int annee, int mois);
    List<Facture> findByRepetiteurIdAndStatutOrderByDateEcheanceAsc(Long repetiteurId, StatutFacture statut);
    Optional<Facture> findByIdAndRepetiteurId(Long id, Long repetiteurId);
    boolean existsByEleveIdAndAnneeAndMois(Long eleveId, int annee, int mois);
}
