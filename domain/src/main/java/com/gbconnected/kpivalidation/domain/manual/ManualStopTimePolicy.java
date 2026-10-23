package com.gbconnected.kpivalidation.domain.manual;

import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

/** Business rules for Spec 04 manual OEE stop-time capture. */
public final class ManualStopTimePolicy {
    public static final BigDecimal DAILY_HOURS = new BigDecimal("24");
    public static final String CAPTURE_UNIT = "HOUR";

    public ManualStopTimeSummary validate(Collection<BigDecimal> hours) {
        Objects.requireNonNull(hours, "hours are required");
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : hours) {
            if (value == null) throw new DomainValidationException("Stop time hours are required");
            if (value.signum() < 0) throw new DomainValidationException("Stop time hours cannot be negative");
            total = total.add(value);
        }
        if (total.compareTo(DAILY_HOURS) > 0) {
            throw new DomainValidationException("Total daily stop time cannot exceed 24 hours");
        }
        return new ManualStopTimeSummary(total, DAILY_HOURS.subtract(total));
    }
}
