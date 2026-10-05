package com.trihydro.rsuinfobridge.controller;

import com.trihydro.rsuinfobridge.mapper.RsuDtoMapper;
import com.trihydro.rsuinfobridge.models.dtos.RsuDto;
import com.trihydro.rsuinfobridge.models.dtos.RsuFilter;
import com.trihydro.rsuinfobridge.service.RsuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
    private final RsuDtoMapper rsuDtoMapper;

    @GetMapping
    @Operation(summary = "Get all RSUs", description = "Retrieves a list of all Roadside Units filtered by primary route and TIM deposit status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of RSUs")
    })
    public List<RsuDto> getAll(@ParameterObject RsuFilter filter) {
        return rsuDtoMapper.toDtoList(rsuService.getAll(filter));
    }
}
