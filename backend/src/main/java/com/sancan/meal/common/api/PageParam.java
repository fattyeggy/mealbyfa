package com.sancan.meal.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 分页入参基类：pageNo 从 1 开始，与 RuoYi 的 PageParam 口径一致。 */
public class PageParam {

    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNo = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能大于 200")
    private Integer pageSize = 20;

    public Integer getPageNo() {
        return pageNo == null ? 1 : pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public Integer getPageSize() {
        return pageSize == null ? 20 : pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
