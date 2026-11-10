package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Commercial transparent-video promotion shown over the home page after launch. */
public class PopupAd implements IHook {
    static Method startAlphaVideo;
    static Field config;
    static Field advert;
    static Method finishStartupAd;

    @Override
    public String getName() {
        return "去启动弹窗广告";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Class<?> videoAd = classLoader.loadClass("com.zhihu.android.ad.special.AdFloatVideo");
        startAlphaVideo = videoAd.getDeclaredMethod("startAlphaVideo");
        config = XposedHelpers.findField(videoAd, "config");
        advert = classLoader.loadClass("com.zhihu.android.ad.special.c.a").getDeclaredField("a");
        advert.setAccessible(true);
        finishStartupAd = classLoader.loadClass("com.zhihu.android.ad.utils.d")
                .getDeclaredMethod("a", int.class);
        finishStartupAd.setAccessible(true);
    }

    @Override
    public void hook() {
        XposedBridge.hookMethod(startAlphaVideo, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (Helper.prefs.getBoolean("switch_mainswitch", false)
                        && Helper.prefs.getBoolean("switch_popupad", true)) {
                    Object nativeConfig = config.get(param.thisObject);
                    // Preserve the host's missing-ad branch, which sends startup completion.
                    if (nativeConfig == null || advert.get(nativeConfig) == null) return;
                    // The native one-shot completion releases pending feed startup work.
                    finishStartupAd.invoke(null, 0);
                    XposedBridge.log("[BetterZhiliao] startup video popup ad suppressed");
                    param.setResult(null);
                }
            }
        });
        XposedBridge.log("[BetterZhiliao] startup video popup ad hook installed");
    }
}
