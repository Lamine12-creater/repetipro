package bj.repetipro.repository;

import bj.repetipro.model.Presence;
import bj.repetipro.model.Seance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeanceRepository extends JpaRepository<Seance, Long> {

    /** Séances d'un répétiteur entre deux dates (ex. aujourd'hui, ou la semaine). */
    List<Seance> findByRepetiteurIdAndDebutBetweenOrderByDebutAsc(Long repetiteurId, LocalDateTime de, LocalDateTime a);

    Optional<Seance> findByIdAndRepetiteurId(Long id, Long repetiteurId);

    /** Pour la facture : nombre de séances d'un élève dans le mois avec une présence donnée. */
    long countByEleveIdAndDebutBetweenAndPresenceIn(Long eleveId, LocalDateTime de, LocalDateTime a,
                                                    Collection<Presence> presences);

    /** Dernières séances d'un élève (historique). */
    List<Seance> findTop10ByEleveIdOrderByDebutDesc(Long eleveId);
}
