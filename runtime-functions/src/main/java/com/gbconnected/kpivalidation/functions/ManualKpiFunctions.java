package com.gbconnected.kpivalidation.functions;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.manual.*;
import com.google.gson.*;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class ManualKpiFunctions {
    private static final String CORRELATION="X-Correlation-Id";

    @FunctionName("manual-kpi-policy")
    public HttpResponseMessage policy(@HttpTrigger(name="req", methods={HttpMethod.GET}, route="v1/manual-kpis/policy", authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req, ExecutionContext ctx){
        return json(req,HttpStatus.OK,FunctionRuntime.RUNTIME.manualKpis().policy(),correlation(req));
    }

    @FunctionName("manual-kpi-date-selection")
    public HttpResponseMessage dateSelection(@HttpTrigger(name="req", methods={HttpMethod.GET}, route="v1/manual-kpis/date-selection", authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req, ExecutionContext ctx){
        String corr=correlation(req);
        try {
            ActorContext actor=FunctionActorContextResolver.resolve(req);
            long plant=requiredLong(req.getQueryParameters().get("plantId"),"plantId");
            long line=requiredLong(req.getQueryParameters().get("lineId"),"lineId");
            LocalDate date=LocalDate.parse(required(req.getQueryParameters().get("businessDate"),"businessDate"));
            return json(req,HttpStatus.OK,FunctionRuntime.RUNTIME.manualKpis().dateSelection(actor,plant,line,date),corr);
        } catch(SecurityException e){return reject(req,HttpStatus.UNAUTHORIZED,e,corr);} catch(AuthorizationException e){return reject(req,HttpStatus.FORBIDDEN,e,corr);} catch(Exception e){return reject(req,HttpStatus.BAD_REQUEST,e,corr);}
    }

    @FunctionName("manual-kpi-stop-classifications")
    public HttpResponseMessage stopClassifications(@HttpTrigger(name="req", methods={HttpMethod.GET}, route="v1/manual-kpis/stop-classifications", authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req, ExecutionContext ctx){
        String corr=correlation(req);
        try {
            ActorContext actor=FunctionActorContextResolver.resolve(req);
            long plant=requiredLong(req.getQueryParameters().get("plantId"),"plantId");
            long line=requiredLong(req.getQueryParameters().get("lineId"),"lineId");
            return json(req,HttpStatus.OK,FunctionRuntime.RUNTIME.manualKpis().stopClassifications(actor,plant,line),corr);
        } catch(SecurityException e){return reject(req,HttpStatus.UNAUTHORIZED,e,corr);} catch(AuthorizationException e){return reject(req,HttpStatus.FORBIDDEN,e,corr);} catch(Exception e){return reject(req,HttpStatus.BAD_REQUEST,e,corr);}
    }

    @FunctionName("manual-kpi-waste-catalog")
    public HttpResponseMessage wasteCatalog(@HttpTrigger(name="req", methods={HttpMethod.GET}, route="v1/manual-kpis/waste-catalog", authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req, ExecutionContext ctx){
        String corr=correlation(req);
        try {
            ActorContext actor=FunctionActorContextResolver.resolve(req);
            long plant=requiredLong(req.getQueryParameters().get("plantId"),"plantId");
            long line=requiredLong(req.getQueryParameters().get("lineId"),"lineId");
            return json(req,HttpStatus.OK,FunctionRuntime.RUNTIME.manualKpis().wasteCatalog(actor,plant,line),corr);
        } catch(SecurityException e){return reject(req,HttpStatus.UNAUTHORIZED,e,corr);} catch(AuthorizationException e){return reject(req,HttpStatus.FORBIDDEN,e,corr);} catch(Exception e){return reject(req,HttpStatus.BAD_REQUEST,e,corr);}
    }

    @FunctionName("manual-kpi-get")
    public HttpResponseMessage get(@HttpTrigger(name="req", methods={HttpMethod.GET}, route="v1/manual-kpis", authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req, ExecutionContext ctx){
        String corr=correlation(req);
        try {
            ActorContext actor=FunctionActorContextResolver.resolve(req);
            long plant=requiredLong(req.getQueryParameters().get("plantId"),"plantId");
            long line=requiredLong(req.getQueryParameters().get("lineId"),"lineId");
            LocalDate date=LocalDate.parse(required(req.getQueryParameters().get("businessDate"),"businessDate"));
            return json(req,HttpStatus.OK,FunctionRuntime.RUNTIME.manualKpis().get(actor,plant,line,date),corr);
        } catch(SecurityException e){return reject(req,HttpStatus.UNAUTHORIZED,e,corr);} catch(AuthorizationException e){return reject(req,HttpStatus.FORBIDDEN,e,corr);} catch(ManualKpiNotFoundException e){return reject(req,HttpStatus.NOT_FOUND,e,corr);} catch(Exception e){return reject(req,HttpStatus.BAD_REQUEST,e,corr);}
    }

    @FunctionName("manual-kpi-save")
    public HttpResponseMessage save(@HttpTrigger(name="req", methods={HttpMethod.PUT}, route="v1/manual-kpis", authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req, ExecutionContext ctx){
        String corr=correlation(req);
        try {
            ActorContext actor=FunctionActorContextResolver.resolve(req);
            JsonObject b=JsonParser.parseString(req.getBody().orElseThrow(()->new IllegalArgumentException("JSON body is required"))).getAsJsonObject();
            SaveManualKpiCommand command=parse(b);
            return json(req,HttpStatus.OK,FunctionRuntime.RUNTIME.manualKpis().save(actor,command,corr),corr);
        } catch(SecurityException e){return reject(req,HttpStatus.UNAUTHORIZED,e,corr);} catch(AuthorizationException e){return reject(req,HttpStatus.FORBIDDEN,e,corr);} catch(ManualKpiConflictException e){return reject(req,HttpStatus.CONFLICT,e,corr);} catch(ManualKpiStorageUnavailableException e){return reject(req,HttpStatus.SERVICE_UNAVAILABLE,e,corr);} catch(Exception e){return reject(req,HttpStatus.BAD_REQUEST,e,corr);}
    }

    private static SaveManualKpiCommand parse(JsonObject b){
        long plant=b.get("plantId").getAsLong(), line=b.get("lineId").getAsLong(); LocalDate date=LocalDate.parse(b.get("businessDate").getAsString());
        Long expectedVersion=b.has("expectedVersion")&&!b.get("expectedVersion").isJsonNull()?b.get("expectedVersion").getAsLong():null;
        List<ManualStopTimeInput> stops=new ArrayList<>(); ManualMassPerformanceInput mass=null; List<ManualWasteInput> waste=new ArrayList<>(); BigDecimal tonnes=null;
        if(b.has("oee")&&!b.get("oee").isJsonNull()){
            JsonObject o=b.getAsJsonObject("oee");
            if(o.has("stopTimes")&&o.get("stopTimes").isJsonArray()) for(JsonElement e:o.getAsJsonArray("stopTimes")){JsonObject x=e.getAsJsonObject();stops.add(new ManualStopTimeInput(x.get("stopClassificationId").getAsLong(),x.get("hours").getAsBigDecimal()));}
            if(o.has("massPerformance")&&!o.get("massPerformance").isJsonNull()){JsonObject x=o.getAsJsonObject("massPerformance");BigDecimal elaborated=x.has("elaboratedMass")&&!x.get("elaboratedMass").isJsonNull()?x.get("elaboratedMass").getAsBigDecimal():null;BigDecimal delivered=x.has("deliveredMass")&&!x.get("deliveredMass").isJsonNull()?x.get("deliveredMass").getAsBigDecimal():null;mass=new ManualMassPerformanceInput(elaborated,delivered);}
        }
        if(b.has("waste")&&!b.get("waste").isJsonNull()){
            JsonObject w=b.getAsJsonObject("waste"); if(w.has("producedTonnes")&&!w.get("producedTonnes").isJsonNull()) tonnes=w.get("producedTonnes").getAsBigDecimal();
            if(w.has("entries")&&w.get("entries").isJsonArray()) for(JsonElement e:w.getAsJsonArray("entries")){JsonObject x=e.getAsJsonObject();if(!x.has("wasteSubclassificationId")||x.get("wasteSubclassificationId").isJsonNull()) throw new IllegalArgumentException("wasteSubclassificationId is required");waste.add(new ManualWasteInput(x.get("wasteClassificationId").getAsLong(),x.get("wasteSubclassificationId").getAsLong(),x.get("kilograms").getAsBigDecimal()));}
        }
        return new SaveManualKpiCommand(plant,line,date,expectedVersion,new ManualOeeSection(stops,mass),new ManualWasteSection(waste,tonnes));
    }
    private static HttpResponseMessage json(HttpRequestMessage<?> req,HttpStatus status,Object body,String corr){return req.createResponseBuilder(status).header(CORRELATION,corr).header("Content-Type","application/json").body(JsonCodec.GSON.toJson(body)).build();}
    private static HttpResponseMessage reject(HttpRequestMessage<?> req,HttpStatus status,Exception e,String corr){return req.createResponseBuilder(status).header(CORRELATION,corr).body(JsonCodec.GSON.toJson(Map.of("error",e.getMessage()==null?"Request rejected":e.getMessage()))).build();}
    private static String correlation(HttpRequestMessage<?> req){return req.getHeaders().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(CORRELATION)).map(Map.Entry::getValue).findFirst().filter(v->!v.isBlank()).orElse(UUID.randomUUID().toString());}
    private static String required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");return v;}
    private static long requiredLong(String v,String n){try{return Long.parseLong(required(v,n));}catch(NumberFormatException e){throw new IllegalArgumentException(n+" must be numeric");}}
}
