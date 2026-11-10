package com.shatyuka.zhiliao.hooks;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Continues only exact list deliveries whose nonempty source was removed by the paid filter. */
final class PremiumListContinuation {
    private final Engine home = new Engine();
    private final Engine question = new Engine();

    boolean installHome(ClassLoader loader) { return install(loader, home, false); }
    boolean installQuestion(ClassLoader loader) { return install(loader, question, true); }

    private boolean install(ClassLoader loader, Engine engine, boolean isQuestion) {
        if (engine.host != null) return true;
        ArrayList<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            ReflectiveHost binding = new ReflectiveHost(loader, isQuestion);
            engine.host = binding;
            Method[] completions = {binding.refresh, binding.more};
            for (int index = 0; index < completions.length; index++) {
                final boolean refresh = index == 0;
                hooks.add(XposedBridge.hookMethod(completions[index], new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!binding.owns(param.thisObject) || param.hasThrowable()) return;
                        try { engine.afterCompletion(param.thisObject, param.args[0], refresh); }
                        catch (ReflectiveOperationException failure) { binding.failure(failure); }
                    }
                }));
            }
            hooks.add(XposedBridge.hookMethod(binding.onLoadMore, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!binding.owns(param.thisObject)) return;
                    try { engine.beforeLoadMore(param.thisObject, param.args[0]); }
                    catch (ReflectiveOperationException failure) { binding.failure(failure); }
                }
            }));
            hooks.add(XposedBridge.hookMethod(binding.onRefresh, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!binding.owns(param.thisObject)) return;
                    try { engine.newFlow(param.thisObject); }
                    catch (ReflectiveOperationException failure) { binding.failure(failure); }
                }
            }));
            hooks.add(XposedBridge.hookMethod(binding.onDestroyView, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!binding.owns(param.thisObject)) return;
                    try { engine.destroy(param.thisObject); }
                    catch (ReflectiveOperationException failure) { binding.failure(failure); }
                }
            }));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            engine.host = null;
            XposedBridge.log("[BetterZhiliao] premium " + (isQuestion ? "question" : "recommend")
                    + " continuation unavailable: " + failure);
            return false;
        }
    }

    void markHome(Object response) { home.mark(response); }
    void markQuestion(Object wrapper) { question.mark(wrapper); }

    /** State is independent of Android so callback ordering and posted requests can be tested. */
    static final class Engine {
        private static final int MAX_AUTOMATIC_REQUESTS = 3;
        private final WeakIdentityMap<Boolean> marked = new WeakIdentityMap<>();
        private final WeakIdentityMap<Sequence> sequences = new WeakIdentityMap<>();
        Host host;

        Engine() { }
        Engine(Host host) { this.host = host; }
        void mark(Object response) { marked.put(response, Boolean.TRUE); }

        void newFlow(Object fragment) throws ReflectiveOperationException {
            Sequence sequence = sequence(fragment);
            dismiss(fragment, sequence);
            sequence.reset();
        }

        void beforeLoadMore(Object fragment, Object paging) throws ReflectiveOperationException {
            Sequence sequence = sequence(fragment);
            if (sequence.pendingPaging != paging) {
                dismiss(fragment, sequence);
                sequence.reset();
            }
            sequence.pendingPaging = null;
            sequence.requestedNext = host.next(paging);
        }

        void afterCompletion(Object fragment, Object response, boolean refresh)
                throws ReflectiveOperationException {
            Sequence sequence = sequence(fragment);
            if (refresh) {
                dismiss(fragment, sequence);
                sequence.reset();
            }
            // Consume now: question responses reuse their cumulative AnswerListWrapper.
            boolean filteredEmpty = marked.remove(response) != null;
            Object paging = filteredEmpty ? host.paging(response) : null;
            int generation = ++sequence.generation;
            host.enqueue(fragment, () -> {
                if (sequences.get(fragment) != sequence || sequence.generation != generation) return;
                try {
                    if (!host.active(fragment)) { destroy(fragment); return; }
                    finishCompletion(fragment, sequence, filteredEmpty, paging);
                } catch (ReflectiveOperationException failure) { host.failure(failure); }
            });
        }

        private void finishCompletion(Object fragment, Sequence sequence, boolean filteredEmpty,
                                      Object paging) throws ReflectiveOperationException {
            if (!filteredEmpty) {
                dismiss(fragment, sequence);
                sequence.reset();
                return;
            }
            if (paging == null) return;
            if (host.end(paging)) {
                dismiss(fragment, sequence);
                sequence.reset();
                return;
            }
            // The original callback has completed; retain its real terminal/footer behavior.
            host.restore(fragment, paging);
            String next = host.next(paging);
            if (next == null || next.isEmpty()) return;
            boolean progressed = !next.equals(sequence.requestedNext) && sequence.seen.add(next);
            if (progressed && sequence.automaticRequests < MAX_AUTOMATIC_REQUESTS) {
                sequence.automaticRequests++;
                request(fragment, sequence, paging);
            } else if (!host.hasVisibleRows(fragment)) {
                dismiss(fragment, sequence);
                WeakReference<Object> owner = new WeakReference<>(fragment);
                int generation = sequence.controlGeneration;
                int delivery = sequence.generation;
                sequence.control = host.showContinue(fragment, () -> {
                    Object current = owner.get();
                    if (current == null || sequences.get(current) != sequence
                            || sequence.controlGeneration != generation || sequence.generation != delivery) return;
                    try {
                        if (!host.active(current)) { destroy(current); return; }
                        dismiss(current, sequence);
                        sequence.reset();
                        sequence.seen.add(next);
                        request(current, sequence, paging);
                    } catch (ReflectiveOperationException failure) { host.failure(failure); }
                });
            }
        }

        void destroy(Object fragment) throws ReflectiveOperationException {
            Sequence sequence = sequences.remove(fragment);
            if (sequence != null) dismiss(fragment, sequence);
        }

        private void request(Object fragment, Sequence sequence, Object paging)
                throws ReflectiveOperationException {
            sequence.pendingPaging = paging;
            host.request(fragment, paging);
        }

        private void dismiss(Object fragment, Sequence sequence) throws ReflectiveOperationException {
            sequence.controlGeneration++;
            if (sequence.control != null) host.dismiss(fragment, sequence.control);
            sequence.control = null;
        }

        private Sequence sequence(Object fragment) {
            Sequence sequence = sequences.get(fragment);
            if (sequence == null) {
                sequence = new Sequence();
                sequences.put(fragment, sequence);
            }
            return sequence;
        }
    }

    interface Host {
        void enqueue(Object fragment, Runnable action) throws ReflectiveOperationException;
        void failure(Throwable failure);
        boolean active(Object fragment) throws ReflectiveOperationException;
        Object paging(Object response) throws ReflectiveOperationException;
        boolean end(Object paging) throws ReflectiveOperationException;
        String next(Object paging) throws ReflectiveOperationException;
        boolean hasVisibleRows(Object fragment) throws ReflectiveOperationException;
        void restore(Object fragment, Object paging) throws ReflectiveOperationException;
        void request(Object fragment, Object paging) throws ReflectiveOperationException;
        Object showContinue(Object fragment, Runnable action) throws ReflectiveOperationException;
        void dismiss(Object fragment, Object control) throws ReflectiveOperationException;
    }

    /** Every required host method is resolved before installing this surface's hook group. */
    private static final class ReflectiveHost implements Host {
        final Class<?> fragment;
        final Method refresh, more, onLoadMore, onRefresh, onDestroyView;
        final Method setPaging, clear, loadMore, getDataList, getView, getRecyclerView, post, getNext;
        final Method makeSnackbar, setAction, showSnackbar, dismissSnackbar;
        final Class<?> clickListener;
        final Field paging, isEnd;
        boolean reportedFailure;

        ReflectiveHost(ClassLoader loader, boolean question) throws ReflectiveOperationException {
            String models = "com.zhihu.android.api.model.";
            Class<?> page = loader.loadClass(models + "Paging");
            Class<?> list = loader.loadClass(models + "ZHObjectList");
            Class<?> response = loader.loadClass(question
                    ? "com.zhihu.android.question.api.model.AnswerListWrapper" : models + "FeedList");
            fragment = loader.loadClass(question
                    ? "com.zhihu.android.question.list.QuestionPagerAnswerListFragment"
                    : "com.zhihu.android.app.feed.ui.fragment.FeedRecommendItemFragment2");
            refresh = method(fragment, question ? "postRefreshSucceed" : "a", void.class,
                    question ? list : response);
            more = method(fragment, question ? "postLoadMoreSucceed" : "b", void.class,
                    question ? list : response);
            onLoadMore = method(fragment, "onLoadMore", void.class, page);
            onRefresh = method(fragment, "onRefresh", void.class, boolean.class);
            onDestroyView = method(fragment, "onDestroyView", void.class);
            setPaging = method(fragment, "setPaging", void.class, page);
            clear = method(fragment, "clearLoadingEmptyAndError", void.class);
            loadMore = method(fragment, "loadMore", void.class, page);
            getDataList = method(fragment, "getDataList", List.class);
            Class<?> view = loader.loadClass("android.view.View");
            getView = method(fragment, "getView", view);
            Class<?> recyclerView = loader.loadClass("androidx.recyclerview.widget.RecyclerView");
            getRecyclerView = method(fragment, "getRecyclerView", recyclerView);
            post = method(view, "post", boolean.class, Runnable.class);
            getNext = method(page, "getNext", String.class);
            paging = field(response, "paging", page);
            isEnd = field(page, "isEnd", boolean.class);
            Class<?> snackbar = loader.loadClass("com.google.android.material.snackbar.Snackbar");
            clickListener = loader.loadClass("android.view.View$OnClickListener");
            makeSnackbar = method(snackbar, "make", snackbar, view, CharSequence.class, int.class);
            setAction = method(snackbar, "setAction", snackbar, CharSequence.class, clickListener);
            showSnackbar = method(snackbar, "show", void.class);
            dismissSnackbar = method(snackbar, "dismiss", void.class);
        }

        boolean owns(Object receiver) { return receiver.getClass() == fragment; }
        @Override public void enqueue(Object receiver, Runnable action) throws ReflectiveOperationException {
            if (getView.invoke(receiver) != null) post.invoke(getRecyclerView.invoke(receiver), action);
        }
        @Override public boolean active(Object receiver) throws ReflectiveOperationException {
            return getView.invoke(receiver) != null;
        }
        @Override public Object paging(Object response) throws IllegalAccessException { return paging.get(response); }
        @Override public boolean end(Object page) throws IllegalAccessException { return isEnd.getBoolean(page); }
        @Override public String next(Object page) throws ReflectiveOperationException {
            return (String) getNext.invoke(page);
        }
        @Override public boolean hasVisibleRows(Object receiver) throws ReflectiveOperationException {
            // restore() removes the host's own empty/error/progress/end objects first.
            return !((List<?>) getDataList.invoke(receiver)).isEmpty();
        }
        @Override public void restore(Object receiver, Object page) throws ReflectiveOperationException {
            setPaging.invoke(receiver, page);
            clear.invoke(receiver);
        }
        @Override public void request(Object receiver, Object page) throws ReflectiveOperationException {
            loadMore.invoke(receiver, page);
        }
        @Override public Object showContinue(Object receiver, Runnable action) throws ReflectiveOperationException {
            Object snackbar = makeSnackbar.invoke(null, getView.invoke(receiver), "本页盐选回答已过滤", -2);
            Object listener = Proxy.newProxyInstance(clickListener.getClassLoader(), new Class<?>[]{clickListener},
                    (proxy, method, args) -> {
                        if ("onClick".equals(method.getName())) { action.run(); return null; }
                        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                        if ("equals".equals(method.getName())) return proxy == args[0];
                        return "PremiumListContinueClick";
                    });
            setAction.invoke(snackbar, "继续加载", listener);
            showSnackbar.invoke(snackbar);
            return snackbar;
        }
        @Override public void dismiss(Object receiver, Object control) throws ReflectiveOperationException {
            dismissSnackbar.invoke(control);
        }
        @Override public void failure(Throwable failure) {
            if (reportedFailure) return;
            reportedFailure = true;
            XposedBridge.log("[BetterZhiliao] premium list continuation unavailable: " + failure);
        }
    }

    private static Method method(Class<?> type, String name, Class<?> returns, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                if (method.getReturnType() != returns) throw new NoSuchMethodException(type.getName() + "." + name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException inherited) { /* Search inherited native fragment methods. */ }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Field field(Class<?> type, String name, Class<?> expected) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (!expected.isAssignableFrom(field.getType())) throw new NoSuchFieldException(type.getName() + "." + name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException inherited) { /* Search inherited response fields. */ }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static final class Sequence {
        final HashSet<String> seen = new HashSet<>();
        int automaticRequests;
        int generation;
        String requestedNext;
        Object pendingPaging;
        Object control;
        int controlGeneration;
        void reset() {
            generation++;
            automaticRequests = 0;
            seen.clear();
            requestedNext = null;
            pendingPaging = null;
        }
    }

    private static final class WeakIdentityMap<T> {
        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
        private final Map<IdentityReference, T> values = new HashMap<>();
        synchronized T get(Object key) { clean(); return values.get(new IdentityReference(key)); }
        synchronized void put(Object key, T value) { clean(); values.put(new IdentityReference(key, queue), value); }
        synchronized T remove(Object key) { clean(); return values.remove(new IdentityReference(key)); }
        private void clean() {
            IdentityReference expired;
            while ((expired = (IdentityReference) queue.poll()) != null) values.remove(expired);
        }
    }

    private static final class IdentityReference extends WeakReference<Object> {
        private final int hash;
        IdentityReference(Object value) { super(value); hash = System.identityHashCode(value); }
        IdentityReference(Object value, ReferenceQueue<Object> queue) {
            super(value, queue);
            hash = System.identityHashCode(value);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof IdentityReference && get() != null
                    && get() == ((IdentityReference) other).get();
        }
    }
}
