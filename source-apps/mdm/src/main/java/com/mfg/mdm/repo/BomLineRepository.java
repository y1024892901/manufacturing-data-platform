package com.mfg.mdm.repo;

import com.mfg.mdm.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BomLineRepository extends JpaRepository<BomLine, Long> {

    List<BomLine> findByBomIdOrderByLineNoAsc(Long bomId);

    void deleteByBomId(Long bomId);
}
