package com.gbconnected.kpivalidation.adapter.postgres.manual;
import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiReferencePort;
import java.sql.*; import java.util.*; import javax.sql.DataSource;
public final class JdbcManualKpiReferenceAdapter implements ManualKpiReferencePort {
    private final DataSource ds; public JdbcManualKpiReferenceAdapter(DataSource ds){this.ds=Objects.requireNonNull(ds);}
    public boolean activeStopClassification(long id){return exists("SELECT EXISTS(SELECT 1 FROM stops.stop_classification WHERE stop_classification_id=? AND is_active=TRUE)",id);}
    public boolean activeMetricDefinition(long id){return exists("SELECT EXISTS(SELECT 1 FROM kpi.metric_definition WHERE metric_definition_id=? AND is_active=TRUE)",id);}
    public boolean activeUom(long id){return exists("SELECT EXISTS(SELECT 1 FROM core.unit_of_measure WHERE uom_id=? AND is_active=TRUE)",id);}
    public boolean validWasteClassification(long c,Long s){
        if(s==null) return exists("SELECT EXISTS(SELECT 1 FROM waste.waste_classification WHERE waste_classification_id=? AND is_active=TRUE)",c);
        String q="SELECT EXISTS(SELECT 1 FROM waste.waste_classification wc JOIN waste.waste_subclassification ws ON ws.waste_classification_id=wc.waste_classification_id WHERE wc.waste_classification_id=? AND ws.waste_subclassification_id=? AND wc.is_active=TRUE AND ws.is_active=TRUE)";
        try(Connection x=ds.getConnection();PreparedStatement p=x.prepareStatement(q)){p.setLong(1,c);p.setLong(2,s);try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}}catch(SQLException e){throw new IllegalStateException("Unable to validate waste catalog",e);}
    }
    private boolean exists(String q,long id){try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(q)){p.setLong(1,id);try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}}catch(SQLException e){throw new IllegalStateException("Unable to validate canonical reference",e);}}
    public String source(){return "CANONICAL_REFERENCE_CATALOGS";}
}
