package com.gbconnected.kpivalidation.appservice.security;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
@Configuration public class SecurityConfiguration {
  @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,@Value("${gbc.security.enabled:false}") boolean enabled)throws Exception{
    http.csrf(c->c.disable()); if(!enabled)return http.authorizeHttpRequests(a->a.anyRequest().permitAll()).build();
    http.authorizeHttpRequests(a->a.requestMatchers("/actuator/health/**","/actuator/info","/api/v1/health","/api/health").permitAll().requestMatchers("/api/**").authenticated().anyRequest().denyAll());
    http.oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter())));http.httpBasic(h->h.disable());http.formLogin(f->f.disable());return http.build();
  }
  private JwtAuthenticationConverter converter(){JwtGrantedAuthoritiesConverter scopes=new JwtGrantedAuthoritiesConverter();JwtAuthenticationConverter c=new JwtAuthenticationConverter();c.setJwtGrantedAuthoritiesConverter(jwt->{List<GrantedAuthority>a=new ArrayList<>();Collection<GrantedAuthority>s=scopes.convert(jwt);if(s!=null)a.addAll(s);List<String>roles=jwt.getClaimAsStringList("roles");if(roles!=null)roles.forEach(r->a.add(new SimpleGrantedAuthority("APPROLE_"+r)));return a;});return c;}
}
