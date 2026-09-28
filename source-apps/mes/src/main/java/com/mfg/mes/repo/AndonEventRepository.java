package com.mfg.mes.repo;

import com.mfg.mes.entity.AndonEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AndonEventRepository extends JpaRepository<AndonEvent, Long> {
    boolean existsByEventNo(String eventNo);
    boolean existsByEventNoAndIdNot(String eventNo, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AndonEvent e where e.id = :id")
    Optional<AndonEvent> findByIdForUpdate(@Param("id") Long id);
}
