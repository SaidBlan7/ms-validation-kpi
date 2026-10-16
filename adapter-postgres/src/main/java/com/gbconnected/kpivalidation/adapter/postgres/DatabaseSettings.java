package com.gbconnected.kpivalidation.adapter.postgres;
import java.util.Map;
public record DatabaseSettings(String mode,String jdbcUrl,String username,String password,int maximumPoolSize,int minimumIdle) {
    public static DatabaseSettings from(Map<String,String> env){return new DatabaseSettings(env.getOrDefault("GBC_PERSISTENCE_MODE","memory"),env.get("JDBC_URL"),env.get("DB_USER"),env.get("DB_PASSWORD"),integer(env,"DB_POOL_MAX_SIZE",10),integer(env,"DB_POOL_MIN_IDLE",0));}
    private static int integer(Map<String,String> env,String key,int d){String v=env.get(key);return v==null||v.isBlank()?d:Integer.parseInt(v);}
    static String required(Map<String,String> env,String key){String v=env.get(key);if(v==null||v.isBlank())throw new IllegalStateException("Missing required environment variable: "+key);return v;}
}
