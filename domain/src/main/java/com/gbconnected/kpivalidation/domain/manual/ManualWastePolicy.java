package com.gbconnected.kpivalidation.domain.manual;

import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;

/** Spec 06 numeric rules for manual waste capture. */
public final class ManualWastePolicy {
    public static final String CAPTURE_UNIT = "KG";

    public void validateKilograms(BigDecimal kilograms) {
        if (kilograms == null) throw new DomainValidationException("Waste kilograms are required");
        if (kilograms.signum() < 0) throw new DomainValidationException("Waste kilograms must be non-negative");
    }
}
