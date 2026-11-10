package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Commercial image overlays and the pendant left after a promotional video. */
public class FloatingAd implements IHook {
    static Method firstScreenDecision;
    static Method secondScreenDecision;
    static Method showPendant;
    static Method removePendant;
    static Field pendantContainer;

    @Override
    public String getName() {
        return "去悬浮广告";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        firstScreenDecision = classLoader.loadClass("com.zhihu.android.ad.special.AdFloat")
                .getDeclaredMethod("lambda$onHostResume$0$AdFloat", Boolean.class);
        secondScreenDecision = classLoader.loadClass("com.zhihu.android.ad.special.AdFloatSecond")
                .getDeclaredMethod("lambda$checkShowFloatSecond$0$AdFloatSecond", Boolean.class);
        Class<?> videoAd = classLoader.loadClass("com.zhihu.android.ad.special.AdFloatVideo");
        showPendant = videoAd.getDeclaredMethod("endVideo", int.class);
        removePendant = videoAd.getDeclaredMethod("remove");
        removePendant.setAccessible(true);
        pendantContainer = videoAd.getDeclaredField("adContainer");
        pendantContainer.setAccessible(true);
    }

    @Override
    public void hook() {
        XC_MethodHook skipImageDialog = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false)
                        && Helper.prefs.getBoolean("switch_floatad", true)) {
                    if (Boolean.TRUE.equals(param.args[0])) {
                        XposedBridge.log("[BetterZhiliao] floating image ad suppressed");
                    }
                    // Keep the host's no-image branch: it releases startup ad waiters.
                    param.args[0] = Boolean.FALSE;
                }
            }
        };
        XposedBridge.hookMethod(firstScreenDecision, skipImageDialog);
        XposedBridge.hookMethod(secondScreenDecision, skipImageDialog);
        XposedBridge.hookMethod(showPendant, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (Helper.prefs.getBoolean("switch_mainswitch", false)
                        && Helper.prefs.getBoolean("switch_floatad", true)
                        && pendantContainer.get(param.thisObject) != null) {
                    // Native cleanup cancels the ad timer and removes the image and close control.
                    removePendant.invoke(param.thisObject);
                    XposedBridge.log("[BetterZhiliao] floating pendant ad suppressed");
                    param.setResult(null);
                }
            }
        });
        XposedBridge.log("[BetterZhiliao] floating image ad hooks installed");
        XposedBridge.log("[BetterZhiliao] floating pendant ad hook installed");
    }
}
