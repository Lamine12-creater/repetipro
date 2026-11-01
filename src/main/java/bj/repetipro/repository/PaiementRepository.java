package bj.repetipro.repository;

import bj.repetipro.model.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    Optional<Paiement> findByReference(String reference);

    Optional<Paiement> findByFournisseurAndReferenceFournisseur(String fournisseur, String referenceFournisseur);

    List<Paiement> findTop5ByRepetiteurIdOrderByDateCreationDesc(Long repetiteurId);
}
