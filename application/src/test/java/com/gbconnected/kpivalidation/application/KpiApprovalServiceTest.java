package com.gbconnected.kpivalidation.application;

import com.gbconnected.kpivalidation.application.approval.*;
import com.gbconnected.kpivalidation.application.port.out.*;
import com.gbconnected.kpivalidation.application.port.out.approval.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KpiApprovalServiceTest {
    private static final UUID TENANT = UUID.randomUUID(), OBJECT = UUID.randomUUID();
    private static final ActorContext ACTOR = new ActorContext(TENANT, OBJECT, Set.of("KPI_APPROVER"));
    private static final Instant NOW = Instant.parse("2026-11-03T21:00:00Z");

    private static final class Store implements KpiApprovalPersistencePort {
        ApprovalValidationView view;
        int decisions;
        Store(long proposer, String state) { view = sample(proposer,state); }
        public List<ApprovalValidationView> pending(){return List.of(view);}
        public List<ApprovalValidationView> all(){return List.of(view);}
        public Optional<ApprovalValidationView> find(long id){return id==31?Optional.of(view):Optional.empty();}
        public ApprovalValidationView decide(long id,long actor,ApprovalDecision choice,Instant ignored,String correlation){
            decisions++;
            return view;
        }
        public void expirePending(Instant now,String correlation) { }
        public boolean supportsWasteValidations(){return true;}
    }
    private static ApprovalValidationView sample(long proposer,String state){
        return new ApprovalValidationView(31,"OEE","KPI_RESULT",state,9,"P9","Planta",11L,"L11","Linea",
                LocalDate.parse("2026-11-02"),"OPERATIVE",null,"cause","plan",null,
                proposer,"validador",NOW.minusSeconds(100),NOW.plusSeconds(100),null,null,null,"PENDING".equals(state),List.of(
                new ApprovalValueChangeView(8L,"OEE","OEE",null,null,null,null,null,null,null,null,
                        java.math.BigDecimal.TEN,java.math.BigDecimal.ONE,null,null,1L,"PCT","%")));
    }
    private static KpiApprovalService service(Store store,boolean hasPlant,boolean isApprover){
        ActorIdentityPort identity=new ActorIdentityPort(){
            public Optional<ActorIdentitySnapshot> findByEntraIdentity(UUID t,UUID o){return Optional.of(new ActorIdentitySnapshot(17L,"Jefe",null,true,"TEST"));}
            public String source(){return "TEST";}
        };
        AccessScopePort access=(t,o,p)->hasPlant;
        OperationalContextPort scope=new OperationalContextPort(){
            public Optional<PlantContext> findPlant(long id){return Optional.empty();}
            public boolean lineBelongsToPlant(long l,long p){return l==11 && p==9;}
        };
        KpiApprovalAuthorizationPort auth=new KpiApprovalAuthorizationPort(){
            public boolean authorized(ActorContext a,long u,long p){return isApprover;}
            public String source(){return "TEST";}
        };
        return new KpiApprovalService(identity,access,scope,FeatureActivationPort.enabledByDefault("TEST"),auth,store,Clock.fixed(NOW,ZoneOffset.UTC));
    }
    @Test void approvedDecisionDelegatesToAtomicPersistence(){
        Store db=new Store(19,"PENDING");
        service(db,true,true).decide(ACTOR,31,ApprovalDecision.APPROVED,"test-1");
        assertEquals(1,db.decisions);
    }
    @Test void sameUserCannotApproveOwnProposal(){
        Store db=new Store(17,"PENDING");
        assertThrows(AuthorizationException.class,()->service(db,true,true).decide(ACTOR,31,ApprovalDecision.APPROVED,"test-2"));
        assertEquals(0,db.decisions);
    }
    @Test void revokedPlantPermissionRejectsDecision(){
        Store db=new Store(19,"PENDING");
        assertThrows(AuthorizationException.class,()->service(db,false,true).decide(ACTOR,31,ApprovalDecision.REJECTED,"test-3"));
        assertEquals(0,db.decisions);
    }
    @Test void revokedApprovalAssignmentRejectsDecision(){
        Store db=new Store(19,"PENDING");
        assertThrows(AuthorizationException.class,()->service(db,true,false).decide(ACTOR,31,ApprovalDecision.REJECTED,"test-4"));
        assertEquals(0,db.decisions);
    }
    @Test void deadlineIsDelegatedToDatabaseRatherThanUnreliableJvmPrecheck(){
        Store db=new Store(19,"PENDING");
        db.view=new ApprovalValidationView(31,"OEE","KPI_RESULT","PENDING",9,"P9","Planta",11L,"L11","Linea",
                LocalDate.parse("2026-11-02"),"OPERATIVE",null,"cause","plan",null,19,"validador",NOW.minusSeconds(100),
                NOW.minusSeconds(1),null,null,null,false,List.of());
        service(db,true,true).decide(ACTOR,31,ApprovalDecision.REJECTED,"test-5");
        assertEquals(1,db.decisions);
    }

    private static ApprovalValidationView waste(String domain, List<ApprovalValueChangeView> details) {
        return new ApprovalValidationView(31,domain,"WASTE_METRIC","PENDING",9,"P9","Planta",11L,"L11","Linea",
                LocalDate.parse("2026-11-02"),"SYSTEM",null,null,null,"GBS-123",19,"validador",
                NOW.minusSeconds(100),NOW.plusSeconds(100),null,null,null,true,details);
    }
    private static ApprovalValueChangeView wasteValue(String domain) {
        return new ApprovalValueChangeView(null,null,null,null,null,null,null,4L,"WASTE_KG","Desperdicio",
                domain,new java.math.BigDecimal("100.000000"),new java.math.BigDecimal("90.000000"),null,null,1L,"KG","kg");
    }
    @Test void emptyOeeValidationCannotBeDecided() {
        Store db=new Store(19,"PENDING");
        db.view=waste("OEE",List.of());
        assertThrows(ApprovalConflictException.class,()->service(db,true,true).decide(ACTOR,31,ApprovalDecision.APPROVED,"empty"));
        assertEquals(0,db.decisions);
    }
    @Test void wasteApprovalUsesExistingCanonicalValidationWithoutNewWasteMovement() {
        Store db=new Store(19,"PENDING"); db.view=waste("WASTE",List.of(wasteValue("WASTE")));
        service(db,true,true).decide(ACTOR,31,ApprovalDecision.APPROVED,"waste-approve");
        assertEquals(1,db.decisions);
    }
    @Test void wasteRejectionUsesSameAtomicDecisionPath() {
        Store db=new Store(19,"PENDING"); db.view=waste("WASTE",List.of(wasteValue("WASTE")));
        service(db,true,true).decide(ACTOR,31,ApprovalDecision.REJECTED,"waste-reject");
        assertEquals(1,db.decisions);
    }
    @Test void mixedWasteAndOeeValidationIsBlocked() {
        Store db=new Store(19,"PENDING"); db.view=waste("MIXED",List.of(wasteValue("WASTE")));
        assertThrows(ApprovalConflictException.class,()->service(db,true,true).decide(ACTOR,31,ApprovalDecision.APPROVED,"waste-mixed"));
        assertEquals(0,db.decisions);
    }
    @Test void wasteWithoutSourceDetailIsBlocked() {
        Store db=new Store(19,"PENDING"); db.view=waste("WASTE",List.of());
        assertThrows(ApprovalConflictException.class,()->service(db,true,true).decide(ACTOR,31,ApprovalDecision.APPROVED,"waste-empty"));
        assertEquals(0,db.decisions);
    }
    @Test void wasteWithIncorrectMetricSourceIsBlocked() {
        Store db=new Store(19,"PENDING"); db.view=waste("WASTE",List.of(wasteValue("SCHEDULING")));
        assertThrows(ApprovalConflictException.class,()->service(db,true,true).decide(ACTOR,31,ApprovalDecision.APPROVED,"waste-foreign"));
        assertEquals(0,db.decisions);
    }
}
