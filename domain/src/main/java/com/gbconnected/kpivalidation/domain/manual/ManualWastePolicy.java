package com.gbconnected.kpivalidation.domain.manual;

import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;

/** Specs 06/07 numeric and unit rules for manual waste capture. */
public final class ManualWastePolicy {
    public static final String WASTE_CAPTURE_UNIT = "KG";
    public static final String PRODUCED_QUANTITY_UNIT = "TONNE";

    public void validateKilograms(BigDecimal kilograms) {
        if (kilograms == null) throw new DomainValidationException("Waste kilograms are required");
        if (kilograms.signum() < 0) throw new DomainValidationException("Waste kilograms must be non-negative");
    }

    public void validateProducedTonnes(BigDecimal producedTonnes) {
        if (producedTonnes == null) return;
        if (producedTonnes.signum() < 0) throw new DomainValidationException("Produced tonnes must be non-negative");
    }
}
