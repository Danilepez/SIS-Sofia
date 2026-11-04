package bo.sofia.sis.soporte;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean UserDetailsService users(@Value("${sofia.operator-password}") String op,@Value("${sofia.quality-password}") String qa,@Value("${sofia.reader-password}") String ro,@Value("${sofia.supervisor-password}") String sv) {
  var encoder=new BCryptPasswordEncoder();
  if(op.length()<12 || qa.length()<12 || ro.length()<12 || sv.length()<12) throw new IllegalArgumentException("Claves de al menos 12 caracteres requeridas");
  return new InMemoryUserDetailsManager(
   User.withUsername("operador").password("{bcrypt}"+encoder.encode(op)).roles("OPERADOR","LECTOR").build(),
   User.withUsername("calidad").password("{bcrypt}"+encoder.encode(qa)).roles("CALIDAD","LECTOR").build(),
   User.withUsername("supervisor").password("{bcrypt}"+encoder.encode(sv)).roles("SUPERVISOR","LECTOR").build(),
   User.withUsername("consulta").password("{bcrypt}"+encoder.encode(ro)).roles("LECTOR").build());
 }
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
   .httpBasic(b->b.authenticationEntryPoint((req,res,e)->res.sendError(401,"Credenciales incorrectas")))
   .addFilterBefore(new OncePerRequestFilter() {
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
     if(!req.getMethod().equals("GET") && !"SIS-SOFIA".equals(req.getHeader("X-Requested-With"))) { res.sendError(403,"Cabecera de origen requerida"); return; }
     chain.doFilter(req,res);
    }
   },BasicAuthenticationFilter.class).build();
 }
}
