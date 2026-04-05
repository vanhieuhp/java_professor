package dev.hieunv.price_radar.config;

import dev.hieunv.price_radar.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimiterService rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String product = request.getParameter("product");
        if (product != null && !rateLimiter.isAllowed(product)) {
            response.setStatus(429);
            response.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
            return false;  // false = stop request, don't reach the controller
        }
        return true;  // true = continue to controller
    }

}
