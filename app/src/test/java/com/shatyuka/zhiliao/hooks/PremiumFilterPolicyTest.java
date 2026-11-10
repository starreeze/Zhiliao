package com.shatyuka.zhiliao.hooks;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class PremiumFilterPolicyTest {
    @Test
    public void classifiesOnlyExplicitPaidMetadata() {
        assertTrue(PremiumFilterPolicy.paidAnswer("paid", null));
        assertTrue(PremiumFilterPolicy.paidAnswer(null, "选自盐选专栏"));
        assertFalse(PremiumFilterPolicy.paidAnswer("normal", null));
        assertFalse(PremiumFilterPolicy.paidAnswer(null, null));
        assertFalse(PremiumFilterPolicy.paidAnswer(null, "作者关闭了评论"));
        assertFalse(PremiumFilterPolicy.paidAnswer(null, "我看到选自盐选专栏这个标签"));
        assertFalse(PremiumFilterPolicy.paidAnswer("vip", null));
    }

    @Test
    public void browserRequiresAnswerAndPaidType() {
        assertTrue(PremiumFilterPolicy.paidShortContent("answer", "paid"));
        assertFalse(PremiumFilterPolicy.paidShortContent("answer", "normal"));
        assertFalse(PremiumFilterPolicy.paidShortContent("pin", "paid"));
        assertFalse(PremiumFilterPolicy.paidShortContent("article", "paid"));
        assertFalse(PremiumFilterPolicy.paidShortContent(null, "paid"));
    }

    @Test
    public void mixedImmutablePageRetainsOrderAndIdentity() {
        Object ordinary = new Object(), paid1 = new Object(), paid2 = new Object(), other = new Object();
        List<Object> source = Collections.unmodifiableList(Arrays.asList(ordinary, paid1, other, paid2));
        List<Object> kept = PremiumFilterPolicy.filter(source, item -> item == paid1 || item == paid2);
        assertEquals(Arrays.asList(ordinary, other), kept);
        assertSame(ordinary, kept.get(0));
        assertSame(other, kept.get(1));
        assertEquals(4, source.size());
        // The native parser can still append/process its ordinary mutable list.
        kept.add(new Object());
    }

    @Test
    public void unchangedAndEmptyPagesRemainValidCollections() {
        List<String> ordinary = Collections.singletonList("ordinary");
        assertSame(ordinary, PremiumFilterPolicy.filter(ordinary, item -> false));
        assertTrue(PremiumFilterPolicy.filter(ordinary, item -> true).isEmpty());
        List<String> empty = Collections.emptyList();
        assertSame(empty, PremiumFilterPolicy.filter(empty, item -> true));
    }
}
