package com.invoicespot.business;

import com.invoicespot.auth.session.CurrentSession;
import com.invoicespot.common.ApiException;
import com.invoicespot.user.User;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

// Gives a controller the business the request's session is working in.
@Component
public class ActiveBusinessResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-Business-Id";

    private final ActiveBusinesses activeBusinesses;

    public ActiveBusinessResolver(ActiveBusinesses activeBusinesses) {
        this.activeBusinesses = activeBusinesses;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return BusinessAccess.class.equals(parameter.getParameterType());
    }

    @Override
    public BusinessAccess resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "You are not logged in");
        }
        BusinessAccess access = activeBusinesses
                .resolve(user, CurrentSession.id().orElse(null), CurrentSession.businessPkid().orElse(null))
                .orElseThrow(BusinessChangedException::none);
        // A tab still showing another business must not write into this one.
        String shown = webRequest.getHeader(HEADER);
        if (shown != null && !shown.isBlank() && !shown.trim().equalsIgnoreCase(access.businessId().toString())) {
            throw BusinessChangedException.to(access.businessId());
        }
        Requires requires = parameter.getParameterAnnotation(Requires.class);
        if (requires == null) {
            throw new IllegalStateException(parameter.getExecutable() + " doesn't say which permission it needs");
        }
        access.require(requires.value());
        return access;
    }
}
