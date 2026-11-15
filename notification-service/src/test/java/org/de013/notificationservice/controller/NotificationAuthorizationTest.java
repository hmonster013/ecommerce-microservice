package org.de013.notificationservice.controller;

import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.security.HeaderAuthenticationFilter;
import org.de013.notificationservice.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationAuthorizationTest {
    private final NotificationService service = mock(NotificationService.class);
    private final NotificationController controller = new NotificationController(service);

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    private void headers(String id, String roles) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", id);
        request.addHeader("X-User-Username", "user");
        if (roles != null) request.addHeader("X-User-Roles", roles);
        new HeaderAuthenticationFilter().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void missingContextFailsClosed() {
        assertEquals(HttpStatus.FORBIDDEN, controller.getNotification(1L).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, controller.getMyNotifications(0, 20).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, controller.getMyUnreadCount().getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.getUserNotifications("a", 0, 20).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.getUnreadCount("a").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.markAsRead(1L).getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void getByIdRequiresOwnerOrPrivilegedRoleAndHandlesNullFields() throws Exception {
        Notification notification = Notification.builder().userId("owner").channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING).build();
        when(service.findById(1L)).thenReturn(Optional.of(notification));
        headers("other", null);
        assertEquals(HttpStatus.FORBIDDEN, controller.getNotification(1L).getStatusCode());
        notification.setUserId(null);
        assertEquals(HttpStatus.FORBIDDEN, controller.getNotification(1L).getStatusCode());
        notification.setUserId("owner");
        SecurityContextHolder.clearContext();
        headers("owner", null);
        assertEquals(HttpStatus.OK, controller.getNotification(1L).getStatusCode());
        SecurityContextHolder.clearContext();
        headers("admin", "ROLE_ADMIN");
        notification.setUserId(null);
        assertEquals(HttpStatus.OK, controller.getNotification(1L).getStatusCode());
        SecurityContextHolder.clearContext();
        headers("support", "ROLE_SUPPORT");
        assertEquals(HttpStatus.OK, controller.getNotification(1L).getStatusCode());
    }

    @Test
    void ownerAndPrivilegedRolesOnly() throws Exception {
        headers("a", null);
        when(service.findByUserId(eq("a"), any())).thenReturn(Page.empty());
        assertEquals(HttpStatus.OK, controller.getMyNotifications(0, 20).getStatusCode());
        assertEquals(HttpStatus.OK, controller.getMyUnreadCount().getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.getUserNotifications("b", 0, 20).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.getUnreadCount("b").getStatusCode());
        Notification notification = Notification.builder().userId("b").build();
        when(service.findById(1L)).thenReturn(Optional.of(notification));
        assertEquals(HttpStatus.FORBIDDEN, controller.markAsRead(1L).getStatusCode());
        notification.setUserId(null);
        assertEquals(HttpStatus.FORBIDDEN, controller.markAsRead(1L).getStatusCode());
        notification.setUserId("a");
        assertEquals(HttpStatus.OK, controller.markAsRead(1L).getStatusCode());
        SecurityContextHolder.clearContext();
        headers("admin", "ROLE_USER,ROLE_ADMIN");
        assertEquals(HttpStatus.OK, controller.getUnreadCount("b").getStatusCode());
        assertEquals(HttpStatus.OK, controller.markAsRead(1L).getStatusCode());
        SecurityContextHolder.clearContext();
        headers("support", "SUPPORT");
        assertEquals(HttpStatus.OK, controller.getUnreadCount("b").getStatusCode());
        notification.setUserId(null);
        assertEquals(HttpStatus.OK, controller.markAsRead(1L).getStatusCode());
    }
}
