package com.dairyfarm.security;

import java.util.Map;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.dairyfarm.common.ApiException;
import com.dairyfarm.member.FarmMemberRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * For any route with a {farmId} path variable: the caller must be an active member of that farm.
 * Routes marked {@link OwnerOnly} also need the OWNER role.
 */
@Component
public class FarmAccessInterceptor implements HandlerInterceptor {

    private final FarmMemberRepository members;
    private final FarmContext context;

    public FarmAccessInterceptor(FarmMemberRepository members, FarmContext context) {
        this.members = members;
        this.context = context;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        @SuppressWarnings("unchecked")
        var vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String raw = vars == null ? null : vars.get("farmId");
        if (raw == null) {
            return true;
        }
        long farmId;
        try {
            farmId = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw ApiException.notFound("Farm not found.");
        }
        var member = members.findByFarmIdAndUserIdAndActiveTrue(farmId, CurrentUser.id())
                .orElseThrow(() -> ApiException.forbidden("You don't have access to this farm."));
        context.set(farmId, member.getId(), member.getRole());

        boolean ownerOnly = AnnotatedElementUtils.hasAnnotation(method.getMethod(), OwnerOnly.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), OwnerOnly.class);
        if (ownerOnly) {
            context.requireOwner();
        }
        return true;
    }
}
