package com.example.backend.module.surface.controller;

import com.example.backend.common.api.ApiResult;
import com.example.backend.module.surface.entity.Tip;
import com.example.backend.module.surface.service.ITipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/tip")
public class TipController {
    @Resource
    private ITipService iTipService;

    @GetMapping("/today")
    public ApiResult<Tip> getRandomTip() {
        Tip tip = iTipService.getRandomTip();
        return ApiResult.success(tip);
    }
}
