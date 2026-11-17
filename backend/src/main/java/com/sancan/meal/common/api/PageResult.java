package com.sancan.meal.common.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/** 分页响应，字段名与 RuoYi {@code PageResult} 一致（list / total）。 */
public class PageResult<T> implements Serializable {

    private List<T> list;
    private Long total;

    public PageResult() {
        this.list = Collections.emptyList();
        this.total = 0L;
    }

    public PageResult(List<T> list, long total) {
        this.list = list;
        this.total = total;
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>();
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
