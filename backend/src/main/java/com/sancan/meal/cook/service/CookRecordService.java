package com.sancan.meal.cook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sancan.meal.common.api.PageResult;
import com.sancan.meal.common.exception.ErrorCode;
import com.sancan.meal.common.exception.ServiceException;
import com.sancan.meal.cook.controller.dto.CookContent;
import com.sancan.meal.cook.controller.dto.CookRecordReqs;
import com.sancan.meal.cook.dal.dataobject.CookPhotoDO;
import com.sancan.meal.cook.dal.dataobject.CookRecordDO;
import com.sancan.meal.cook.dal.mapper.CookPhotoMapper;
import com.sancan.meal.cook.dal.mapper.CookRecordMapper;
import com.sancan.meal.menu.controller.dto.DishVersionSaveReq;
import com.sancan.meal.menu.dal.dataobject.DishDO;
import com.sancan.meal.menu.dal.dataobject.DishVersionDO;
import com.sancan.meal.menu.dal.dataobject.IngredientDO;
import com.sancan.meal.menu.dal.mapper.DishMapper;
import com.sancan.meal.menu.dal.mapper.DishVersionMapper;
import com.sancan.meal.menu.dal.mapper.IngredientMapper;
import com.sancan.meal.menu.service.DishService;
import com.sancan.meal.nutrition.service.NutritionEngine;
import com.sancan.meal.plan.service.NutritionTargetCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CookRecordService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SYNCED = "SYNCED";

    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_INGREDIENTS = 80;
    private static final int MAX_COMPONENTS = 12;
    private static final int MAX_STEPS = 60;

    private final CookRecordMapper recordMapper;
    private final CookPhotoMapper photoMapper;
    private final IngredientMapper ingredientMapper;
    private final DishMapper dishMapper;
    private final DishVersionMapper dishVersionMapper;
    private final DishService dishService;
    private final NutritionTargetCalculator targetCalculator;
    private final ObjectMapper objectMapper;

    public CookRecordService(CookRecordMapper recordMapper, CookPhotoMapper photoMapper,
                             IngredientMapper ingredientMapper, DishMapper dishMapper,
                             DishVersionMapper dishVersionMapper, DishService dishService,
                             NutritionTargetCalculator targetCalculator, ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.photoMapper = photoMapper;
        this.ingredientMapper = ingredientMapper;
        this.dishMapper = dishMapper;
        this.dishVersionMapper = dishVersionMapper;
        this.dishService = dishService;
        this.targetCalculator = targetCalculator;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- 读

    public record Summary(Long id, String title, Long dishId, String dishName, LocalDate cookDate,
                          String status, Long syncedVersionId, LocalDateTime updateTime) {
    }

    public record Detail(Long id, String title, Long dishId, String dishName, LocalDate cookDate,
                         String status, Long syncedVersionId, Integer syncedVersionNo,
                         LocalDateTime syncedTime, String remark, CookContent content,
                         CookCalculation calculation) {
    }

    public PageResult<Summary> page(String keyword, int pageNo, int pageSize) {
        LambdaQueryWrapper<CookRecordDO> wrapper = new LambdaQueryWrapper<CookRecordDO>()
                // 列表不取 content，避免把整锅记录都读出来
                .select(CookRecordDO::getId, CookRecordDO::getTitle, CookRecordDO::getDishId,
                        CookRecordDO::getCookDate, CookRecordDO::getStatus,
                        CookRecordDO::getSyncedVersionId, CookRecordDO::getUpdateTime)
                .orderByDesc(CookRecordDO::getCookDate)
                .orderByDesc(CookRecordDO::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(CookRecordDO::getTitle, keyword.trim());
        }
        Page<CookRecordDO> page = recordMapper.selectPage(new Page<>(pageNo, Math.min(pageSize, 100)), wrapper);
        Map<Long, String> dishNames = dishNames(page.getRecords().stream()
                .map(CookRecordDO::getDishId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<Summary> list = page.getRecords().stream()
                .map(r -> new Summary(r.getId(), r.getTitle(), r.getDishId(), dishNames.get(r.getDishId()),
                        r.getCookDate(), r.getStatus(), r.getSyncedVersionId(), r.getUpdateTime()))
                .toList();
        return new PageResult<>(list, page.getTotal());
    }

    public Detail detail(Long id) {
        return toDetail(get(id));
    }

    // ---------------------------------------------------------------- 写

    @Transactional
    public Long create(CookRecordReqs.CreateReq req) {
        if (req.dishId() != null) {
            dishService.getDish(req.dishId());
        }
        CookRecordDO record = new CookRecordDO();
        record.setTitle(req.title().trim());
        record.setDishId(req.dishId());
        record.setCookDate(req.cookDate() == null ? LocalDate.now() : req.cookDate());
        record.setStatus(STATUS_DRAFT);
        record.setContent(write(CookContent.starter()));
        recordMapper.insert(record);
        return record.getId();
    }

    @Transactional
    public Detail update(Long id, CookRecordReqs.UpdateReq req) {
        CookRecordDO record = get(id);
        if (req.dishId() != null) {
            dishService.getDish(req.dishId());
        }
        CookContent content = req.content() == null ? read(record.getContent()) : req.content().normalized();
        validate(content, id);
        String json = write(content);
        boolean changed = !Objects.equals(json, record.getContent())
                || !Objects.equals(req.dishId(), record.getDishId());
        record.setTitle(req.title().trim());
        record.setDishId(req.dishId());
        record.setCookDate(req.cookDate() == null ? record.getCookDate() : req.cookDate());
        record.setRemark(req.remark());
        record.setContent(json);
        if (changed && STATUS_SYNCED.equals(record.getStatus())) {
            // 同步后又改了内容：回到未同步，但保留上次同步的版本号以便对照
            record.setStatus(STATUS_DRAFT);
        }
        recordMapper.updateById(record);
        return toDetail(record);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        recordMapper.deleteById(id);
        photoMapper.delete(new LambdaQueryWrapper<CookPhotoDO>().eq(CookPhotoDO::getRecordId, id));
    }

    /** 再做一锅：复制食材、组件、步骤与份数，清空熟重和照片，需要重新称。 */
    @Transactional
    public Long copy(Long id) {
        CookRecordDO source = get(id);
        CookContent c = read(source.getContent());
        List<CookContent.Part> parts = c.components().stream()
                .map(p -> new CookContent.Part(p.key(), p.name(), p.role(), null, p.standardG()))
                .toList();
        List<CookContent.Step> steps = c.steps().stream()
                .map(s -> new CookContent.Step(s.key(), s.text(), s.heat(), s.durationSec(), List.of()))
                .toList();
        CookRecordDO record = new CookRecordDO();
        record.setTitle(source.getTitle());
        record.setDishId(source.getDishId());
        record.setCookDate(LocalDate.now());
        record.setStatus(STATUS_DRAFT);
        record.setContent(write(new CookContent(c.portions(), c.ingredients(), parts, steps, c.weightTargets())));
        recordMapper.insert(record);
        return record.getId();
    }

    public record SyncResult(Long dishId, Long versionId, Integer versionNo) {
    }

    /** 按标准一份生成新的菜品版本与配方。旧的生效版本会被归档，已发布菜单引用的版本不受影响。 */
    @Transactional
    public SyncResult sync(Long id, CookRecordReqs.SyncReq req) {
        CookRecordDO record = get(id);
        Long dishId = req != null && req.dishId() != null ? req.dishId() : record.getDishId();
        if (dishId == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "请先选择或新建要同步到的菜品");
        }
        DishDO dish = dishService.getDish(dishId);
        CookCalculation calc = calculate(read(record.getContent()));
        if (!calc.blockers().isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "还不能同步：" + String.join("；", calc.blockers()));
        }
        if (calc.standard() == null || calc.bom().isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "还不能同步：没有可用的食材与出品组件");
        }
        List<DishVersionSaveReq.BomLineReq> bom = calc.bom().stream()
                .map(l -> new DishVersionSaveReq.BomLineReq(l.ingredientId(), l.rawG(), l.note(),
                        l.componentType(), l.cookedG()))
                .toList();
        CookCalculation.Plate std = calc.standard();
        String remark = "做菜记录 #" + record.getId() + " · " + record.getCookDate() + " · " + record.getTitle();
        DishVersionSaveReq versionReq = new DishVersionSaveReq(
                std.weightG(), std.nutrients().kcal(), std.nutrients().proteinG(),
                std.nutrients().carbG(), std.nutrients().fatG(),
                remark.length() > 255 ? remark.substring(0, 255) : remark, bom);
        Long versionId = dishService.createVersion(dish.getId(), versionReq);
        DishVersionDO version = dishVersionMapper.selectById(versionId);

        record.setDishId(dish.getId());
        record.setStatus(STATUS_SYNCED);
        record.setSyncedVersionId(versionId);
        record.setSyncedTime(LocalDateTime.now());
        recordMapper.updateById(record);
        return new SyncResult(dish.getId(), versionId, version.getVersion());
    }

    // ---------------------------------------------------------------- 照片

    @Transactional
    public Long uploadPhoto(Long recordId, MultipartFile file) {
        get(recordId);
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "没有收到照片");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!PHOTO_TYPES.contains(type)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "只支持 JPG / PNG / WebP 照片");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "照片不能超过 5MB");
        }
        CookPhotoDO photo = new CookPhotoDO();
        photo.setRecordId(recordId);
        photo.setContentType(type);
        photo.setSizeBytes((int) file.getSize());
        try {
            photo.setData(file.getBytes());
        } catch (IOException e) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "照片读取失败，请重试");
        }
        photoMapper.insert(photo);
        return photo.getId();
    }

    public CookPhotoDO getPhoto(Long photoId) {
        CookPhotoDO photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "照片不存在");
        }
        return photo;
    }

    @Transactional
    public void deletePhoto(Long photoId) {
        getPhoto(photoId);
        photoMapper.deleteById(photoId);
    }

    // ---------------------------------------------------------------- 计算

    public CookCalculation calculate(CookContent content) {
        CookContent c = content.normalized();
        Set<Long> ids = c.ingredients().stream()
                .map(CookContent.Ingredient::ingredientId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, IngredientDO> library = new HashMap<>();
        if (!ids.isEmpty()) {
            for (IngredientDO ing : ingredientMapper.selectBatchIds(ids)) {
                library.put(ing.getId(), ing);
            }
        }
        List<CookCalculator.MealShare> meals = List.of(
                new CookCalculator.MealShare("LUNCH", "午餐", targetCalculator.share("LUNCH")),
                new CookCalculator.MealShare("DINNER", "晚餐", targetCalculator.share("DINNER")),
                new CookCalculator.MealShare("BREAKFAST", "早餐", targetCalculator.share("BREAKFAST")));
        return CookCalculator.calculate(c, library, NutritionEngine.defaultConfig().tiers(), meals);
    }

    // ---------------------------------------------------------------- 内部

    private CookRecordDO get(Long id) {
        CookRecordDO record = recordMapper.selectById(id);
        if (record == null) {
            throw new ServiceException(ErrorCode.NOT_FOUND, "做菜记录不存在");
        }
        return record;
    }

    private Detail toDetail(CookRecordDO record) {
        CookContent content = read(record.getContent());
        String dishName = null;
        if (record.getDishId() != null) {
            DishDO dish = dishMapper.selectById(record.getDishId());
            dishName = dish == null ? null : dish.getName();
        }
        Integer versionNo = null;
        if (record.getSyncedVersionId() != null) {
            DishVersionDO v = dishVersionMapper.selectById(record.getSyncedVersionId());
            versionNo = v == null ? null : v.getVersion();
        }
        return new Detail(record.getId(), record.getTitle(), record.getDishId(), dishName,
                record.getCookDate(), record.getStatus(), record.getSyncedVersionId(), versionNo,
                record.getSyncedTime(), record.getRemark(), content, calculate(content));
    }

    private void validate(CookContent c, Long recordId) {
        if (c.ingredients().size() > MAX_INGREDIENTS) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "食材最多 " + MAX_INGREDIENTS + " 行");
        }
        if (c.components().size() > MAX_COMPONENTS) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "出品组件最多 " + MAX_COMPONENTS + " 个");
        }
        if (c.steps().size() > MAX_STEPS) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "步骤最多 " + MAX_STEPS + " 步");
        }
        if (c.portions() != null && (c.portions() < 1 || c.portions() > 500)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "份数需在 1–500 之间");
        }
        Set<String> keys = new LinkedHashSet<>();
        for (CookContent.Part p : c.components()) {
            if (p.key() == null || p.key().isBlank() || !keys.add(p.key())) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "出品组件标识为空或重复");
            }
            if (p.name() == null || p.name().isBlank()) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "出品组件需要名字");
            }
            if (negative(p.cookedG()) || negative(p.standardG())) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "重量不能为负");
            }
        }
        for (CookContent.Ingredient i : c.ingredients()) {
            if (negative(i.rawG())) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "重量不能为负");
            }
        }
        // 步骤里的照片必须属于这条记录，防止串看别的记录的照片
        Set<Long> photoIds = new LinkedHashSet<>();
        for (CookContent.Step s : c.steps()) {
            if (s.photoIds() != null) {
                photoIds.addAll(s.photoIds());
            }
        }
        if (!photoIds.isEmpty()) {
            long owned = photoMapper.selectCount(new LambdaQueryWrapper<CookPhotoDO>()
                    .eq(CookPhotoDO::getRecordId, recordId)
                    .in(CookPhotoDO::getId, photoIds));
            if (owned != photoIds.size()) {
                throw new ServiceException(ErrorCode.BAD_REQUEST, "步骤里有不属于这条记录的照片");
            }
        }
    }

    private boolean negative(BigDecimal v) {
        return v != null && v.signum() < 0;
    }

    private Map<Long, String> dishNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (DishDO d : dishMapper.selectBatchIds(ids)) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    private CookContent read(String json) {
        if (json == null || json.isBlank()) {
            return CookContent.starter().normalized();
        }
        try {
            return objectMapper.readValue(json, CookContent.class).normalized();
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "做菜记录内容损坏，无法读取");
        }
    }

    private String write(CookContent content) {
        try {
            return objectMapper.writeValueAsString(content.normalized());
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "做菜记录内容无法保存");
        }
    }
}
