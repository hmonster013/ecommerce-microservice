package org.de013.keycloak.profilesync.reconciliation;

import org.de013.keycloak.profilesync.UserProfilePayload;
import org.de013.keycloak.profilesync.UserProfileProvisioningClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileReconcilerTest {

    @Mock
    private KeycloakAdminClient adminClient;

    @Mock
    private UserProfileProvisioningClient provisioningClient;

    private ProfileReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new ProfileReconciler(adminClient, provisioningClient);
    }

    @Test
    void reconcile_WhenUsersExist_ShouldProvisionAndReportSuccess() {
        when(adminClient.obtainAccessToken()).thenReturn("mock-token");

        List<Map<String, Object>> users = List.of(
                createUserMap("kc-1", "user1", "user1@example.com", "User", "One"),
                createUserMap("kc-2", "user2", "user2@example.com", "User", "Two")
        );
        when(adminClient.fetchUsers("mock-token", 0, 10)).thenReturn(users);
        when(provisioningClient.syncUserProfile(anyString())).thenReturn("updated");

        ReconciliationReport report = reconciler.reconcile(false, 10);

        assertEquals(2, report.totalKeycloakUsers());
        assertEquals(2, report.successCount());
        assertEquals(0, report.skippedNoEmailCount());
        assertEquals(0, report.failureCount());
        assertFalse(report.hasFailures());
        verify(provisioningClient, times(2)).syncUserProfile(anyString());
        assertEquals(2, report.updatedCount());
    }

    @Test
    void reconcile_WhenUserHasNoEmail_ShouldSkipProvisioning() {
        when(adminClient.obtainAccessToken()).thenReturn("mock-token");

        List<Map<String, Object>> users = List.of(
                createUserMap("kc-1", "user1", null, "User", "One")
        );
        when(adminClient.fetchUsers("mock-token", 0, 10)).thenReturn(users);

        ReconciliationReport report = reconciler.reconcile(false, 10);

        assertEquals(1, report.totalKeycloakUsers());
        assertEquals(0, report.successCount());
        assertEquals(1, report.skippedNoEmailCount());
        assertEquals(0, report.failureCount());
        verifyNoInteractions(provisioningClient);
    }

    @Test
    void reconcile_WhenDryRun_ShouldNotCallProvisioningClient() {
        when(adminClient.obtainAccessToken()).thenReturn("mock-token");

        List<Map<String, Object>> users = List.of(
                createUserMap("kc-1", "user1", "user1@example.com", "User", "One")
        );
        when(adminClient.fetchUsers("mock-token", 0, 10)).thenReturn(users);

        ReconciliationReport report = reconciler.reconcile(true, 10);

        assertEquals(1, report.totalKeycloakUsers());
        assertEquals(1, report.successCount());
        assertTrue(report.dryRun());
        verifyNoInteractions(provisioningClient);
    }

    @Test
    void reconcile_WhenPagination_ShouldFetchMultiplePages() {
        when(adminClient.obtainAccessToken()).thenReturn("mock-token");

        List<Map<String, Object>> page1 = List.of(
                createUserMap("kc-1", "user1", "user1@example.com", "User", "One"),
                createUserMap("kc-2", "user2", "user2@example.com", "User", "Two")
        );
        List<Map<String, Object>> page2 = List.of(
                createUserMap("kc-3", "user3", "user3@example.com", "User", "Three")
        );

        when(adminClient.fetchUsers("mock-token", 0, 2)).thenReturn(page1);
        when(adminClient.fetchUsers("mock-token", 2, 2)).thenReturn(page2);
        when(provisioningClient.syncUserProfile(anyString())).thenReturn("unchanged");

        ReconciliationReport report = reconciler.reconcile(false, 2);

        assertEquals(3, report.totalKeycloakUsers());
        assertEquals(3, report.successCount());
        verify(adminClient, times(1)).fetchUsers("mock-token", 0, 2);
        verify(adminClient, times(1)).fetchUsers("mock-token", 2, 2);
        verify(provisioningClient, times(3)).syncUserProfile(anyString());
    }

    @Test
    void reconcile_WhenProvisioningFails_ShouldRecordFailure() {
        when(adminClient.obtainAccessToken()).thenReturn("mock-token");

        List<Map<String, Object>> users = List.of(
                createUserMap("kc-1", "user1", "user1@example.com", "User", "One")
        );
        when(adminClient.fetchUsers("mock-token", 0, 10)).thenReturn(users);
        when(provisioningClient.syncUserProfile(anyString())).thenReturn("failure");

        ReconciliationReport report = reconciler.reconcile(false, 10);

        assertEquals(1, report.totalKeycloakUsers());
        assertEquals(0, report.successCount());
        assertEquals(1, report.failureCount());
        assertTrue(report.hasFailures());
    }

    private Map<String, Object> createUserMap(String id, String username, String email, String firstName, String lastName) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("username", username);
        map.put("email", email);
        map.put("firstName", firstName);
        map.put("lastName", lastName);
        return map;
    }
}
