package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class AutoRefresh implements IHook {
    static Method returnTopOrRefresh;
    static Method returnTopAndRefresh;
    static Method tryRefresh;

    @Override
    public String getName() {
        return "禁止自动刷新";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        try {
            Class<?> BaseTabChildFragment = classLoader.loadClass("com.zhihu.android.app.feed.ui.fragment.BaseTabChildFragment");
            try {
                returnTopOrRefresh = BaseTabChildFragment.getMethod("a", boolean.class);
                returnTopAndRefresh = BaseTabChildFragment.getMethod("b", boolean.class);
            } catch (NoSuchMethodException ignored) {
                try {
                    returnTopOrRefresh = BaseTabChildFragment.getMethod("returnTopOrRefresh", boolean.class);
                    returnTopAndRefresh = BaseTabChildFragment.getMethod("returnTopAndRefresh", boolean.class);
                } catch (NoSuchMethodException ignored2) {
                }
            }
        } catch (ClassNotFoundException ignored) {
        }

        try {
            Class<?> FeedAutoRefreshManager = classLoader.loadClass("com.zhihu.android.app.feed.util.FeedAutoRefreshManager");
            findTryRefreshMethod(FeedAutoRefreshManager);
        } catch (ClassNotFoundException ignored) {
            Helper.findClass(classLoader, "com.zhihu.android.app.feed.util.", 0, 1, this::findTryRefreshMethod);
        }

        // Keep the shared feed requests and success callbacks intact: initial loads,
        // manual refreshes and preload consumers all depend on their completion.
    }

    @Override
    public void hook() throws Throwable {
        if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_autorefresh", false)) {
            if (returnTopOrRefresh != null) {
                XposedBridge.hookMethod(returnTopOrRefresh, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if ((boolean) param.args[0]) {
                            param.setResult(1);
                        }
                    }
                });
            }
            if (returnTopAndRefresh != null) {
                XposedBridge.hookMethod(returnTopAndRefresh, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                    }
                });
            }
            if (tryRefresh != null) {
                XposedBridge.hookMethod(tryRefresh, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                    }
                });
            }
        }
    }

    private boolean findTryRefreshMethod(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getReturnType() == void.class && method.getParameterCount() == 4 &&
                    method.getParameterTypes()[0] == long.class &&
                    method.getParameterTypes()[1] == int.class &&
                    (method.getParameterTypes()[2].getModifiers() & Modifier.INTERFACE) != 0 &&
                    (method.getParameterTypes()[3].getModifiers() & Modifier.ABSTRACT) != 0
            ) {
                tryRefresh = method;
                return true;
            }
        }
        return false;
    }
}
