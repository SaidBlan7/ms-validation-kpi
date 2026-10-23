package com.gbconnected.kpivalidation.functions;

import com.gbconnected.kpivalidation.application.*;
import com.gbconnected.kpivalidation.application.manual.scope.*;
import com.google.gson.*;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import java.util.*;

public class ManualKpiScopeFunctions {
    private static final String CORRELATION="X-Correlation-Id";

    @FunctionName("manual-kpi-scope-me")
    public HttpResponseMessage me(@HttpTrigger(name="req",methods={HttpMethod.GET},route="v1/manual-kpis/scope/me",authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req,ExecutionContext ctx){
        String c=correlation(req);try{return json(req,HttpStatus.OK,FunctionRuntime.RUNTIME.manualKpiScope().myScope(FunctionActorContextResolver.resolve(req)),c);}catch(SecurityException e){return reject(req,HttpStatus.UNAUTHORIZED,e,c);}catch(AuthorizationException e){return reject(req,HttpStatus.FORBIDDEN,e,c);}catch(Exception e){return reject(req,HttpStatus.BAD_REQUEST,e,c);}
    }

    @FunctionName("manual-kpi-scope-policy")
    public HttpResponseMessage policy(@HttpTrigger(name="req",methods={HttpMethod.GET},route="v1/manual-kpis/scope/policy",authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req,ExecutionContext ctx){return json(req,HttpStatus.OK,FunctionRuntime.RUNTIME.manualKpiScope().policy(),correlation(req));}

    @FunctionName("manual-kpi-scope-admin-list")
    public HttpResponseMessage list(@HttpTrigger(name="req",methods={HttpMethod.GET},route="v1/manual-kpis/scope/admin/assignments",authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req,ExecutionContext ctx){
        String c=correlation(req);try{return json(req,HttpStatus.OK,FunctionRuntime.RUNTIME.manualKpiScopeAdministration().list(FunctionActorContextResolver.resolve(req),requiredLong(req.getQueryParameters().get("plantId"),"plantId")),c);}catch(SecurityException e){return reject(req,HttpStatus.UNAUTHORIZED,e,c);}catch(AuthorizationException e){return reject(req,HttpStatus.FORBIDDEN,e,c);}catch(Exception e){return reject(req,HttpStatus.BAD_REQUEST,e,c);}
    }

    @FunctionName("manual-kpi-scope-admin-assign")
    public HttpResponseMessage assign(@HttpTrigger(name="req",methods={HttpMethod.POST},route="v1/manual-kpis/scope/admin/assignments",authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req,ExecutionContext ctx){return mutate(req,true);}

    @FunctionName("manual-kpi-scope-admin-revoke")
    public HttpResponseMessage revoke(@HttpTrigger(name="req",methods={HttpMethod.DELETE},route="v1/manual-kpis/scope/admin/assignments",authLevel=AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> req,ExecutionContext ctx){return mutate(req,false);}

    private HttpResponseMessage mutate(HttpRequestMessage<Optional<String>> req,boolean assign){
        String c=correlation(req);try{
            ActorContext actor=FunctionActorContextResolver.resolve(req);JsonObject b=JsonParser.parseString(req.getBody().orElseThrow(()->new IllegalArgumentException("JSON body is required"))).getAsJsonObject();
            long target=b.get("targetUserId").getAsLong(),plant=b.get("plantId").getAsLong();String reason=b.has("reason")&&!b.get("reason").isJsonNull()?b.get("reason").getAsString():null;
            Object out=assign?FunctionRuntime.RUNTIME.manualKpiScopeAdministration().assign(actor,new AssignManualKpiPlantCommand(target,plant,reason),c):FunctionRuntime.RUNTIME.manualKpiScopeAdministration().revoke(actor,new RevokeManualKpiPlantCommand(target,plant,reason),c);
            return json(req,HttpStatus.OK,out,c);
        }catch(SecurityException e){return reject(req,HttpStatus.UNAUTHORIZED,e,c);}catch(AuthorizationException e){return reject(req,HttpStatus.FORBIDDEN,e,c);}catch(ManualKpiScopeConflictException e){return reject(req,HttpStatus.CONFLICT,e,c);}catch(Exception e){return reject(req,HttpStatus.BAD_REQUEST,e,c);}
    }
    private static HttpResponseMessage json(HttpRequestMessage<?> r,HttpStatus s,Object b,String c){return r.createResponseBuilder(s).header(CORRELATION,c).header("Content-Type","application/json").body(JsonCodec.GSON.toJson(b)).build();}
    private static HttpResponseMessage reject(HttpRequestMessage<?> r,HttpStatus s,Exception e,String c){return r.createResponseBuilder(s).header(CORRELATION,c).body(JsonCodec.GSON.toJson(Map.of("error",e.getMessage()==null?"Request rejected":e.getMessage()))).build();}
    private static String correlation(HttpRequestMessage<?> req){return req.getHeaders().entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(CORRELATION)).map(Map.Entry::getValue).findFirst().filter(v->!v.isBlank()).orElse(UUID.randomUUID().toString());}
    private static long requiredLong(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" is required");try{return Long.parseLong(v);}catch(NumberFormatException e){throw new IllegalArgumentException(n+" must be numeric");}}
}
