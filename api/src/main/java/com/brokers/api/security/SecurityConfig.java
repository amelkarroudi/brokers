package com.brokers.api.security;

import com.brokers.api.auth.AuthService;
import com.brokers.api.common.GlobalExceptionHandler;
import com.brokers.api.config.BrokersProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Stateless token security. CSRF protection is disabled because authentication travels in the
 * Authorization header, never in cookies, so browsers cannot attach it cross-site.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/signup",
            "/api/auth/login",
            "/api/webhooks/**",
            "/sandbox/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health/**",
            "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService authService, JsonMapper jsonMapper) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> writeProblem(response, jsonMapper,
                                HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication is required"))
                        .accessDeniedHandler((request, response, e) -> writeProblem(response, jsonMapper,
                                HttpStatus.FORBIDDEN, "forbidden", "Access denied")))
                .addFilterBefore(new BearerTokenFilter(authService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Password logins go through AuthService; this only stops Spring Boot creating a default user. */
    @Bean
    UserDetailsService noUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Form login is not supported");
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(BrokersProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.security().corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private static void writeProblem(HttpServletResponse response, JsonMapper jsonMapper,
                                     HttpStatus status, String code, String detail) throws IOException {
        ProblemDetail problem = GlobalExceptionHandler.problem(status, code, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
