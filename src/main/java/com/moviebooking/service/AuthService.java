package com.moviebooking.service;

import com.moviebooking.domain.Role;
import com.moviebooking.domain.User;
import com.moviebooking.dto.AuthDtos.RegisterRequest;
import com.moviebooking.dto.AuthDtos.UserResponse;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.NotFoundException;
import com.moviebooking.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Public registration always creates a CUSTOMER. Admins are bootstrapped from configuration. */
    @Transactional
    public UserResponse registerCustomer(RegisterRequest req) {
        return create(req.name(), req.email(), req.password(), Role.CUSTOMER);
    }

    @Transactional
    public UserResponse create(String name, String email, String rawPassword, Role role) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalized)) {
            throw new ConflictException("Email is already registered");
        }
        User user = userRepository.save(new User(name.trim(), normalized, passwordEncoder.encode(rawPassword), role));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return userRepository.findById(userId).map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}
