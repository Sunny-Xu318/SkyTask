package com.skytask.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PageResponse<T> {
    List<T> records;
    long total;
    int page;
    int size;
}
