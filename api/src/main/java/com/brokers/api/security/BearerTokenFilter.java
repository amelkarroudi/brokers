package com.brokers.api.security;

import com.brokers.api.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <token>}. Requests without a valid
 * token continue unauthenticated and are rejected later by the authorization rules.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final AuthService authService;

    public BearerTokenFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        authService.authenticate(header.substring(BEARER.length()).trim()).ifPresent(member -> {
            var authentication = new UsernamePasswordAuthenticationToken(
                    member, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });
        chain.doFilter(request, response);
    }
}
