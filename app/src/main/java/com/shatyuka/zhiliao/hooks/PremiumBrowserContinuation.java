package com.shatyuka.zhiliao.hooks;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Preserves native completion and real paging when the paid filter removes a whole page. */
final class PremiumBrowserContinuation {
    private static final String UI = "com.zhihu.android.feature.short_container_feature.ui.";
    private static final int MAX_AUTOMATIC_SKIPS = 3;
    private final WeakIdentityMap<Boolean> filteredEmpty = new WeakIdentityMap<>();
    private final WeakIdentityMap<Sequence> sequences = new WeakIdentityMap<>();
    private Host host;
    private boolean installed;
    private boolean reportedFailure;

    PremiumBrowserContinuation() { }

    // The host boundary lets JVM tests run the complete sequence without Android or a network.
    PremiumBrowserContinuation(Host host) { this.host = host; }

    boolean install(ClassLoader loader) {
        if (installed) return true;
        ArrayList<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            ReflectiveHost binding = new ReflectiveHost(loader);
            host = binding;
            for (int index = 0; index < binding.initial.length; index++) {
                final Field owner = binding.initialOwners[index];
                hooks.add(XposedBridge.hookMethod(binding.initial[index], new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (beforeInitial(owner.get(param.thisObject), param.args[0])) param.setResult(null);
                        } catch (ReflectiveOperationException | ClassCastException failure) {
                            unavailable(failure);
                        }
                    }
                }));
            }
            hooks.add(XposedBridge.hookMethod(binding.zeroPage, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    sequence(binding.zeroOwner.get(param.thisObject)).nativeCompletionDepth++;
                }
                @Override protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    Object vm = binding.zeroOwner.get(param.thisObject);
                    try {
                        if (!param.hasThrowable()) afterZeroPage(vm, binding.skipNextAfterZero.getBoolean(param.thisObject));
                    } catch (ReflectiveOperationException | ClassCastException failure) { unavailable(failure); }
                    finally { sequence(vm).nativeCompletionDepth--; }
                }
            }));
            hooks.add(XposedBridge.hookMethod(binding.loadMore, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    sequence(binding.loadOwner.get(param.thisObject)).nativeCompletionDepth++;
                }
                @Override protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    Object vm = binding.loadOwner.get(param.thisObject);
                    try {
                        if (!param.hasThrowable()) afterLoadMore(vm,
                                binding.requestPaging.get(param.thisObject), param.args[0]);
                    } catch (ReflectiveOperationException | ClassCastException failure) { unavailable(failure); }
                    finally { sequence(vm).nativeCompletionDepth--; }
                }
            }));
            hooks.add(XposedBridge.hookMethod(binding.request, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    beforeRequest(param.thisObject);
                }
            }));
            installed = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            host = null;
            unavailable(failure);
            return false;
        }
    }

    /** Called only after a nonempty source was filtered down to an empty collection. */
    void markFilteredEmpty(Object response) { filteredEmpty.put(response, Boolean.TRUE); }

    // Return true only when replacing the native first-page empty branch.
    boolean beforeInitial(Object viewModel, Object response) throws ReflectiveOperationException {
        Sequence sequence = sequence(viewModel);
        sequence.reset();
        if (!isFilteredEmpty(response)) return false;
        host.cacheInitial(viewModel, response);
        sequence.nativeCompletionDepth++;
        try {
            if (host.zeroPageReady(viewModel)) {
                host.emitInitial(viewModel, response);
                skip(viewModel, sequence, null, response);
            } else {
                // The original zero-page callback emits the cached list after rendering its target.
                sequence.deferred = new WeakReference<>(response);
            }
            return true;
        } finally { sequence.nativeCompletionDepth--; }
    }

    void afterZeroPage(Object viewModel, boolean skipNext) throws ReflectiveOperationException {
        Sequence sequence = sequences.get(viewModel);
        if (sequence == null || sequence.deferred == null || skipNext
                || !host.zeroPageReady(viewModel) || !host.nextRenderEnabled(viewModel)) return;
        Object response = sequence.deferred.get();
        sequence.deferred = null;
        if (isFilteredEmpty(response)) skip(viewModel, sequence, null, response);
    }

    /** Runs after the original callback releases its in-flight cursor and emits native completion. */
    void afterLoadMore(Object viewModel, Object requestedPaging, Object response)
            throws ReflectiveOperationException {
        Sequence sequence = sequence(viewModel);
        if (isFilteredEmpty(response)) {
            skip(viewModel, sequence, requestedPaging, response);
        } else {
            // An unfiltered empty response also ends our sequence; its native handling is untouched.
            sequence.reset();
        }
    }

    // The existing VM entry point remains usable after the automatic budget is exhausted.
    void beforeRequest(Object viewModel) {
        Sequence sequence = sequence(viewModel);
        if (sequence.automaticInvocationDepth == 0 && sequence.nativeCompletionDepth == 0) sequence.reset();
    }

    private boolean isFilteredEmpty(Object response) throws ReflectiveOperationException {
        return response != null && filteredEmpty.get(response) != null && host.empty(response);
    }

    private void skip(Object viewModel, Sequence sequence, Object requestedPaging, Object response)
            throws ReflectiveOperationException {
        Object paging = host.paging(response);
        if (paging == null || host.end(paging)) return;
        String next = host.next(paging);
        if (next == null || next.isEmpty() || sequence.automaticSkips >= MAX_AUTOMATIC_SKIPS) return;
        if (requestedPaging != null) {
            String requested = host.next(requestedPaging);
            if (next.equals(requested)) return;
            if (requested != null) sequence.seen.add(requested);
        }
        if (!sequence.seen.add(next)) return;
        sequence.automaticSkips++;
        sequence.automaticInvocationDepth++;
        try { host.request(viewModel, paging); }
        finally { sequence.automaticInvocationDepth--; }
    }

    private Sequence sequence(Object viewModel) {
        Sequence sequence = sequences.get(viewModel);
        if (sequence == null) {
            sequence = new Sequence();
            sequences.put(viewModel, sequence);
        }
        return sequence;
    }

    private void unavailable(Throwable failure) {
        if (reportedFailure) return;
        reportedFailure = true;
        XposedBridge.log("[BetterZhiliao] premium browser continuation unavailable: " + failure);
    }

    interface Host {
        boolean empty(Object response) throws ReflectiveOperationException;
        Object paging(Object response) throws ReflectiveOperationException;
        boolean end(Object paging) throws ReflectiveOperationException;
        String next(Object paging) throws ReflectiveOperationException;
        boolean zeroPageReady(Object viewModel) throws ReflectiveOperationException;
        boolean nextRenderEnabled(Object viewModel) throws ReflectiveOperationException;
        void cacheInitial(Object viewModel, Object response) throws ReflectiveOperationException;
        void emitInitial(Object viewModel, Object response) throws ReflectiveOperationException;
        void request(Object viewModel, Object paging) throws ReflectiveOperationException;
    }

    private static final class Sequence {
        final HashSet<String> seen = new HashSet<>();
        int automaticSkips;
        int automaticInvocationDepth;
        int nativeCompletionDepth;
        WeakReference<Object> deferred;
        void reset() {
            automaticSkips = 0;
            seen.clear();
            deferred = null;
        }
    }

    /** All version-specific members are resolved before any hook is installed. */
    private static final class ReflectiveHost implements Host {
        final Method[] initial = new Method[2];
        final Field[] initialOwners = new Field[2];
        final Method zeroPage, loadMore, request, getNext, setValue, nextDisabled;
        final Field zeroOwner, skipNextAfterZero, loadOwner, requestPaging, data, paging, isEnd, ready, cached, loading, events;
        final Constructor<?> initialEvent;

        ReflectiveHost(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> list = loader.loadClass("com.zhihu.android.api.model.ZHObjectList");
            Class<?> page = loader.loadClass("com.zhihu.android.api.model.Paging");
            Class<?> vm = loader.loadClass(UI + "b.b");
            String[] initialNames = {"b.b$b$1", "b.b$e"};
            for (int index = 0; index < initialNames.length; index++) {
                Class<?> callback = loader.loadClass(UI + initialNames[index]);
                initial[index] = method(callback, "a", void.class, list);
                initialOwners[index] = field(callback, "a", vm);
            }
            Class<?> zero = loader.loadClass(UI + "b.b$c");
            Class<?> more = loader.loadClass(UI + "b.b$i$1");
            zeroPage = method(zero, "a", void.class, List.class);
            zeroOwner = field(zero, "a", vm);
            skipNextAfterZero = field(zero, "d", boolean.class);
            loadMore = method(more, "a", void.class, list);
            loadOwner = field(more, "a", vm);
            requestPaging = field(more, "b", page);
            request = method(vm, "a", void.class, page);
            data = field(list, "data", List.class);
            paging = field(list, "paging", page);
            isEnd = field(page, "isEnd", boolean.class);
            getNext = method(page, "getNext", String.class);
            ready = field(vm, "g", boolean.class);
            cached = field(vm, "h", list);
            loading = field(vm, "i", boolean.class);
            events = field(vm, "c", loader.loadClass("androidx.lifecycle.ad"));
            nextDisabled = method(vm, "c", boolean.class);
            setValue = method(events.getType(), "setValue", void.class, Object.class);
            initialEvent = loader.loadClass(UI + "a.a$g").getDeclaredConstructor(list);
            initialEvent.setAccessible(true);
        }

        @Override public boolean empty(Object response) throws IllegalAccessException {
            List<?> items = (List<?>) data.get(response);
            return items != null && items.isEmpty();
        }
        @Override public Object paging(Object response) throws IllegalAccessException { return paging.get(response); }
        @Override public boolean end(Object page) throws IllegalAccessException { return isEnd.getBoolean(page); }
        @Override public String next(Object page) throws ReflectiveOperationException { return (String) getNext.invoke(page); }
        @Override public boolean zeroPageReady(Object vm) throws IllegalAccessException { return ready.getBoolean(vm); }
        @Override public boolean nextRenderEnabled(Object vm) throws ReflectiveOperationException {
            return !((Boolean) nextDisabled.invoke(vm));
        }
        @Override public void cacheInitial(Object vm, Object response) throws IllegalAccessException {
            cached.set(vm, response);
            loading.setBoolean(vm, false);
        }
        @Override public void emitInitial(Object vm, Object response) throws ReflectiveOperationException {
            setValue.invoke(events.get(vm), initialEvent.newInstance(response));
        }
        @Override public void request(Object vm, Object page) throws ReflectiveOperationException { request.invoke(vm, page); }
    }

    private static Method method(Class<?> type, String name, Class<?> returns, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                if (method.getReturnType() != returns) throw new NoSuchMethodException(type.getName() + "." + name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException inherited) { /* Search inherited LiveData methods. */ }
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
            } catch (NoSuchFieldException inherited) { /* Search inherited model fields. */ }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static final class WeakIdentityMap<T> {
        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
        private final Map<IdentityReference, T> values = new HashMap<>();
        synchronized T get(Object key) { clean(); return values.get(new IdentityReference(key)); }
        synchronized void put(Object key, T value) { clean(); values.put(new IdentityReference(key, queue), value); }
        private void clean() {
            IdentityReference expired;
            while ((expired = (IdentityReference) queue.poll()) != null) values.remove(expired);
        }
    }

    private static final class IdentityReference extends WeakReference<Object> {
        private final int hash;
        IdentityReference(Object object) { super(object); hash = System.identityHashCode(object); }
        IdentityReference(Object object, ReferenceQueue<Object> queue) {
            super(object, queue);
            hash = System.identityHashCode(object);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof IdentityReference && get() != null
                    && get() == ((IdentityReference) other).get();
        }
    }
}
