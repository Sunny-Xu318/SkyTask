package com.skytask.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertRuleRequest {
    private String id;

    @NotBlank(message = "规则名称不能为空")
    private String name;

    @NotBlank(message = "监控指标不能为空")
    private String metric;

    @NotNull(message = "阈值不能为空")
    private Double threshold;

    private List<String> channels;
    private List<String> subscribers;
    private boolean enabled = true;
}
