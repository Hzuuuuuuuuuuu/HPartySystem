package com.hparty.system.aspect;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hparty.framework.annotation.OperLog;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.entity.SysOperLog;
import com.hparty.system.mapper.SysOperLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面。
 *
 * <p>记录成功与异常两种结果 —— 越权尝试、规则校验失败这类"没办成"的操作
 * 恰恰是最需要留痕的，只记成功会让日志失去价值。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    /** 参数与结果的截断长度，避免超长 JSON 撑爆日志表 */
    private static final int MAX_TEXT_LENGTH = 2000;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint point, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable thrown = null;
        try {
            result = point.proceed();
            return result;
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            try {
                saveLog(point, operLog, result, thrown, System.currentTimeMillis() - start);
            } catch (Exception e) {
                // 记日志失败绝不能影响业务
                log.warn("记录操作日志失败", e);
            }
        }
    }

    private void saveLog(ProceedingJoinPoint point, OperLog operLog,
                         Object result, Throwable thrown, long costTime) {
        SysOperLog entity = new SysOperLog();
        entity.setTitle(operLog.title());
        entity.setBusinessType(operLog.businessType().getCode());

        MethodSignature signature = (MethodSignature) point.getSignature();
        entity.setMethod(signature.getDeclaringTypeName() + "." + signature.getName() + "()");

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entity.setRequestMethod(request.getMethod());
            entity.setOperUrl(StrUtil.sub(request.getRequestURI(), 0, 255));
            entity.setOperIp(clientIp(request));
        }

        LoginUser user = SecurityUtils.getLoginUserOrNull();
        entity.setOperName(user == null ? "匿名" : user.getUsername());

        if (operLog.saveRequestData()) {
            entity.setOperParam(StrUtil.sub(toJson(sanitizeArgs(point.getArgs())), 0, MAX_TEXT_LENGTH));
        }
        if (operLog.saveResponseData() && result != null) {
            entity.setJsonResult(StrUtil.sub(toJson(result), 0, MAX_TEXT_LENGTH));
        }

        if (thrown != null) {
            entity.setStatus(0);
            String msg = thrown.getMessage();
            entity.setErrorMsg(StrUtil.sub(msg == null ? thrown.getClass().getName() : msg, 0, MAX_TEXT_LENGTH));
        } else {
            entity.setStatus(1);
        }

        entity.setCostTime(costTime);
        entity.setOperTime(LocalDateTime.now());

        operLogMapper.insert(entity);
    }

    /**
     * 剔除无法序列化的参数（文件流、Servlet 对象等），否则 ObjectMapper 会抛异常。
     */
    private Object[] sanitizeArgs(Object[] args) {
        if (args == null) {
            return new Object[0];
        }
        return Arrays.stream(args).map(a -> {
            if (a instanceof MultipartFile f) {
                return "[文件] " + f.getOriginalFilename();
            }
            if (a instanceof MultipartFile[] fs) {
                return "[文件×" + fs.length + "]";
            }
            if (a instanceof HttpServletRequest || a instanceof jakarta.servlet.http.HttpServletResponse) {
                return "[Servlet 对象]";
            }
            return a;
        }).toArray();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private String clientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                int idx = ip.indexOf(',');
                return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
            }
        }
        String ip = request.getRemoteAddr();
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }
}
