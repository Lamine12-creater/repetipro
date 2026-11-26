package bj.repetipro.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import bj.repetipro.model.StatutPaiement;
import bj.repetipro.model.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    Optional<Paiement> findByReference(String reference);

    Optional<Paiement> findByFournisseurAndReferenceFournisseur(String fournisseur, String referenceFournisseur);

    List<Paiement> findTop5ByRepetiteurIdOrderByDateCreationDesc(Long repetiteurId);
    List<Paiement> findTop8ByOrderByDateCreationDesc();
    long countByStatut(StatutPaiement statut);

    @Query("select coalesce(sum(p.montant), 0) from Paiement p where p.statut = :statut")
    long sommeMontantParStatut(@Param("statut") StatutPaiement statut);
}
