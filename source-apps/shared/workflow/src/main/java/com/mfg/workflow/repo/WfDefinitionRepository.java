package com.mfg.workflow.repo;

import com.mfg.workflow.entity.WfDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WfDefinitionRepository extends JpaRepository<WfDefinition, Long> {

    Optional<WfDefinition> findByDefCodeAndEnabledTrue(String defCode);

    Optional<WfDefinition> findByBizTypeAndEnabledTrue(String bizType);

    List<WfDefinition> findByEnabledTrueOrderByIdAsc();
}
