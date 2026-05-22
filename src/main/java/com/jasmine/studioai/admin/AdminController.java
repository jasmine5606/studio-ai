package com.jasmine.studioai.admin;

import com.jasmine.studioai.audit.AuditService;
import com.jasmine.studioai.auth.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "管理后台", description = "系统管理与监控接口")
public class AdminController {

    private final AdminService adminService;
    private final AuditService auditService;

    @Operation(summary = "系统仪表盘数据")
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        auditService.log(UserContext.userId(), "ADMIN_DASHBOARD", "system", "Dashboard viewed");
        return adminService.dashboard();
    }

    @Operation(summary = "用量统计")
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return adminService.usageStats();
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "timestamp", java.time.Instant.now().toString()
        );
    }
}
