package com.mfg.plm.repo;
import com.mfg.plm.entity.EngineeringChange;import org.springframework.data.jpa.repository.JpaRepository;import java.util.Optional;
public interface EngineeringChangeRepository extends JpaRepository<EngineeringChange,Long>{boolean existsByEcnNo(String ecnNo);Optional<EngineeringChange> findByEcnNo(String ecnNo);}
