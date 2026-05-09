package com.classpulse.session;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class FocusStudentRequest {
    /** null = unfocus */
    private UUID studentId;
}
