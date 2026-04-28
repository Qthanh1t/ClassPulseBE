package com.classpulse.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUpdateUserRequest {

    private Boolean isActive;
    private Role role;
}
