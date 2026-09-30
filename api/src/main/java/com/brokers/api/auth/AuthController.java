package com.brokers.api.auth;

import com.brokers.api.auth.AuthDtos.ClientInfo;
import com.brokers.api.auth.AuthDtos.LoginRequest;
import com.brokers.api.auth.AuthDtos.MeResponse;
import com.brokers.api.auth.AuthDtos.SessionResponse;
import com.brokers.api.auth.AuthDtos.SignupRequest;
import com.brokers.api.security.AuthenticatedMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
        return authService.signup(request, clientInfo(http));
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, clientInfo(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedMember member) {
        authService.logout(member.sessionId());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedMember member) {
        return authService.me(member);
    }

    private static ClientInfo clientInfo(HttpServletRequest http) {
        return new ClientInfo(http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
    }
}
