package com.gbconnected.kpivalidation.domain.manual;

import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;

/** Specs 06/07 numeric and unit rules for manual waste capture. */
public final class ManualWastePolicy {
    public static final String WASTE_CAPTURE_UNIT = "KG";
    public static final String PRODUCED_QUANTITY_UNIT = "TONNE";

    /**
     * Canonical waste.daily_manual_quantity.quantity is NUMERIC(18,6).
     * Validate exact representability, rather than allowing PostgreSQL to round
     * entered values silently or reject an oversized value during persistence.
     */
    public void validateKilograms(BigDecimal kilograms) {
        if (kilograms == null) throw new DomainValidationException("Waste kilograms are required");
        if (kilograms.signum() < 0) throw new DomainValidationException("Waste kilograms must be non-negative");
        BigDecimal normalized = kilograms.stripTrailingZeros();
        if (normalized.scale() > 6) {
            throw new DomainValidationException("Waste kilograms cannot exceed 6 fractional digits (canonical NUMERIC(18,6))");
        }
        if (normalized.compareTo(new BigDecimal("1000000000000")) >= 0) {
            throw new DomainValidationException("Waste kilograms exceed the maximum supported by canonical NUMERIC(18,6)");
        }
    }

    public void validateProducedTonnes(BigDecimal producedTonnes) {
        if (producedTonnes == null) return;
        if (producedTonnes.signum() < 0) throw new DomainValidationException("Produced tonnes must be non-negative");
    }
}
