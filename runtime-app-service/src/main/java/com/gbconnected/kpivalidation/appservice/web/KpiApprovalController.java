package com.gbconnected.kpivalidation.appservice.web;

import com.gbconnected.kpivalidation.application.approval.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/kpi-approval", "/api/kpi-approval"})
public final class KpiApprovalController {
    private static final Logger LOG = LoggerFactory.getLogger(KpiApprovalController.class);
    private final KpiApprovalUseCase useCase;

    public KpiApprovalController(KpiApprovalUseCase useCase) { this.useCase = useCase; }

    @GetMapping("/pending")
    public List<ApprovalValidationView> pending(@AuthenticationPrincipal Jwt jwt) {
        var actor = RequestActorContextResolver.resolve(jwt);
        var result = useCase.pending(actor);
        LOG.info("event=kpi_approval_pending_open correlationId={} resultCount={}", correlationId(), result.size());
        return result;
    }

    @GetMapping("/validations/{id}")
    public ApprovalValidationView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        var result = useCase.get(RequestActorContextResolver.resolve(jwt), id);
        LOG.info("event=kpi_approval_review correlationId={} validationId={} plantId={} status={}",
                correlationId(), id, result.plantId(), result.status());
        return result;
    }

    @PostMapping("/validations/{id}/decision")
    public ApprovalValidationView decide(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @RequestBody DecisionRequest body) {
        if (body == null || body.decision() == null || body.decision().isBlank()) {
            throw new IllegalArgumentException("decision is required");
        }
        ApprovalDecision decision = ApprovalDecision.valueOf(body.decision().trim().toUpperCase(Locale.ROOT));
        var result = useCase.decide(RequestActorContextResolver.resolve(jwt), id, decision, correlationId());
        LOG.info("event=kpi_approval_decision correlationId={} validationId={} plantId={} decision={} status={}",
                correlationId(), id, result.plantId(), decision, result.status());
        return result;
    }

    @GetMapping("/policy")
    public ApprovalPolicyView policy() { return useCase.policy(); }

    public record DecisionRequest(String decision) { }

    private static String correlationId() {
        String value = MDC.get("correlationId");
        return value == null ? "" : value;
    }
}
