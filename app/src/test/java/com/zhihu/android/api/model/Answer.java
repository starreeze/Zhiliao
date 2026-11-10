package com.zhihu.android.api.model;

/** Minimal verified host schema; this fixture is never packaged in the APK. */
public class Answer {
    public String businessType;
    public LabelInfo labelInfo;

    public static class LabelInfo { public String text; }
}
