package org.de013.userservice.controller;

import org.de013.userservice.dto.UserUpdateDto;
import org.de013.userservice.exception.UserExceptionHandler;
import org.de013.userservice.security.HeaderAuthenticationProvider;
import org.de013.userservice.service.UserManagementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

class UserProfileContractTest {
    @Test
    void selectsTrustedSubjectAndIgnoresClientIdentityFields() throws Exception {
        UserManagementService service = mock(UserManagementService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserManagementController(service))
                .setControllerAdvice(new UserExceptionHandler()).build();
        mvc.perform(put("/users/profile").principal(HeaderAuthenticationProvider.createFromHeaders("trusted-subject", "old-username", "identity@mail.net"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"An\",\"lastName\":\"\",\"phone\":\"\",\"address\":\"\",\"email\":\"attacker@mail.net\",\"id\":999,\"keycloakId\":\"other\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<UserUpdateDto> dto = ArgumentCaptor.forClass(UserUpdateDto.class);
        verify(service).updateUserProfile(eq("trusted-subject"), dto.capture());
        assertEquals("An", dto.getValue().firstName());
        assertEquals("", dto.getValue().phone());
        assertEquals(4, UserUpdateDto.class.getRecordComponents().length);
    }

    @Test
    void rejectsMissingSubjectBeforeInvokingService() throws Exception {
        UserManagementService service = mock(UserManagementService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserManagementController(service))
                .setControllerAdvice(new UserExceptionHandler()).build();
        mvc.perform(put("/users/profile").contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"An\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
