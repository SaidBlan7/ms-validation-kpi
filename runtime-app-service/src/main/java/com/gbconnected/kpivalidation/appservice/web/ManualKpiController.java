package com.gbconnected.kpivalidation.appservice.web;
import com.gbconnected.kpivalidation.application.ActorContext;
import com.gbconnected.kpivalidation.application.manual.*;
import com.gbconnected.kpivalidation.domain.manual.ManualKpiDateSelection;
import java.time.LocalDate; import java.util.*;
import org.slf4j.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.security.oauth2.jwt.Jwt; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping({"/api/v1/manual-kpis", "/api/manual-kpis"})
public class ManualKpiController {
    private static final Logger LOG=LoggerFactory.getLogger(ManualKpiController.class); private final ManualKpiUseCase useCase;
    public ManualKpiController(ManualKpiUseCase useCase){this.useCase=useCase;}
    @GetMapping("/policy") public ManualKpiPolicyView policy(){LOG.info("event=manual_kpi_module_open correlationId={} result=OK",correlation());return useCase.policy();}
    @GetMapping("/date-selection") public ManualKpiDateSelection dateSelection(@AuthenticationPrincipal Jwt jwt,@RequestParam long plantId,@RequestParam long lineId,@RequestParam LocalDate businessDate){
        ActorContext actor=RequestActorContextResolver.resolve(jwt);
        ManualKpiDateSelection r=useCase.dateSelection(actor,plantId,lineId,businessDate);
        LOG.info("event=manual_kpi_date_selection actorObject={} plantId={} lineId={} businessDate={} selectable={} rejectionCode={} correlationId={} result=OK",actor.objectId(),plantId,lineId,businessDate,r.selectable(),r.rejectionCode(),correlation());
        return r;
    }
    @GetMapping public ManualKpiRecord get(@AuthenticationPrincipal Jwt jwt,@RequestParam long plantId,@RequestParam long lineId,@RequestParam LocalDate businessDate){
        ActorContext actor=RequestActorContextResolver.resolve(jwt); LOG.info("event=manual_kpi_selection actorObject={} plantId={} lineId={} businessDate={} correlationId={}",actor.objectId(),plantId,lineId,businessDate,correlation());
        try { ManualKpiRecord r=useCase.get(actor,plantId,lineId,businessDate); LOG.info("event=manual_kpi_read actorObject={} plantId={} lineId={} businessDate={} recordId={} correlationId={} result=OK",actor.objectId(),plantId,lineId,businessDate,r.recordId(),correlation()); return r; }
        catch(RuntimeException e){LOG.warn("event=manual_kpi_rejected action=READ actorObject={} plantId={} lineId={} businessDate={} correlationId={} reason={}",actor.objectId(),plantId,lineId,businessDate,correlation(),safe(e));throw e;}
    }
    @PutMapping public ManualKpiRecord save(@AuthenticationPrincipal Jwt jwt,@RequestBody ManualKpiHttpRequest request){
        ActorContext actor=RequestActorContextResolver.resolve(jwt); SaveManualKpiCommand c=toCommand(request);
        try { ManualKpiRecord r=useCase.save(actor,c,correlation()); LOG.info("event=manual_kpi_saved actorObject={} plantId={} lineId={} businessDate={} recordId={} version={} correlationId={} result=OK",actor.objectId(),r.plantId(),r.lineId(),r.businessDate(),r.recordId(),r.version(),correlation()); return r; }
        catch(RuntimeException e){LOG.warn("event=manual_kpi_rejected action=SAVE actorObject={} plantId={} lineId={} businessDate={} correlationId={} reason={}",actor.objectId(),c.plantId(),c.lineId(),c.businessDate(),correlation(),safe(e));throw e;}
    }
    private static SaveManualKpiCommand toCommand(ManualKpiHttpRequest r){
        if(r==null) throw new IllegalArgumentException("JSON body is required");
        var stops=r.oee()==null||r.oee().stopTimes()==null?List.<ManualStopTimeInput>of():r.oee().stopTimes().stream().map(x->new ManualStopTimeInput(x.stopClassificationId(),x.seconds())).toList();
        var perf=r.oee()==null||r.oee().performance()==null?List.<ManualPerformanceInput>of():r.oee().performance().stream().map(x->new ManualPerformanceInput(x.metricDefinitionId(),x.value(),x.uomId())).toList();
        var wastes=r.waste()==null||r.waste().entries()==null?List.<ManualWasteInput>of():r.waste().entries().stream().map(x->new ManualWasteInput(x.wasteClassificationId(),x.wasteSubclassificationId(),x.kilograms())).toList();
        return new SaveManualKpiCommand(r.plantId(),r.lineId(),r.businessDate(),new ManualOeeSection(stops,perf),new ManualWasteSection(wastes,r.waste()==null?null:r.waste().producedTonnes()));
    }
    private static String correlation(){String x=MDC.get("correlationId");return x==null?"":x;}
    private static String safe(RuntimeException e){String x=e.getMessage();return x==null||x.isBlank()?e.getClass().getSimpleName():x;}
}
