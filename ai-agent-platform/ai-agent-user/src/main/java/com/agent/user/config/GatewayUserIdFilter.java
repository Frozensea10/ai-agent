package com.agent.user.config;

import com.agent.common.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 鉴权过滤器：直接从 Authorization 头解析 JWT 获取用户身份。
 *
 * <p>不再信任 X-User-Id 头，否则 user 服务端口一旦直接暴露，任意客户端可通过伪造该头冒充任意用户。
 * 网关已校验过 JWT，此处再次校验可防御直连 user 服务的越权访问。
 */
@Slf4j
@Component
public class GatewayUserIdFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);
            if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
                String token = authHeader.substring(BEARER_PREFIX.length());
                try {
                    if (JwtUtil.validateToken(token) && JwtUtil.isAccessToken(token)) {
                        Long userId = JwtUtil.getUserId(token);
                        List<SimpleGrantedAuthority> authorities =
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, authorities);
                        authentication.setDetails(userId);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                    // token 无效不立即抛出，留给后续 Security 链处理；仅记录调试日志
                    log.debug("JWT 解析失败: {}", e.getMessage());
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
