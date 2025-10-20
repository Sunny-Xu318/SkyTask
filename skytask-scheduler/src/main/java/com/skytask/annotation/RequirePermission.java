package com.skytask.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * 用于标注需要权限验证的方法
 *
 * 使用示例:
 * @RequirePermission("task:write")
 * @RequirePermission("task:delete")
 * @RequirePermission("task:admin")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 所需权限标识
     * 格式: resource:action
     * 例如: task:write, task:read, node:manage
     */
    String value();

    /**
     * 权限描述 (可选,用于文档生成)
     */
    String description() default "";
}
