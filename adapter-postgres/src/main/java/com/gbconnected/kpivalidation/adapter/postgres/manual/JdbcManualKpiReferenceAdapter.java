package com.gbconnected.kpivalidation.adapter.postgres.manual;

import com.gbconnected.kpivalidation.application.manual.ManualStopClassificationView;
import com.gbconnected.kpivalidation.application.manual.ManualWasteCatalogEntryView;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiReferencePort;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

public final class JdbcManualKpiReferenceAdapter implements ManualKpiReferencePort {
    private final DataSource ds;
    public JdbcManualKpiReferenceAdapter(DataSource ds){this.ds=Objects.requireNonNull(ds);}

    @Override
    public List<ManualStopClassificationView> activeStopClassifications(long plantId) {
        String sql="""
                WITH plant_org AS (
                    SELECT organization_id
                      FROM core.plant_context_assignment
                     WHERE plant_id = ?
                       AND valid_from <= CURRENT_TIMESTAMP
                       AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP)
                     ORDER BY valid_from DESC
                     LIMIT 1
                ), scoped AS (
                    SELECT DISTINCT ON (sc.classification_code)
                           sc.stop_classification_id,
                           sc.classification_code,
                           sc.classification_name,
                           sc.display_order,
                           sc.oee_stop_type_code,
                           sc.organization_id
                      FROM stops.stop_classification sc
                      LEFT JOIN plant_org po ON TRUE
                     WHERE sc.is_active = TRUE
                       AND (sc.organization_id IS NULL OR sc.organization_id = po.organization_id)
                     ORDER BY sc.classification_code,
                              (sc.organization_id IS NOT NULL) DESC,
                              sc.stop_classification_id DESC
                )
                SELECT stop_classification_id, classification_code, classification_name,
                       display_order, oee_stop_type_code
                  FROM scoped
                 ORDER BY display_order NULLS LAST, classification_name, stop_classification_id
                """;
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setLong(1,plantId);
            try(ResultSet r=p.executeQuery()){
                List<ManualStopClassificationView> out=new ArrayList<>();
                while(r.next()){
                    String type=r.getString("oee_stop_type_code");
                    Integer order=(Integer)r.getObject("display_order");
                    out.add(new ManualStopClassificationView(
                            r.getLong("stop_classification_id"),
                            r.getString("classification_code"),
                            r.getString("classification_name"),
                            order,
                            type,
                            "PROGRAMMED".equals(type),
                            "UNPROGRAMMED".equals(type)));
                }
                return List.copyOf(out);
            }
        }catch(SQLException e){throw new IllegalStateException("Unable to load Manual KPI stop classifications",e);}
    }

    @Override
    public List<ManualWasteCatalogEntryView> activeWasteSubclassifications(long plantId) {
        String sql="""
                WITH plant_org AS (
                    SELECT organization_id
                      FROM core.plant_context_assignment
                     WHERE plant_id = ?
                       AND valid_from <= CURRENT_TIMESTAMP
                       AND (valid_to IS NULL OR valid_to > CURRENT_TIMESTAMP)
                     ORDER BY valid_from DESC
                     LIMIT 1
                ), scoped AS (
                    SELECT DISTINCT ON (wc.classification_code, ws.subclassification_code)
                           wc.waste_classification_id,
                           wc.classification_code,
                           wc.classification_name,
                           ws.waste_subclassification_id,
                           ws.subclassification_code,
                           ws.subclassification_name,
                           ws.organization_id
                      FROM waste.waste_classification wc
                      JOIN waste.waste_subclassification ws
                        ON ws.waste_classification_id = wc.waste_classification_id
                      LEFT JOIN plant_org po ON TRUE
                     WHERE wc.is_active = TRUE
                       AND ws.is_active = TRUE
                       AND (ws.organization_id IS NULL OR ws.organization_id = po.organization_id)
                     ORDER BY wc.classification_code,
                              ws.subclassification_code,
                              (ws.organization_id IS NOT NULL) DESC,
                              ws.waste_subclassification_id DESC
                )
                SELECT waste_classification_id, classification_code, classification_name,
                       waste_subclassification_id, subclassification_code, subclassification_name
                  FROM scoped
                 ORDER BY classification_name, subclassification_name, waste_subclassification_id
                """;
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setLong(1,plantId);
            try(ResultSet r=p.executeQuery()){
                List<ManualWasteCatalogEntryView> out=new ArrayList<>();
                while(r.next()){
                    out.add(new ManualWasteCatalogEntryView(
                            r.getLong("waste_classification_id"),
                            r.getString("classification_code"),
                            r.getString("classification_name"),
                            r.getLong("waste_subclassification_id"),
                            r.getString("subclassification_code"),
                            r.getString("subclassification_name"),
                            true));
                }
                return List.copyOf(out);
            }
        }catch(SQLException e){throw new IllegalStateException("Unable to load canonical waste compendium",e);}
    }

    @Override
    public boolean wasteSubclassificationExists(long plantId,long classificationId,long subclassificationId){
        String q="SELECT EXISTS(SELECT 1 FROM waste.waste_subclassification ws WHERE ws.waste_classification_id=? AND ws.waste_subclassification_id=?)";
        try(Connection x=ds.getConnection();PreparedStatement p=x.prepareStatement(q)){
            p.setLong(1,classificationId);p.setLong(2,subclassificationId);
            try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}
        }catch(SQLException e){throw new IllegalStateException("Unable to validate historical canonical waste subclassification",e);}
    }

    public boolean activeMetricDefinition(long id){return exists("SELECT EXISTS(SELECT 1 FROM kpi.metric_definition WHERE metric_definition_id=? AND is_active=TRUE)",id);}
    public boolean activeUom(long id){return exists("SELECT EXISTS(SELECT 1 FROM core.unit_of_measure WHERE uom_id=? AND is_active=TRUE)",id);}
    private boolean exists(String q,long id){try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(q)){p.setLong(1,id);try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}}catch(SQLException e){throw new IllegalStateException("Unable to validate canonical reference",e);}}
    public String source(){return "CANONICAL_REFERENCE_CATALOGS";}
}
