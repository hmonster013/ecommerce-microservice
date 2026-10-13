package org.de013.keycloak.profilesync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileSyncEventListenerProviderTest {

    private static final String TARGET_REALM = "ecommerce-realm";

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakContext context;

    @Mock
    private UserProvider userProvider;

    @Mock
    private RealmProvider realmProvider;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private UserProfileProvisioningClient provisioningClient;

    private ProfileSyncConfiguration configuration;
    private ProfileSyncEventListenerProvider provider;

    @Test
    void updateNotifiesOnlyAfterCommit() {
        KeycloakTransactionManager manager = mock(KeycloakTransactionManager.class);
        when(session.getTransactionManager()).thenReturn(manager);
        Event event = new Event();
        event.setType(EventType.UPDATE_PROFILE);
        event.setRealmId(TARGET_REALM);
        event.setUserId("subject");
        provider.onEvent(event);
        verifyNoInteractions(provisioningClient);
        ArgumentCaptor<KeycloakTransaction> callback = ArgumentCaptor.forClass(KeycloakTransaction.class);
        verify(manager).enlistAfterCompletion(callback.capture());
        callback.getValue().begin();
        callback.getValue().commit();
        verify(provisioningClient).syncUserProfile("subject");
    }

    @Test
    void rolledBackUpdateDoesNotNotify() {
        KeycloakTransactionManager manager = mock(KeycloakTransactionManager.class);
        when(session.getTransactionManager()).thenReturn(manager);
        Event event = new Event();
        event.setType(EventType.UPDATE_EMAIL);
        event.setRealmId(TARGET_REALM);
        event.setUserId("subject");
        provider.onEvent(event);
        ArgumentCaptor<KeycloakTransaction> callback = ArgumentCaptor.forClass(KeycloakTransaction.class);
        verify(manager).enlistAfterCompletion(callback.capture());
        callback.getValue().begin();
        callback.getValue().rollback();
        verifyNoInteractions(provisioningClient);
    }

    @BeforeEach
    void setUp() {
        configuration = new ProfileSyncConfiguration(
                "http://user-service:8081/internal/keycloak/users",
                "test-secret",
                TARGET_REALM,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );

        provider = new ProfileSyncEventListenerProvider(session, configuration, provisioningClient);
    }

    @Test
    void onEvent_WhenRegisterInTargetRealm_ShouldProvisionUser() {
        Event event = new Event();
        event.setType(EventType.REGISTER);
        event.setRealmId(TARGET_REALM);
        event.setUserId("user-uuid-123");

        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "user-uuid-123")).thenReturn(user);

        when(user.getId()).thenReturn("user-uuid-123");
        when(user.getUsername()).thenReturn("john_doe");
        when(user.getEmail()).thenReturn("john@example.com");
        when(user.getFirstName()).thenReturn("John");
        when(user.getLastName()).thenReturn("Doe");
        when(provisioningClient.provisionUserProfile(any())).thenReturn(true);

        provider.onEvent(event);

        ArgumentCaptor<UserProfilePayload> captor = ArgumentCaptor.forClass(UserProfilePayload.class);
        verify(provisioningClient, times(1)).provisionUserProfile(captor.capture());

        UserProfilePayload payload = captor.getValue();
        assertEquals("user-uuid-123", payload.keycloakId());
        assertEquals("john_doe", payload.username());
        assertEquals("john@example.com", payload.email());
        assertEquals("John", payload.firstName());
        assertEquals("Doe", payload.lastName());
    }

    @Test
    void onEvent_WhenNonRegisterEvent_ShouldIgnore() {
        Event event = new Event();
        event.setType(EventType.LOGIN);
        event.setRealmId(TARGET_REALM);
        event.setUserId("user-uuid-123");

        provider.onEvent(event);

        verifyNoInteractions(provisioningClient);
    }

    @Test
    void onEvent_WhenDifferentRealm_ShouldIgnore() {
        Event event = new Event();
        event.setType(EventType.REGISTER);
        event.setRealmId("other-realm");
        event.setUserId("user-uuid-123");

        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(null);

        provider.onEvent(event);

        verifyNoInteractions(provisioningClient);
    }

    @Test
    void onEvent_WhenUserMissingEmail_ShouldSkipProvisioning() {
        Event event = new Event();
        event.setType(EventType.REGISTER);
        event.setRealmId(TARGET_REALM);
        event.setUserId("user-uuid-no-email");

        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "user-uuid-no-email")).thenReturn(user);

        when(user.getUsername()).thenReturn("user_no_email");
        when(user.getEmail()).thenReturn(null);

        provider.onEvent(event);

        verify(provisioningClient, never()).provisionUserProfile(any());
    }

    @Test
    void onEvent_AdminCreateUser_ShouldProvisionUser() {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setOperationType(OperationType.CREATE);
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setRealmId(TARGET_REALM);
        adminEvent.setResourcePath("users/admin-created-uuid");

        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "admin-created-uuid")).thenReturn(user);

        when(user.getId()).thenReturn("admin-created-uuid");
        when(user.getUsername()).thenReturn("admin_created");
        when(user.getEmail()).thenReturn("admin_created@example.com");
        when(user.getFirstName()).thenReturn("Admin");
        when(user.getLastName()).thenReturn("User");
        when(provisioningClient.provisionUserProfile(any())).thenReturn(true);

        provider.onEvent(adminEvent, false);

        ArgumentCaptor<UserProfilePayload> captor = ArgumentCaptor.forClass(UserProfilePayload.class);
        verify(provisioningClient, times(1)).provisionUserProfile(captor.capture());
        assertEquals("admin-created-uuid", captor.getValue().keycloakId());
    }

    @Test
    void onEvent_AdminNonCreateOrNonUserData_ShouldIgnore() {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setOperationType(OperationType.UPDATE);
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setRealmId(TARGET_REALM);

        provider.onEvent(adminEvent, false);

        verifyNoInteractions(provisioningClient);
    }
}
