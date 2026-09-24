package com.moviebooking.dto;

import com.moviebooking.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record UserResponse(Long id, String name, String email, Role role) {
    }
}
