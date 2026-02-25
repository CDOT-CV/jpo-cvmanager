package com.trihydro.rsuinfobridge.controller;

import com.trihydro.rsuinfobridge.models.dtos.RsuDto;
import com.trihydro.rsuinfobridge.service.RsuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rsus")
@RequiredArgsConstructor
@Tag(name = "RSU", description = "Roadside Unit information endpoints")
public class RsuController {
    private final RsuService rsuService;

    @GetMapping("/all")
    @Operation(summary = "Get all RSUs", description = "Retrieves a list of all Roadside Units in the system")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of RSUs")
    })
    public List<RsuDto> getAllRsus() {
        return rsuService.getAllRsus();
    }

    @GetMapping("/all-tim-deposit-enabled")
    @Operation(summary = "Get all RSUs with TIM deposit enabled", description = "Retrieves a list of all Roadside Units that have Traveler Information Message (TIM) deposit capability enabled")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list of TIM-enabled RSUs")
    })
    public List<RsuDto> getAllRsusWithTimDepositEnabled() {
        return rsuService.getAllRsusWithTimDepositEnabled();
    }
}
