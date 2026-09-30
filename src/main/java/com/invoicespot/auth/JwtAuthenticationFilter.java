package com.invoicespot.auth;

import com.invoicespot.auth.session.CurrentSession;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.auth.session.UserSession;
import com.invoicespot.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null
                && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, header.substring(BEARER_PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    // The token's session must still be open and belong to its user, so a
    // session that is signed out stops its access tokens at once.
    private void authenticate(HttpServletRequest request, String token) {
        Optional<JwtService.TokenClaims> claims = jwtService.readAccessClaims(token);
        Optional<UserSession> session = claims.flatMap(found -> sessionService
                .active(found.sessionId())
                .filter(open -> open.getUser().getId().equals(found.subject())
                        && open.getUser().acceptsTokenIssuedAt(found.issuedAt())));
        if (session.isEmpty()) {
            return;
        }
        User user = session.get().getUser();
        List<SimpleGrantedAuthority> authorities =
                user.getRoles().stream().map(SimpleGrantedAuthority::new).toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(
                new CurrentSession.Details(session.get().getId(), session.get().getBusinessPkid()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
