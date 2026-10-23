package com.gbconnected.kpivalidation.adapter.postgres.manual;

import com.gbconnected.kpivalidation.application.port.out.manual.ManualKpiScopePort;
import com.gbconnected.kpivalidation.domain.manual.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

public final class JdbcManualKpiScopeAdapter implements ManualKpiScopePort {
    private final DataSource ds;
    public JdbcManualKpiScopeAdapter(DataSource ds){this.ds=Objects.requireNonNull(ds);}

    @Override
    public List<ManualKpiPlantScope> findEffectiveScope(UUID tenantId, UUID objectId, String featureCode) {
        String sql = """
            SELECT p.plant_id,p.plant_code,p.plant_name,p.timezone_name,
                   l.line_id,l.line_code,l.line_name
              FROM app_security.user_identity u
              JOIN app_security.user_plant_access upa ON upa.user_id=u.user_id
              JOIN core.plant p ON p.plant_id=upa.plant_id AND p.is_active=TRUE
              JOIN app_security.user_line_access ula ON ula.user_id=u.user_id
              JOIN core.line l ON l.line_id=ula.line_id AND l.is_active=TRUE
              JOIN core.line_plant_assignment lpa ON lpa.line_id=l.line_id AND lpa.plant_id=p.plant_id
             WHERE u.entra_tenant_id=? AND u.entra_object_id=? AND u.is_active=TRUE
               AND upa.valid_from<=CURRENT_TIMESTAMP AND (upa.valid_to IS NULL OR upa.valid_to>CURRENT_TIMESTAMP)
               AND ula.valid_from<=CURRENT_TIMESTAMP AND (ula.valid_to IS NULL OR ula.valid_to>CURRENT_TIMESTAMP)
               AND lpa.valid_from<=CURRENT_TIMESTAMP AND (lpa.valid_to IS NULL OR lpa.valid_to>CURRENT_TIMESTAMP)
               AND COALESCE((
                    SELECT ca.boolean_value
                      FROM configuration.configuration_definition cd
                      JOIN configuration.configuration_assignment ca ON ca.configuration_definition_id=cd.configuration_definition_id
                     WHERE cd.configuration_code=? AND cd.is_active=TRUE AND ca.boolean_value IS NOT NULL
                       AND ca.valid_from<=CURRENT_TIMESTAMP AND (ca.valid_to IS NULL OR ca.valid_to>CURRENT_TIMESTAMP)
                       AND (ca.line_id=l.line_id
                         OR (ca.line_id IS NULL AND ca.plant_id=p.plant_id)
                         OR (ca.organization_id IS NULL AND ca.plant_id IS NULL AND ca.line_id IS NULL
                             AND ca.product_id IS NULL AND ca.dough_id IS NULL AND ca.machine_id IS NULL))
                     ORDER BY CASE WHEN ca.line_id=l.line_id THEN 0 WHEN ca.plant_id=p.plant_id THEN 1 ELSE 2 END,
                              ca.valid_from DESC, ca.configuration_assignment_id DESC
                     LIMIT 1
               ), FALSE)=TRUE
             ORDER BY p.plant_name,p.plant_id,l.line_name,l.line_id
            """;
        try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            p.setObject(1,tenantId);p.setObject(2,objectId);p.setString(3,featureCode);
            try(ResultSet r=p.executeQuery()){
                Map<Long,PlantBuilder> plants=new LinkedHashMap<>();
                while(r.next()){
                    long plantId=r.getLong("plant_id");
                    PlantBuilder b=plants.computeIfAbsent(plantId,id->new PlantBuilder(id,get(r,"plant_code"),get(r,"plant_name"),get(r,"timezone_name")));
                    b.lines.add(new ManualKpiLineScope(r.getLong("line_id"),r.getString("line_code"),r.getString("line_name")));
                }
                List<ManualKpiPlantScope> out=new ArrayList<>();
                for(PlantBuilder b:plants.values()) out.add(new ManualKpiPlantScope(b.id,b.code,b.name,b.timezone,List.copyOf(b.lines)));
                return List.copyOf(out);
            }
        } catch(SQLException e){throw new IllegalStateException("Unable to resolve Manual KPI effective scope",e);}
    }
    @Override public String source(){return "app_security.user_plant_access+user_line_access+configuration.configuration_assignment";}
    private static String get(ResultSet r,String c){try{return r.getString(c);}catch(SQLException e){throw new IllegalStateException(e);}}
    private static final class PlantBuilder{final long id;final String code,name,timezone;final List<ManualKpiLineScope> lines=new ArrayList<>();PlantBuilder(long i,String c,String n,String t){id=i;code=c;name=n;timezone=t;}}
}
