package com.pki.ca.config;

import com.pki.ca.security.JwtAuthenticationFilter;
import com.pki.ca.security.RestAccessDeniedHandler;
import com.pki.ca.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Value("${app.security.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with work factor of 12 as mandated by SR-002
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // Public authentication endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/register").permitAll()
                        // Public health & actuator probes
                        .requestMatchers("/api/v1/system/health", "/actuator/health").permitAll()
                        // OpenAPI / Swagger Documentation
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        // Public CA certificate and chain downloads
                        .requestMatchers(HttpMethod.GET, "/api/v1/ca/certificate", "/api/v1/ca/chain").permitAll()
                        // Public Certificate download
                        .requestMatchers(HttpMethod.GET, "/api/v1/certificates/*/download").permitAll()
                        // Pre-flight CORS OPTIONS requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Protected admin user management
                        .requestMatchers("/api/v1/admin/**").hasRole("CA_ADMIN")
                        // Protected CA administration (key init and certificate import)
                        .requestMatchers(HttpMethod.POST, "/api/v1/ca/init", "/api/v1/ca/import-cert").hasRole("CA_ADMIN")
                        // Protected CA operational metadata
                        .requestMatchers(HttpMethod.GET, "/api/v1/ca").authenticated()
                        // Protected certificate issuance
                        .requestMatchers(HttpMethod.POST, "/api/v1/ca/requests/*/issue").hasAnyRole("RA_OPERATOR", "CA_ADMIN")
                        // Protected RA review and approval operations
                        .requestMatchers("/api/v1/ra/**").hasAnyRole("RA_OPERATOR", "CA_ADMIN", "SECURITY_OFFICER")
                        // Protected certificate enrollment requests
                        .requestMatchers("/api/v1/enrollment/**").hasAnyRole("END_ENTITY", "RA_OPERATOR", "CA_ADMIN", "SECURITY_OFFICER", "AUDITOR")
                        // Certificate profiles
                        .requestMatchers("/api/v1/profiles/**").authenticated()
                        // Certificate querying
                        .requestMatchers(HttpMethod.GET, "/api/v1/certificates/my").hasRole("END_ENTITY")
                        .requestMatchers(HttpMethod.GET, "/api/v1/certificates").hasAnyRole("RA_OPERATOR", "CA_ADMIN", "SECURITY_OFFICER", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/certificates/*").hasAnyRole("END_ENTITY", "RA_OPERATOR", "CA_ADMIN", "SECURITY_OFFICER", "AUDITOR")
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        for (String origin : origins) {
            if ("*".equals(origin.trim())) {
                throw new IllegalStateException("CORS misconfiguration: Wildcard origin '*' cannot be combined with allowCredentials=true.");
            }
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
