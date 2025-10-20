package com.skytask.service.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskQuery {
    String keyword;
    String status;
    String owner;
    List<String> tags;
    int page;
    int size;
}
