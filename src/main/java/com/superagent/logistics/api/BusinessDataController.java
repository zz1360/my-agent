package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.BusinessDataSourceResponse;
import com.superagent.logistics.business.BusinessDataCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/business-data")
public class BusinessDataController {

    private final BusinessDataCatalogService catalogService;

    public BusinessDataController(BusinessDataCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/sources")
    public BusinessDataSourceResponse sources(@RequestParam(required = false) String tenantId,
                                              @RequestParam(required = false) String userId,
                                              @RequestParam(required = false) List<String> roles) {
        return catalogService.describe(tenantId, userId, roles);
    }
}
