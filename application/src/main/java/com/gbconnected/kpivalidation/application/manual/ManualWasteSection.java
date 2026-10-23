package com.gbconnected.kpivalidation.application.manual;
import java.math.BigDecimal;
import java.util.List;
public record ManualWasteSection(List<ManualWasteInput> waste, BigDecimal producedTonnes) {
    public ManualWasteSection { waste = waste == null ? List.of() : List.copyOf(waste); }
    public boolean empty() { return waste.isEmpty() && producedTonnes == null; }
}
