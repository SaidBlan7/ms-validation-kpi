package com.gbconnected.kpivalidation.domain.manual;

import static org.junit.jupiter.api.Assertions.*;
import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManualStopTimePolicyTest {
    private final ManualStopTimePolicy policy = new ManualStopTimePolicy();

    @Test void acceptsDecimalValuesUpToExactly24Hours() {
        var result = policy.validate(List.of(new BigDecimal("0.125"), new BigDecimal("23.875")));
        assertEquals(0, result.totalHours().compareTo(new BigDecimal("24")));
        assertEquals(0, result.remainingHours().compareTo(BigDecimal.ZERO));
    }

    @Test void acceptsAllZero() {
        var result = policy.validate(List.of(BigDecimal.ZERO, new BigDecimal("0.000")));
        assertEquals(0, result.totalHours().compareTo(BigDecimal.ZERO));
    }

    @Test void rejectsMoreThan24HoursWithoutRounding() {
        assertThrows(DomainValidationException.class,
                () -> policy.validate(List.of(new BigDecimal("23.999999"), new BigDecimal("0.000002"))));
    }

    @Test void rejectsSubSecondPrecisionBecauseCanonicalStorageUsesWholeSeconds() {
        assertThrows(DomainValidationException.class,
                () -> policy.validate(List.of(new BigDecimal("0.0001"))));
    }

    @Test void rejectsNegativeValues() {
        assertThrows(DomainValidationException.class,
                () -> policy.validate(List.of(new BigDecimal("-0.01"))));
    }
}
