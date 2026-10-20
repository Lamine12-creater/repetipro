package bj.repetipro.repository;

import bj.repetipro.model.Repetiteur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data écrit les requêtes SQL à partir du nom des méthodes. */
public interface RepetiteurRepository extends JpaRepository<Repetiteur, Long> {
    Optional<Repetiteur> findByTelephone(String telephone);
    boolean existsByTelephone(String telephone);
    List<Repetiteur> findAllByOrderByDateInscriptionDesc();
}
