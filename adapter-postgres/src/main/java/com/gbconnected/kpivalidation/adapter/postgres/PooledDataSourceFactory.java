package com.gbconnected.kpivalidation.adapter.postgres;
import com.zaxxer.hikari.*;
import java.util.Map;
import javax.sql.DataSource;
public final class PooledDataSourceFactory {
  private PooledDataSourceFactory(){}
  public static HikariDataSource createPasswordDataSource(DatabaseSettings s){if(s.jdbcUrl()==null||s.username()==null||s.password()==null)throw new IllegalStateException("JDBC_URL, DB_USER and DB_PASSWORD are required for postgres-password mode");HikariConfig c=base(s);c.setJdbcUrl(s.jdbcUrl());c.setUsername(s.username());c.setPassword(s.password());return new HikariDataSource(c);}
  public static HikariDataSource createLakebaseOAuthDataSource(DatabaseSettings s,Map<String,String> env){DataSource ds=LakebaseOAuthDataSource.from(env);HikariConfig c=base(s);c.setDataSource(ds);c.setMaxLifetime(45L*60L*1000L);c.setIdleTimeout(15L*60L*1000L);return new HikariDataSource(c);}
  private static HikariConfig base(DatabaseSettings s){HikariConfig c=new HikariConfig();c.setPoolName("gbc2-kpi-validation-db");c.setMaximumPoolSize(s.maximumPoolSize());c.setMinimumIdle(s.minimumIdle());c.setConnectionTimeout(10000L);c.setValidationTimeout(5000L);return c;}
}
