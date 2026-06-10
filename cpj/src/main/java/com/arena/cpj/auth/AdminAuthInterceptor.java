package com.arena.cpj.auth;

import com.arena.cpj.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (UserContext.get().getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin access required");
        }
        return true;
    }
}
