package com.dairyfarm.security;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.dairyfarm.user.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Creates the app_user row the first time a Supabase user calls the API. */
@Component
public class UserProvisioningInterceptor implements HandlerInterceptor {

    private final UserService users;

    public UserProvisioningInterceptor(UserService users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        users.ensureUser(CurrentUser.jwt());
        return true;
    }
}
