package com.mfg.mdm.repo;
import com.mfg.mdm.entity.BomSubstitute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BomSubstituteRepository extends JpaRepository<BomSubstitute,Long>{
    List<BomSubstitute> findByBomLineIdOrderByPriorityNoAsc(Long bomLineId);
    boolean existsByBomLineIdAndSubstituteMaterialCode(Long bomLineId,String code);
}
