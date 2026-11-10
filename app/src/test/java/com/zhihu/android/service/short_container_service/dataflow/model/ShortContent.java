package com.zhihu.android.service.short_container_service.dataflow.model;

public class ShortContent {
    private final String contentType;
    private final String businessType;

    public ShortContent(String contentType, String businessType) {
        this.contentType = contentType;
        this.businessType = businessType;
    }
}
