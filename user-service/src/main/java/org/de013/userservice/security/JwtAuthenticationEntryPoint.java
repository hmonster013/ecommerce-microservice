package org.de013.userservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.JCode;
import org.de013.common.constant.MessageConstants;
import org.de013.common.dto.ApiResponse;
import org.de013.common.util.HttpUtils;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String clientIp = HttpUtils.getClientIpAddress(request);
        String userAgent = HttpUtils.getUserAgent(request);
        String requestUri = HttpUtils.getFullRequestUri(request);

        log.error("Unauthorized access attempt - IP: {}, User-Agent: {}, URI: {}, Error: {}",
                clientIp, userAgent, requestUri, authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<Object> apiResponse = ApiResponse.error(
                MessageConstants.UNAUTHORIZED,
                JCode.UNAUTHORIZED,
                HttpUtils.getFullRequestUri(request)
        );

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
