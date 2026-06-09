package com.arena.cpj.auth;

import com.arena.cpj.common.NotFoundException;
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
        String headerRollNo = request.getHeader(ROLL_NO_HEADER);
        String rollNo = (headerRollNo != null && !headerRollNo.isBlank())
                ? headerRollNo
                : request.getParameter(ROLL_NO_PARAM);
        if (rollNo == null || rollNo.isBlank()) {
            throw new UnauthorizedException("Missing roll number");
        }

        String resolvedRollNo = rollNo.trim();
        User user = userRepository.findByRollNo(resolvedRollNo)
                .orElseThrow(() -> new NotFoundException("User not found for roll number: " + resolvedRollNo));
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
