package com.example.bankcards.security;

import com.example.bankcards.entity.User;
import com.example.bankcards.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String u = username == null ? null : username.trim();
        User user = userRepository.findByUsername(u)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + u));
        log.debug("Loaded user: {} with roles: {}", user.getUsername(), user.getRoles());
        return new CustomUserDetails(user);
    }
}
