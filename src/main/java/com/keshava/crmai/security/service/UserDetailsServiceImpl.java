package com.keshava.crmai.security.service;

import com.keshava.crmai.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * username here is the userId UUID string (JWT sub claim).
     * Loads org-specific User from the current tenant DB.
     */
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        try {
            return userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Invalid user id format: " + userId);
        }
    }
}
