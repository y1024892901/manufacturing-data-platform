package com.mfg.plm.repo;

import com.mfg.plm.entity.EngineeringChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EngineeringChangeRepository extends JpaRepository<EngineeringChange, Long> {
    boolean existsByEcnNo(String ecnNo);
    Optional<EngineeringChange> findByEcnNo(String ecnNo);
    Page<EngineeringChange> findByEcnNoContainingIgnoreCaseOrEcnTitleContainingIgnoreCase(String ecnNo, String ecnTitle, Pageable pageable);
}
