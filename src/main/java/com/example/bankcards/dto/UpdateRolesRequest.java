package com.example.bankcards.dto;

import com.example.bankcards.entity.enums.Role;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdateRolesRequest {
    @NotEmpty
    private Set<Role> roles;
}