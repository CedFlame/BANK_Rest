package com.example.bankcards.dto;

import com.example.bankcards.entity.enums.Role;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String username;
    private LocalDateTime createdAt;
    private Set<Role> roles;
}