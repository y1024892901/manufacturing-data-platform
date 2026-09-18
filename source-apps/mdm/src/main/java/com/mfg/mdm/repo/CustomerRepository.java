package com.mfg.mdm.repo;

import com.mfg.mdm.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerCode(String customerCode);

    boolean existsByCustomerCode(String customerCode);

    Page<Customer> findByCustomerNameContainingOrCustomerCodeContaining(
            String name, String code, Pageable pageable);

    Page<Customer> findByStatus(String status, Pageable pageable);

    /** 业务系统可选用的客户 */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.status IN ('PUBLISHED','CHANGING')
            ORDER BY c.customerCode
            """)
    List<Customer> findConsumable();

    /** 一客多码识别：按统一社会信用代码找重复 */
    List<Customer> findByUnifiedSocialCodeAndIdNot(String socialCode, Long id);

    /** 重名检测（用于提交时的疑重复提示） */
    List<Customer> findByCustomerName(String customerName);
}
