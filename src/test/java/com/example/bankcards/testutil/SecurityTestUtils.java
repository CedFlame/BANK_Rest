package com.example.bankcards.testutil;

import com.example.bankcards.entity.User;
import com.example.bankcards.entity.enums.Role;
import com.example.bankcards.security.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;

public final class SecurityTestUtils {
    private SecurityTestUtils() {}

    public static RequestPostProcessor user()  {
        return SecurityMockMvcRequestPostProcessors.user("user").roles("USER");
    }

    public static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN");
    }

    public static RequestPostProcessor customUser(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("user"+id+"@example.com");
        u.setPassword("{noop}pwd");
        u.setRoles(Set.of(Role.ROLE_USER));
        u.setAccountNonExpired(true);
        u.setAccountNonLocked(true);
        u.setCredentialsNonExpired(true);
        u.setEnabled(true);

        UserDetails cud = new CustomUserDetails(u);

        var auth = new UsernamePasswordAuthenticationToken(
                cud,
                cud.getPassword(),
                cud.getAuthorities()
        );
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
