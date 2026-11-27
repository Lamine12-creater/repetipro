package bj.repetipro.repository;

import bj.repetipro.model.EnfantParent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnfantParentRepository extends JpaRepository<EnfantParent, Long> {
    List<EnfantParent> findByParentIdOrderByIdAsc(Long parentId);
    Optional<EnfantParent> findByIdAndParentId(Long id, Long parentId);
}
