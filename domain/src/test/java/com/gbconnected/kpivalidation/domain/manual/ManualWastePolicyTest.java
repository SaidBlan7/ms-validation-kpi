package com.gbconnected.kpivalidation.domain.manual;

import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManualWastePolicyTest {
    private final ManualWastePolicy policy = new ManualWastePolicy();

    @Test void acceptsZeroAndDecimalsForWasteKg() {
        assertDoesNotThrow(() -> policy.validateKilograms(BigDecimal.ZERO));
        assertDoesNotThrow(() -> policy.validateKilograms(new BigDecimal("0.001")));
        assertDoesNotThrow(() -> policy.validateKilograms(new BigDecimal("12.750")));
    }

    @Test void rejectsNegativeWasteKg() {
        assertThrows(DomainValidationException.class, () -> policy.validateKilograms(new BigDecimal("-0.001")));
    }

    @Test void producedTonnesMayBeZeroOrDecimalAndMayBeAbsent() {
        assertDoesNotThrow(() -> policy.validateProducedTonnes(null));
        assertDoesNotThrow(() -> policy.validateProducedTonnes(BigDecimal.ZERO));
        assertDoesNotThrow(() -> policy.validateProducedTonnes(new BigDecimal("0.0001")));
        assertDoesNotThrow(() -> policy.validateProducedTonnes(new BigDecimal("25.400")));
    }

    @Test void rejectsNegativeProducedTonnes() {
        assertThrows(DomainValidationException.class, () -> policy.validateProducedTonnes(new BigDecimal("-0.0001")));
    }
}
