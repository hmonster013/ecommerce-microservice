package org.de013.orderservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
public class FeignRequestInterceptor implements RequestInterceptor {

    private static final String[] HEADERS_TO_FORWARD = {
            "X-User-Id",
            "X-User-Username",
            "X-User-Email",
            "X-User-FirstName",
            "X-User-LastName",
            "X-User-Roles",
            "Authorization"
    };

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            for (String headerName : HEADERS_TO_FORWARD) {
                String headerValue = request.getHeader(headerName);
                if (headerValue != null) {
                    template.header(headerName, headerValue);
                }
            }
        } else {
            log.warn("RequestContextHolder is empty, cannot forward headers");
        }
    }
}
