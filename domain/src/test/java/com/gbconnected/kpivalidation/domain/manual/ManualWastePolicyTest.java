package com.gbconnected.kpivalidation.domain.manual;

import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManualWastePolicyTest {
    private final ManualWastePolicy policy = new ManualWastePolicy();
    @Test void acceptsZeroAndDecimals(){ assertDoesNotThrow(() -> policy.validateKilograms(new BigDecimal("0.125"))); }
    @Test void rejectsNegative(){ assertThrows(DomainValidationException.class, () -> policy.validateKilograms(new BigDecimal("-0.001"))); }
    @Test void rejectsNull(){ assertThrows(DomainValidationException.class, () -> policy.validateKilograms(null)); }
}
