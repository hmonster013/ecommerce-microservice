package org.de013.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class UserContextHolderTest {

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void extractUserContextFromRequest_WhenHeadersPresent_ShouldReturnValidContext() {
        request.addHeader("X-User-Id", "user-uuid-123");
        request.addHeader("X-User-Username", "john_doe");
        request.addHeader("X-User-Email", "john@example.com");

        UserContext context = UserContextHolder.getCurrentUser();

        assertNotNull(context);
        assertEquals("user-uuid-123", context.getUserId());
        assertEquals("john_doe", context.getUsername());
        assertEquals("john@example.com", context.getEmail());
        assertTrue(context.isValid());
    }

    @Test
    void extractUserContextFromRequest_WhenHeadersMissing_ShouldReturnNull() {
        UserContext context = UserContextHolder.getCurrentUser();
        assertNull(context);
    }

    @Test
    void requireAuthenticated_WhenNotAuthenticated_ShouldThrowException() {
        assertThrows(SecurityException.class, UserContextHolder::requireAuthenticated);
    }

    @Test
    void requireAuthenticated_WhenAuthenticated_ShouldReturnContext() {
        request.addHeader("X-User-Id", "user-uuid-123");
        request.addHeader("X-User-Username", "john_doe");

        UserContext context = UserContextHolder.requireAuthenticated();

        assertNotNull(context);
        assertEquals("user-uuid-123", context.getUserId());
        assertEquals("john_doe", context.getUsername());
    }
}
