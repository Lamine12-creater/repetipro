package bj.repetipro.repository;

import bj.repetipro.model.Eleve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Toutes les méthodes filtrent par répétiteur : chacun ne voit que ses élèves. */
public interface EleveRepository extends JpaRepository<Eleve, Long> {
    List<Eleve> findByRepetiteurIdAndActifTrueOrderByNomAsc(Long repetiteurId);
    List<Eleve> findByRepetiteurIdAndActifFalseOrderByNomAsc(Long repetiteurId);
    Optional<Eleve> findByIdAndRepetiteurId(Long id, Long repetiteurId);
    long countByRepetiteurIdAndActifTrue(Long repetiteurId);
}
