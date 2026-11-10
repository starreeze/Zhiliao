package com.shatyuka.zhiliao.hooks;

import com.zhihu.android.api.model.Answer;
import com.zhihu.android.api.model.Feed;
import com.zhihu.android.api.model.MixShortCardTargetWrapper;
import com.zhihu.android.service.short_container_service.dataflow.model.ShortContent;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PremiumFilterModelTest {
    private final PremiumFilter hook = new PremiumFilter();

    public static class UnverifiedCard {
        public List<String> elements;
        public Map<String, Object> metadata;
    }

    private Object invoke(String name, Class<?> argType, Object arg) throws Exception {
        Method method = PremiumFilter.class.getDeclaredMethod(name, argType);
        method.setAccessible(true);
        return method.invoke(hook, arg);
    }

    @Test
    public void filtersWrappedAnswersButRetainsOtherWholeItems() throws Exception {
        Answer paid = new Answer(); paid.businessType = "paid";
        Answer ordinary = new Answer(); ordinary.businessType = "normal";
        MixShortCardTargetWrapper paidWrapper = new MixShortCardTargetWrapper(); paidWrapper.target = paid;
        MixShortCardTargetWrapper ordinaryWrapper = new MixShortCardTargetWrapper(); ordinaryWrapper.target = ordinary;
        Object nonAnswer = new Object();
        List<?> source = Arrays.asList(ordinaryWrapper, paidWrapper, nonAnswer, paid);
        List<?> kept = (List<?>) invoke("filterAnswers", List.class, source);
        assertEquals(Arrays.asList(ordinaryWrapper, nonAnswer), kept);
        assertSame(ordinaryWrapper, kept.get(0));
        assertSame(ordinary, ordinaryWrapper.getTarget());
        assertEquals(4, source.size());
    }

    @Test
    public void readsDedicatedAnswerLabelAndPrivateShortContentFields() throws Exception {
        Answer labeled = new Answer(); labeled.labelInfo = new Answer.LabelInfo();
        labeled.labelInfo.text = "选自盐选专栏";
        assertEquals(true, invoke("paidAnswer", Object.class, labeled));
        assertEquals(true, invoke("paidShortContent", Object.class, new ShortContent("answer", "paid")));
        assertEquals(false, invoke("paidShortContent", Object.class, new ShortContent("pin", "paid")));
        assertEquals(false, invoke("paidShortContent", Object.class, new ShortContent("answer", "normal")));
        assertEquals(false, invoke("paidAnswer", Object.class, new Object()));
        assertEquals(false, invoke("paidAnswer", Object.class, null));
    }

    @Test
    public void recommendationUnwrapsOnlyVerifiedFeedTarget() throws Exception {
        Answer paid = new Answer(); paid.businessType = "paid";
        Feed feed = new Feed(); feed.target = paid;
        assertEquals(true, invoke("paidRecommendation", Object.class, feed));
        assertSame(paid, feed.target);
        feed.target = new Object();
        assertEquals(false, invoke("paidRecommendation", Object.class, feed));
        assertEquals(false, invoke("paidAnswer", Object.class, feed));
    }

    @Test
    public void recommendationRetainsUnverifiedCardsAndQuotedLabels() throws Exception {
        UnverifiedCard card = new UnverifiedCard();
        card.elements = Collections.singletonList("选自盐选专栏");
        card.metadata = new HashMap<>();
        card.metadata.put("business_type", "paid");
        card.metadata.put("can_reply", false);
        // A shared field name is insufficient evidence of this unknown card schema's meaning.
        assertEquals(false, invoke("paidRecommendation", Object.class, card));
        Feed feed = new Feed(); feed.target = card;
        assertEquals(false, invoke("paidRecommendation", Object.class, feed));
        assertSame(card, feed.target);
    }
}
