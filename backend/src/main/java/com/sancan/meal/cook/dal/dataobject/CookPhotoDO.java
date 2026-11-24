package com.sancan.meal.cook.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 做菜步骤照片。存在数据库而不是本地磁盘：云托管容器的磁盘不持久，重新部署会丢文件。
 * 前端上传前已压缩到长边 1280px 左右，单张通常在 300KB 以内。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_cook_photo")
public class CookPhotoDO extends BaseDO {

    private Long recordId;
    private String contentType;
    private Integer sizeBytes;
    private byte[] data;
}
