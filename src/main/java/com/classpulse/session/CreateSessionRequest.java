package com.classpulse.session;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateSessionRequest {

    private UUID scheduleId;
}
