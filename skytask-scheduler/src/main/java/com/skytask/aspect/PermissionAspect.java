package com.skytask.aspect;

import com.skytask.annotation.RequirePermission;
import com.skytask.context.RequestContextAdapter;
import com.skytask.exception.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限校验切面
 * 拦截所有标注了 @RequirePermission 的方法
 */
@Aspect
@Component
@Order(10)  // 在租户拦截器之后执行
@RequiredArgsConstructor
@Slf4j
public class PermissionAspect {

    private final RequestContextAdapter requestContextAdapter;

    /**
     * 权限校验前置通知
     */
    @Before("@annotation(com.skytask.annotation.RequirePermission)")
    public void checkPermission(JoinPoint joinPoint) {
        // ⚠️ 开发环境：临时禁用权限检查，方便调试
        // TODO: 生产环境请删除这个return语句，启用完整的权限检查
        log.debug("Development mode: Permission check is disabled");
        return;
        
        /* 取消注释以启用权限检查：
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        if (annotation == null) {
            return;
        }

        String requiredPermission = annotation.value();
        String currentUser = requestContextAdapter.currentUser();

        log.debug("Checking permission [{}] for user [{}] on method [{}]",
                  requiredPermission, currentUser, method.getName());

        if (!requestContextAdapter.hasPermission(requiredPermission)) {
            log.warn("Access denied: user [{}] lacks permission [{}]",
                     currentUser, requiredPermission);
            throw new AccessDeniedException(
                String.format("权限不足，需要权限: %s", requiredPermission)
            );
        }

        log.debug("Permission check passed for user [{}]", currentUser);
        */
    }
}
