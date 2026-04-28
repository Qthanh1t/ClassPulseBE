package com.classpulse.classroom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClassroomRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    private String description;

    @Size(max = 100)
    private String subject;

    private Boolean isArchived;
}
