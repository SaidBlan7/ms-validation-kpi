# Final review — Specs 01-07

This file describes the current integrated implementation only. Older incremental decisions in CHANGES.md are historical and are superseded wherever they conflict with this document.

## Closed specifications

- **Spec 01**: D-1 calendar in `core.plant.timezone_name`; immutable original-value snapshot; one proposal opportunity per canonical datum through this service; new proposals persist as `PENDING`; Approval KPI owns approval/rejection and effective application.
- **Spec 02**: plant-specific KPI validator entitlement; maximum two effective validators per plant; assignments remain active until explicit revocation; users may be assigned to multiple plants; revocation removes future access but does not delete or mutate existing `PENDING` proposals.
- **Spec 03**: D-1 means the immediately previous local calendar day in the plant timezone; weekends and holidays are not skipped; every write revalidates D-1 server-side; stale screens/URLs are rejected after plant-local midnight.
- **Spec 04**: stop-time reference is derived from `stops.stop_event`, `stops.stop_fragment`, `stops.classification_history` and `stops.stop_classification`; only `PROGRAMMED` and `UNPROGRAMMED` classifications are correctable; one classification correction is one validation proposal. The cross-classification total-time formula remains intentionally open because the specification explicitly forbids assuming a 24-hour rule without evidence.
- **Spec 05**: elaborated mass comes from `scheduling.batch_execution`; delivered mass comes from `scheduling.finished_product_output`, using canonical `manufacturing.packaging_conversion` when conversion to a MASS UOM is required. Durable variable identity is `kpi.kpi_validation_detail.metric_definition_id` -> `kpi.metric_definition`. Proposals persist as `PENDING`. Missing source data is not treated as zero. Special line-specific performance formulas remain owned by OEE calculation/approval and are not invented by this service.
- **Spec 06**: `OPERATIVE` requires cause + action plan; `SYSTEM` requires incident number. Incident number is normalized single-line text with a 200 Unicode-character maximum. Cause and action plan are normalized with a 2000 Unicode-character maximum each. No regex and no online GB Support validation are applied by this service.
- **Spec 07**: `kpi.kpi_validation.proposed_at` is the absolute timestamp source of truth; presentation uses the plant IANA timezone. Historical actor identity is anchored by canonical `proposed_by_user_id` plus Entra tenant/object identifiers; current display name/email may be shown as presentation attributes. This microservice does not delete validation/audit evidence.

## Canonical persistence

Validation proposals use `kpi.kpi_validation` plus `kpi.kpi_validation_detail`. OEE uses `kpi_result_id`; stop time uses `stop_classification_id`; masses use `metric_definition_id`. Reason/evidence fields are stored on the validation header and written atomically with the detail.

## Intentional external dependencies / open business rules

- Approval KPI owns approval/rejection, approval comments and application of the approved correction.
- GB Support online incident validation is not part of the current contract; only the incident reference is captured.
- Spec 04 cross-classification total-time mathematics remains undefined by the source specification.
- Spec 05 special line-specific performance formulas and the allowed elaborated-vs-delivered relationship remain outside this validation service until formally defined.

## Source handoff hygiene

Generated `target/` directories are not canonical source and should not be committed or used as proof of the current build. Rebuild from source in the normal Maven environment:

```bash
rm -rf target */target
mvn clean verify
```
