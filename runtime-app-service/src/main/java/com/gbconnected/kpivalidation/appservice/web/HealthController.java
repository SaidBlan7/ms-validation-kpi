package com.gbconnected.kpivalidation.appservice.web;
import com.gbconnected.kpivalidation.application.port.out.DatabaseHealthPort;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController public class HealthController {private final DatabaseHealthPort health;public HealthController(DatabaseHealthPort h){health=h;}@GetMapping({"/api/v1/health","/api/health"})ResponseEntity<Map<String,Object>> health(){boolean ok=health.healthy();return ResponseEntity.status(ok?HttpStatus.OK:HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status",ok?"UP":"DOWN","database",ok));}}
