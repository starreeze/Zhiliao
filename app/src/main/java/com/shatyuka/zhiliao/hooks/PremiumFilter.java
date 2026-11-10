package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Filters complete items at independent page boundaries, retaining native completion and paging. */
public final class PremiumFilter implements IHook {
    private static final String MODELS = "com.zhihu.android.api.model.";
    private ClassLoader loader;
    private boolean reportedModelFailure;
    private final PremiumBrowserContinuation browserContinuation = new PremiumBrowserContinuation();
    private final PremiumListContinuation listContinuation = new PremiumListContinuation();

    @Override
    public String getName() { return "盐选付费内容过滤"; }

    @Override
    public void init(ClassLoader classLoader) { loader = classLoader; }

    @Override
    public void hook() {
        install("recommend", () -> {
            Class<?> fragment = loader.loadClass("com.zhihu.android.app.feed.ui.fragment.FeedRecommendItemFragment2");
            Class<?> feedList = loader.loadClass(MODELS + "FeedList");
            Method refresh = fragment.getDeclaredMethod("a", feedList);
            Method more = fragment.getDeclaredMethod("b", feedList);
            if (!listContinuation.installHome(loader))
                throw new NoSuchMethodException("recommend continuation callbacks unavailable");
            hookList(refresh, "recommend");
            hookList(more, "recommend");
        });
        install("question", () -> {
            Class<?> wrapper = loader.loadClass("com.zhihu.android.question.api.model.AnswerListWrapper");
            Class<?> answerList = loader.loadClass(MODELS + "IAnswerList");
            Class<?> extensions = loader.loadClass(MODELS + "IAnswerListKt");
            Method get = extensions.getDeclaredMethod("data", answerList);
            Method set = extensions.getDeclaredMethod("setData", answerList, List.class);
            Method setup = wrapper.getDeclaredMethod("setupAnswerData", answerList, boolean.class);
            if (!listContinuation.installQuestion(loader))
                throw new NoSuchMethodException("question continuation callbacks unavailable");
            XposedBridge.hookMethod(setup, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!enabled("question")) return;
                    try {
                        List<?> source = (List<?>) get.invoke(null, param.args[0]);
                        if (source == null) return;
                        List<?> kept = filterAnswers(source);
                        if (kept != source) {
                            set.invoke(null, param.args[0], kept);
                            if (kept.isEmpty()) listContinuation.markQuestion(param.thisObject);
                            removed("question", source.size() - kept.size());
                        }
                    } catch (ReflectiveOperationException | ClassCastException error) {
                        modelFailure(error);
                    }
                }
            });
        });
        install("browser", () -> {
            Class<?> repository = loader.loadClass("com.zhihu.android.service.short_container_service.dataflow.repo.c");
            Method collection = repository.getDeclaredMethod("a", loader.loadClass(MODELS + "ZHObjectList"));
            if (!browserContinuation.install(loader))
                throw new NoSuchMethodException("browser continuation callbacks unavailable");
            // This collection-only method precedes index assignment and rendering. Direct targets
            // and expanded-body continuation use separate overloads and never enter this hook.
            hookList(collection, "browser");
        });
    }

    private void hookList(Method method, String surface) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!enabled(surface)) return;
                if ("recommend".equals(surface) && param.thisObject.getClass() != method.getDeclaringClass()) return;
                try {
                    Object response = param.args[0];
                    if (response == null) return;
                    Field data = field(response.getClass(), "data");
                    List<?> source = (List<?>) data.get(response);
                    if (source == null) return;
                    List<?> kept = "browser".equals(surface)
                            ? PremiumFilterPolicy.filter(source, PremiumFilter.this::paidShortContent)
                            : PremiumFilterPolicy.filter(source, PremiumFilter.this::paidRecommendation);
                    if (kept != source) {
                        data.set(response, kept);
                        if ("browser".equals(surface) && kept.isEmpty())
                            browserContinuation.markFilteredEmpty(response);
                        if ("recommend".equals(surface) && kept.isEmpty())
                            listContinuation.markHome(response);
                        removed(surface, source.size() - kept.size());
                    }
                } catch (ReflectiveOperationException | ClassCastException error) {
                    modelFailure(error);
                }
            }
        });
    }

    private static boolean enabled(String surface) {
        return Helper.prefs.getBoolean("switch_mainswitch", false)
                && Helper.prefs.getBoolean("switch_filter_paid_" + surface, false);
    }

    private static void removed(String surface, int count) {
        XposedBridge.log("[BetterZhiliao] premium " + surface + " removed=" + count);
    }

    private List<?> filterAnswers(List<?> source) {
        return PremiumFilterPolicy.filter(source, this::paidAnswer);
    }

    private boolean paidRecommendation(Object item) {
        try {
            if (item != null && (MODELS + "Feed").equals(item.getClass().getName()))
                item = optional(item, "target");
            // SDUI Card has no verified premium-positive metadata schema yet. Keep it intact.
            return paidAnswer(item);
        } catch (ReflectiveOperationException error) {
            modelFailure(error);
            return false;
        }
    }

    private boolean paidAnswer(Object item) {
        try {
            if (item != null && (MODELS + "MixShortCardTargetWrapper").equals(item.getClass().getName()))
                item = item.getClass().getMethod("getTarget").invoke(item);
            if (item == null || !(MODELS + "Answer").equals(item.getClass().getName())) return false;
            Object label = optional(item, "labelInfo");
            return PremiumFilterPolicy.paidAnswer(optional(item, "businessType"), optional(label, "text"));
        } catch (ReflectiveOperationException error) {
            modelFailure(error);
            return false;
        }
    }

    private boolean paidShortContent(Object item) {
        if (item == null || !"com.zhihu.android.service.short_container_service.dataflow.model.ShortContent".equals(item.getClass().getName()))
            return false;
        try {
            return PremiumFilterPolicy.paidShortContent(optional(item, "contentType"), optional(item, "businessType"));
        } catch (ReflectiveOperationException error) {
            modelFailure(error);
            return false;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) { /* Search inherited model fields. */ }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Object optional(Object model, String name) throws IllegalAccessException {
        if (model == null) return null;
        try { return field(model.getClass(), name).get(model); }
        catch (NoSuchFieldException missing) { return null; }
    }

    private synchronized void modelFailure(Exception error) {
        if (reportedModelFailure) return;
        reportedModelFailure = true;
        XposedBridge.log("[BetterZhiliao] premium model unavailable: " + error);
    }

    private interface Installation { void run() throws ReflectiveOperationException; }

    private void install(String surface, Installation installation) {
        try {
            installation.run();
            XposedBridge.log("[BetterZhiliao] premium " + surface + " hook installed");
        }
        catch (ReflectiveOperationException | LinkageError unavailable) {
            // Version-specific surfaces are optional; one missing surface must not disable others.
            XposedBridge.log("[BetterZhiliao] premium " + surface + " hook unavailable: " + unavailable);
        }
    }
}
