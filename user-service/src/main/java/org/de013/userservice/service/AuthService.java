package org.de013.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.userservice.dto.AuthResponse;
import org.de013.userservice.dto.LoginRequest;
import org.de013.userservice.dto.UserRegistrationRequest;
import org.de013.userservice.dto.UserResponse;
import org.de013.userservice.entity.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    
    public AuthResponse register(UserRegistrationRequest request) {
        log.info("Processing registration for user: {}", request.getUsername());
        
        UserResponse userResponse = userService.registerUser(request);
        
        // Load user for JWT generation
        User user = (User) userService.loadUserByUsername(request.getUsername());
        String jwtToken = jwtService.generateToken(user);
        
        return AuthResponse.builder()
                .token(jwtToken)
                .user(userResponse)
                .build();
    }
    
    public AuthResponse authenticate(LoginRequest request) {
        log.info("Processing authentication for user: {}", request.getUsername());
        
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );
        
        User user = (User) authentication.getPrincipal();
        String jwtToken = jwtService.generateToken(user);
        
        UserResponse userResponse = userService.getUserByUsername(user.getUsername());
        
        return AuthResponse.builder()
                .token(jwtToken)
                .user(userResponse)
                .build();
    }
}
