package com.example.bankcards.testutil;


import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class SecurityTestUtils {
    private SecurityTestUtils() {}
    public static RequestPostProcessor user()  { return SecurityMockMvcRequestPostProcessors.user("user").roles("USER"); }
    public static RequestPostProcessor admin() { return SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"); }
}
