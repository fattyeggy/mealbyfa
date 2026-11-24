package com.sancan.meal.cook.controller;

import com.sancan.meal.common.api.CommonResult;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.security.Permission;
import com.sancan.meal.common.security.RequirePermission;
import com.sancan.meal.cook.controller.dto.CookContent;
import com.sancan.meal.cook.controller.dto.CookRecordReqs;
import com.sancan.meal.cook.dal.dataobject.CookPhotoDO;
import com.sancan.meal.cook.service.CookCalculation;
import com.sancan.meal.cook.service.CookRecordService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

/** 做菜记录：厨房边做边记，算出不同分量的出品与营养，并同步成菜品版本。 */
@RestController
@RequestMapping("/admin/meal/cook-records")
public class CookRecordController {

    private final CookRecordService service;

    public CookRecordController(CookRecordService service) {
        this.service = service;
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping
    public CommonResult<PageResult<CookRecordService.Summary>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return CommonResult.ok(service.page(keyword, pageNo, pageSize));
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping("/{id}")
    public CommonResult<CookRecordService.Detail> detail(@PathVariable Long id) {
        return CommonResult.ok(service.detail(id));
    }

    @RequirePermission(Permission.DISH_WRITE)
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody CookRecordReqs.CreateReq req) {
        return CommonResult.ok(service.create(req));
    }

    /** 保存并返回最新计算结果；手机端边记边自动保存。 */
    @RequirePermission(Permission.DISH_WRITE)
    @PutMapping("/{id}")
    public CommonResult<CookRecordService.Detail> update(@PathVariable Long id,
                                                         @Valid @RequestBody CookRecordReqs.UpdateReq req) {
        return CommonResult.ok(service.update(id, req));
    }

    /** 只计算不保存。 */
    @RequirePermission(Permission.MENU_READ)
    @PostMapping("/calculate")
    public CommonResult<CookCalculation> calculate(@RequestBody CookContent content) {
        return CommonResult.ok(service.calculate(content));
    }

    @RequirePermission(Permission.DISH_WRITE)
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.ok(true);
    }

    @RequirePermission(Permission.DISH_WRITE)
    @PostMapping("/{id}/copy")
    public CommonResult<Long> copy(@PathVariable Long id) {
        return CommonResult.ok(service.copy(id));
    }

    @RequirePermission(Permission.DISH_WRITE)
    @PostMapping("/{id}/sync")
    public CommonResult<CookRecordService.SyncResult> sync(@PathVariable Long id,
                                                          @RequestBody(required = false) CookRecordReqs.SyncReq req) {
        return CommonResult.ok(service.sync(id, req));
    }

    @RequirePermission(Permission.DISH_WRITE)
    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResult<Long> uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return CommonResult.ok(service.uploadPhoto(id, file));
    }

    @RequirePermission(Permission.MENU_READ)
    @GetMapping("/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@PathVariable Long photoId) {
        CookPhotoDO photo = service.getPhoto(photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .body(photo.getData());
    }

    @RequirePermission(Permission.DISH_WRITE)
    @DeleteMapping("/photos/{photoId}")
    public CommonResult<Boolean> deletePhoto(@PathVariable Long photoId) {
        service.deletePhoto(photoId);
        return CommonResult.ok(true);
    }
}
