package com.gbconnected.kpivalidation.adapter.memory;

import com.gbconnected.kpivalidation.application.approval.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryKpiApprovalPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-11-03T20:00:00Z");
    private static ApprovalValidationView proposal(long id, Instant expires) {
        var change = new ApprovalValueChangeView(null,null,null,null,null,null,null,11L,
                "WASTE_KG","Waste kg","WASTE",new BigDecimal("100"),new BigDecimal("80"),
                null,null,1L,"KG","kg");
        return new ApprovalValidationView(id,"WASTE","WASTE_METRIC","PENDING",5L,"P5","Plant",
                7L,"L7","Line",LocalDate.parse("2026-11-02"),"SYSTEM",null,null,null,"SUP-11",
                10L,"Validator",NOW.minusSeconds(100),expires,null,null,null,true,List.of(change));
    }
    @Test void approveUsesProposedValueAndDoesNotCreateWasteMovements() {
        var store = new InMemoryValidationPersistenceAdapter();
        assertTrue(store.supportsWasteValidations());
        store.registerWasteValidation(proposal(101,NOW.plusSeconds(40)));
        assertEquals(1,store.pending().size());
        var result=store.decide(101,20,ApprovalDecision.APPROVED,NOW,"a");
        assertEquals("APPROVED",result.status());
        assertEquals(0,result.changes().getFirst().effectiveValue().compareTo(new BigDecimal("80")));
        assertEquals(1,store.all().size());
    }
    @Test void rejectUsesOriginalValue() {
        var store = new InMemoryValidationPersistenceAdapter();
        store.registerWasteValidation(proposal(102,NOW.plusSeconds(40)));
        var result=store.decide(102,20,ApprovalDecision.REJECTED,NOW,"r");
        assertEquals("REJECTED",result.status());
        assertEquals(0,result.changes().getFirst().effectiveValue().compareTo(new BigDecimal("100")));
    }
    @Test void identicalRetryIsIdempotentButOtherDecisionsAreNot() {
        var store=new InMemoryValidationPersistenceAdapter();
        store.registerWasteValidation(proposal(103,NOW.plusSeconds(40)));
        store.decide(103,20,ApprovalDecision.APPROVED,NOW,"first");
        assertEquals("APPROVED",store.decide(103,20,ApprovalDecision.APPROVED,NOW.plusSeconds(1),"retry").status());
        assertThrows(ApprovalConflictException.class,()->store.decide(103,21,ApprovalDecision.APPROVED,NOW,"other"));
        assertThrows(ApprovalConflictException.class,()->store.decide(103,20,ApprovalDecision.REJECTED,NOW,"change"));
    }
    @Test void expiredOrSelfApprovedProposalIsBlocked() {
        var store=new InMemoryValidationPersistenceAdapter();
        store.registerWasteValidation(proposal(104,NOW.minusSeconds(1)));
        assertThrows(ApprovalConflictException.class,()->store.decide(104,20,ApprovalDecision.APPROVED,NOW,"late"));
        assertEquals("EXPIRED",store.find(104).orElseThrow().status());
        assertThrows(ApprovalConflictException.class,()->store.decide(104,20,ApprovalDecision.APPROVED,NOW,"again"));
        store.registerWasteValidation(proposal(105,NOW.plusSeconds(5)));
        assertThrows(ApprovalConflictException.class,()->store.decide(105,10,ApprovalDecision.APPROVED,NOW,"self"));
    }
    @Test void rejectsInvalidOrDuplicateWasteProposals() {
        var store=new InMemoryValidationPersistenceAdapter();
        store.registerWasteValidation(proposal(106,NOW.plusSeconds(5)));
        assertThrows(ApprovalConflictException.class,()->store.registerWasteValidation(proposal(106,NOW.plusSeconds(5))));
        var bad=proposal(107,NOW.plusSeconds(5));
        var empty=new ApprovalValidationView(bad.validationId(),bad.validationDomain(),bad.validationKind(),bad.status(),
                bad.plantId(),bad.plantCode(),bad.plantName(),bad.lineId(),bad.lineCode(),bad.lineName(),bad.businessDate(),
                bad.reasonType(),bad.justification(),bad.cause(),bad.actionPlan(),bad.incidentReference(),
                bad.proposedByUserId(),bad.proposedByDisplayName(),bad.proposedAt(),bad.expiresAt(),
                bad.decidedByUserId(),bad.decidedByDisplayName(),bad.decidedAt(),true,List.of());
        assertThrows(IllegalArgumentException.class,()->store.registerWasteValidation(empty));
    }
}
