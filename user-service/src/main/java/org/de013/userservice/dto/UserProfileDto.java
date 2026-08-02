package org.de013.userservice.dto;

import lombok.Builder;

@Builder
public record UserProfileDto(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        String address
) {}
