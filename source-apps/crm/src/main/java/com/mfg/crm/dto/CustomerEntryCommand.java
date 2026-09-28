package com.mfg.crm.dto;

import java.math.BigDecimal;

/** Fields accepted by the CRM customer registration entry point. */
public record CustomerEntryCommand(
        String customerCode,
        String customerName,
        String shortName,
        String unifiedSocialCode,
        String customerLevel,
        String customerType,
        String industry,
        String region,
        BigDecimal creditLimit,
        String paymentTerms,
        String taxNo,
        String contactPerson,
        String contactPhone,
        String contactEmail,
        String address) {
}
