package com.skytask.dto;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskStatusUpdateRequest {

    @NotNull
    private Boolean enabled;
}
