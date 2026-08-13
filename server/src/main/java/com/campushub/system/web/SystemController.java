package com.campushub.system.web;

import com.campushub.system.SystemModule;
import com.campushub.system.domain.SystemStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SystemController {

    private final SystemModule systemModule;

    SystemController(SystemModule systemModule) {
        this.systemModule = systemModule;
    }

    @GetMapping("/api/system")
    SystemStatusResponse systemStatus() {
        SystemStatus status = systemModule.currentStatus();
        return new SystemStatusResponse(status.status(), status.version(), status.buildTime(), status.serverTime());
    }
}
