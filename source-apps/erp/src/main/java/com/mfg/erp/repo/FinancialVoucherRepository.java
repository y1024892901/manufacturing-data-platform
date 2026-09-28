package com.mfg.erp.repo;

import com.mfg.erp.entity.FinancialVoucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialVoucherRepository extends JpaRepository<FinancialVoucher, Long> {
    boolean existsByVoucherNoAndCompanyCodeAndFiscalPeriod(String no, String company, String period);

    @Query("""
            SELECT v FROM FinancialVoucher v
            WHERE (:keyword IS NULL OR :keyword = '' OR
                   LOWER(v.voucherNo) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(v.sourceNo, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(v.salesOrderNo, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(v.summary, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:status IS NULL OR :status = '' OR v.voucherStatus = :status)
            """)
    Page<FinancialVoucher> search(@Param("keyword") String keyword, @Param("status") String status,
                                  Pageable pageable);
}
