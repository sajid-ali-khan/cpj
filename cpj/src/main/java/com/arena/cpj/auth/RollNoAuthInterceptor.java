package com.arena.cpj.auth;

import com.arena.cpj.user.User;
import com.arena.cpj.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RollNoAuthInterceptor implements HandlerInterceptor {

    public static final String ROLL_NO_HEADER = "X-Roll-No";
    public static final String ROLL_NO_PARAM = "rollNo";

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String headerRollNo = request.getHeader(ROLL_NO_HEADER);
        String token = (headerRollNo != null && !headerRollNo.isBlank())
                ? headerRollNo
                : request.getParameter(ROLL_NO_PARAM);
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing session token");
        }

        String resolvedToken = token.trim();
        User user = userRepository.findByActiveSessionToken(resolvedToken)
                .orElseThrow(() -> new UnauthorizedException("Session invalid or expired. Please log in again."));
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
