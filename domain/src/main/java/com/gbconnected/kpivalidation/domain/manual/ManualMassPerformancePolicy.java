package com.gbconnected.kpivalidation.domain.manual;

import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;

/** Business rules for Spec 05 manual OEE performance inputs. */
public final class ManualMassPerformancePolicy {
    public static final String ELABORATED_VARIABLE = "ELABORATED_MASS";
    public static final String DELIVERED_VARIABLE = "DELIVERED_MASS";

    public void validate(BigDecimal elaboratedMass, BigDecimal deliveredMass) {
        if (elaboratedMass == null && deliveredMass == null) {
            return;
        }
        if (elaboratedMass == null || deliveredMass == null) {
            throw new DomainValidationException("Both elaboratedMass and deliveredMass are required when manual performance is captured");
        }
        if (elaboratedMass.signum() < 0) {
            throw new DomainValidationException("elaboratedMass must be non-negative");
        }
        if (deliveredMass.signum() < 0) {
            throw new DomainValidationException("deliveredMass must be non-negative");
        }
        // Spec 05 explicitly keeps cross-day operational lag valid. Do not enforce delivered <= elaborated.
    }
}
