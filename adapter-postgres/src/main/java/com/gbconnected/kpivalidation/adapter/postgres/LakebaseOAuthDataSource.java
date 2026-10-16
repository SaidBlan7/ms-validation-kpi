package com.gbconnected.kpivalidation.adapter.postgres;
import com.databricks.sdk.WorkspaceClient;
import com.databricks.sdk.core.DatabricksConfig;
import com.databricks.sdk.service.postgres.*;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Map;
import java.util.logging.Logger;
import javax.sql.DataSource;
public final class LakebaseOAuthDataSource implements DataSource {
  private final WorkspaceClient client; private final String endpointName,jdbcUrl,username; private volatile PrintWriter logWriter; private volatile int loginTimeout;
  private LakebaseOAuthDataSource(WorkspaceClient c,String e,String j,String u){client=c;endpointName=e;jdbcUrl=j;username=u;}
  public static LakebaseOAuthDataSource from(Map<String,String> env){String host=DatabaseSettings.required(env,"DATABRICKS_HOST"),cid=DatabaseSettings.required(env,"DATABRICKS_CLIENT_ID"),secret=DatabaseSettings.required(env,"DATABRICKS_CLIENT_SECRET"),endpoint=DatabaseSettings.required(env,"ENDPOINT_NAME"),pg=DatabaseSettings.required(env,"PGHOST"),db=DatabaseSettings.required(env,"PGDATABASE"),user=DatabaseSettings.required(env,"PGUSER"),port=env.getOrDefault("PGPORT","5432");WorkspaceClient c=new WorkspaceClient(new DatabricksConfig().setHost(host).setClientId(cid).setClientSecret(secret));String url="jdbc:postgresql://"+pg+":"+port+"/"+db+"?sslmode=require&connectTimeout=10&socketTimeout=30";return new LakebaseOAuthDataSource(c,endpoint,url,user);}
  public Connection getConnection() throws SQLException {try{DatabaseCredential credential=client.postgres().generateDatabaseCredential(new GenerateDatabaseCredentialRequest().setEndpoint(endpointName));return DriverManager.getConnection(jdbcUrl,username,credential.getToken());}catch(RuntimeException e){throw new SQLException("Unable to generate Lakebase database credential",e);}}
  public Connection getConnection(String u,String p){throw new UnsupportedOperationException("Explicit username/password is not supported for Lakebase OAuth");}
  public PrintWriter getLogWriter(){return logWriter;} public void setLogWriter(PrintWriter out){logWriter=out;DriverManager.setLogWriter(out);} public void setLoginTimeout(int s){loginTimeout=s;DriverManager.setLoginTimeout(s);} public int getLoginTimeout(){return loginTimeout;} public Logger getParentLogger() throws SQLFeatureNotSupportedException{return Logger.getLogger("com.gbconnected.kpivalidation.adapter.postgres");} public <T>T unwrap(Class<T> i)throws SQLException{if(i.isInstance(this))return i.cast(this);throw new SQLException("Not a wrapper for "+i.getName());} public boolean isWrapperFor(Class<?> i){return i.isInstance(this);}
}
