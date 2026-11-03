package com.gbconnected.kpivalidation.domain.manual;

import com.gbconnected.kpivalidation.domain.DomainValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/** Business rules for Spec 04 manual OEE stop-time capture. */
public final class ManualStopTimePolicy {
    public static final BigDecimal DAILY_HOURS = new BigDecimal("24");
    public static final String CAPTURE_UNIT = "HOUR";
    public static final int STORAGE_RESOLUTION_SECONDS = 1;
    private static final BigDecimal SECONDS_PER_HOUR = new BigDecimal("3600");
    // Allow only floating-decimal artifacts introduced when converting stored whole seconds
    // back into hours (DECIMAL128); do not silently round arbitrary sub-second inputs.
    private static final BigDecimal SECONDS_ROUNDTRIP_TOLERANCE = new BigDecimal("0.00000000000000000001");

    public ManualStopTimeSummary validate(Collection<BigDecimal> hours) {
        Objects.requireNonNull(hours, "hours are required");
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : hours) {
            if (value == null) throw new DomainValidationException("Stop time hours are required");
            if (value.signum() < 0) throw new DomainValidationException("Stop time hours cannot be negative");
            BigDecimal seconds = value.multiply(SECONDS_PER_HOUR);
            BigDecimal nearestSecond = seconds.setScale(0, RoundingMode.HALF_UP);
            if (seconds.subtract(nearestSecond).abs().compareTo(SECONDS_ROUNDTRIP_TOLERANCE) > 0) {
                throw new DomainValidationException("Stop time precision is finer than one second; canonical storage uses whole seconds");
            }
            total = total.add(value);
        }
        if (total.compareTo(DAILY_HOURS) > 0) {
            throw new DomainValidationException("Total daily stop time cannot exceed 24 hours");
        }
        return new ManualStopTimeSummary(total, DAILY_HOURS.subtract(total));
    }
}
