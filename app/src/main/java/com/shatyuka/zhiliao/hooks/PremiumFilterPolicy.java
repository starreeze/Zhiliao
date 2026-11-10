package com.shatyuka.zhiliao.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Classification uses content metadata, never comment permissions or body keywords. */
final class PremiumFilterPolicy {
    static boolean paidAnswer(Object businessType, Object labelText) {
        return "paid".equals(businessType) || "选自盐选专栏".equals(labelText);
    }

    static boolean paidShortContent(Object contentType, Object businessType) {
        return "answer".equals(contentType) && "paid".equals(businessType);
    }

    static <T> List<T> filter(List<T> source, Predicate<T> paid) {
        ArrayList<T> kept = null;
        for (int index = 0; index < source.size(); index++) {
            T item = source.get(index);
            if (paid.test(item)) {
                if (kept == null) kept = new ArrayList<>(source.subList(0, index));
            } else if (kept != null) {
                kept.add(item);
            }
        }
        return kept == null ? source : kept;
    }
}
