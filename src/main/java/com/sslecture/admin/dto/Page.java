package com.sslecture.admin.dto;

import java.util.List;

/** 列表的分頁格式（backend_api_spec.md「分頁格式」）。 */
public record Page<T>(List<T> items, long total, int page, int pageSize) {}
