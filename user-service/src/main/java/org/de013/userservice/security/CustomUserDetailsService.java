package org.de013.userservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.userservice.entity.User;
import org.de013.userservice.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);
        
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> {
                    log.error("User not found with username or email: {}", username);
                    return new UsernameNotFoundException("User not found with username or email: " + username);
                });

        log.debug("User found: {} with {} roles", user.getUsername(), user.getRoles().size());
        return user;
    }

    /**
     * Load user by user ID
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        log.debug("Loading user by ID: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", id);
                    return new UsernameNotFoundException("User not found with ID: " + id);
                });

        log.debug("User found by ID: {} - {}", id, user.getUsername());
        return user;
    }
}
