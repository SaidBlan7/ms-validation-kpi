package com.gbconnected.kpivalidation.domain.manual;

import static org.junit.jupiter.api.Assertions.*;
import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ManualMassPerformancePolicyTest {
    private final ManualMassPerformancePolicy policy = new ManualMassPerformancePolicy();

    @Test void acceptsZeroDecimalsAndDeliveredGreaterThanElaborated() {
        assertDoesNotThrow(() -> policy.validate(new BigDecimal("0.5"), new BigDecimal("1.25")));
        assertDoesNotThrow(() -> policy.validate(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test void rejectsNegativeValues() {
        assertThrows(DomainValidationException.class, () -> policy.validate(new BigDecimal("-0.1"), BigDecimal.ZERO));
        assertThrows(DomainValidationException.class, () -> policy.validate(BigDecimal.ZERO, new BigDecimal("-0.1")));
    }

    @Test void requiresThePairWhenPerformanceIsPresent() {
        assertThrows(DomainValidationException.class, () -> policy.validate(BigDecimal.ONE, null));
        assertThrows(DomainValidationException.class, () -> policy.validate(null, BigDecimal.ONE));
        assertDoesNotThrow(() -> policy.validate(null, null));
    }
}
