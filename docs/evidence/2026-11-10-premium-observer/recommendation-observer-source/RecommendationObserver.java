package com.shatyuka.zhiliao.hooks;

import android.text.Html;
import com.shatyuka.zhiliao.Helper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Temporary metadata-only observation; no request/body capture and no result changes. */
public final class RecommendationObserver implements IHook {
    private static final int DISCOVERY_LIMIT = 64, EVENT_LIMIT = 256, BYTE_LIMIT = 512 * 1024;
    private static final String ENTRY = "zhiliao.rec.entry", SNAPSHOT = "zhiliao.rec.snapshot";
    private static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
            "com.zhihu.android.api.model.PaidContent", "com.zhihu.android.api.model.extension.MomentsPaidColumn",
            "com.zhihu.android.api.model.extension.MomentsManuscript", "com.zhihu.android.api.model.template.api.ApiTemplateRoot",
            "com.zhihu.android.api.model.MarketCard", "com.zhihu.android.ui.shared.sdui.model.Card", "com.zhihu.android.api.model.Answer"));
    private static final Set<String> ENUM_KEYS = new HashSet<>(Arrays.asList("type", "content_type", "business_type", "sub_type", "sdui_business_type", "vip_type", "catalog_type", "product_type"));
    private static final Set<String> FLAG_KEYS = new HashSet<>(Arrays.asList("is_paid", "is_free", "is_vip_free", "has_purchased", "is_purchased", "ownership", "is_vip", "is_svip"));
    private static final String[] LABELS = {"选自盐选专栏", "付费阅读内容仅盐选会员可评论"};
    private final Set<String> discovered = new HashSet<>(), unsupported = new HashSet<>();
    private int entries, events, bytes, nullResults;
    private Class<?> deserializer;
    @Override public String getName() { return "临时推荐元数据观察"; }
    @Override public void init(ClassLoader loader) throws Throwable {
        deserializer = loader.loadClass("com.zhihu.android.api.util.ZHObjectRegistryCenter$InnerDeserializer");
        bytes = (int) Math.min(BYTE_LIMIT, file().length());
    }
    @Override public void hook() throws Throwable {
        Set<XC_MethodHook.Unhook> low = XposedBridge.hookAllMethods(deserializer, "deserialize", new XC_MethodHook(XC_MethodHook.PRIORITY_LOWEST) {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    p.setObjectExtra("zhiliao.rec.low_ran", true);
                    if (p.hasThrowable() || stopped()) return;
                    Object model = p.getResult();
                    if (model == null) {
                        if (nullResults++ < 4) emit(new JSONObject().put("event", "lowest_result_null"));
                        return;
                    }
                    JSONObject metadata = metadata(model);
                    if (metadata == null) {
                        String name = model.getClass().getName();
                        if (unsupported.size() < 16 && unsupported.add(name)) emit(new JSONObject().put("event", "unsupported_model_class").put("model_class", name));
                        return;
                    }
                    String identity = metadata.optString("public_id", "no_id") + "#" + metadata.optString("json_discriminator")
                            + "#" + metadata.opt("exact_label_matches") + "#" + metadata.opt("business_type") + "#" + metadata.opt("extra_metadata");
                    String key = model.getClass().getName() + "#" + identity;
                    synchronized (discovered) {
                        if (discovered.contains(key)) return;
                        if (discovered.size() >= DISCOVERY_LIMIT) {
                            if (discovered.add("LIMIT")) emit(new JSONObject().put("event", "recommendation_probe_limit").put("reason", "discovery_count"));
                            return;
                        }
                        discovered.add(key);
                    }
                    Object entry = p.getObjectExtra(ENTRY);
                    metadata.put("event", "recommendation_prefilter_metadata").put("invocation", entry)
                            .put("callback_priority", XC_MethodHook.PRIORITY_LOWEST).put("scope", "global_deserializer_in_sample_window");
                    emit(metadata);
                    p.setObjectExtra(SNAPSHOT, new JSONObject().put("invocation", entry).put("model_class", model.getClass().getName())
                            .put("public_id", metadata.opt("public_id")).put("low_callback_sequence", metadata.optInt("sequence", -1)));
                } catch (Throwable error) { observerError("lowest", error); }
            }
        });
        Set<XC_MethodHook.Unhook> high = XposedBridge.hookAllMethods(deserializer, "deserialize", new XC_MethodHook(XC_MethodHook.PRIORITY_HIGHEST) {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                synchronized (RecommendationObserver.this) { p.setObjectExtra(ENTRY, ++entries); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (p.hasThrowable() || stopped()) return;
                    Object saved = p.getObjectExtra(SNAPSHOT);
                    if (saved instanceof JSONObject) {
                        JSONObject receipt = (JSONObject) saved;
                        Object result = p.getResult();
                        receipt.put("event", "recommendation_afterfilter_receipt").put("callback_priority", XC_MethodHook.PRIORITY_HIGHEST)
                                .put("low_callback_observed_first", true).put("result_removed_by_callbacks", result == null)
                                .put("result_class", result == null ? JSONObject.NULL : result.getClass().getName());
                        emit(receipt);
                    } else if (p.getObjectExtra("zhiliao.rec.low_ran") == null && p.getResult() != null
                            && SUPPORTED.contains(p.getResult().getClass().getName())) {
                        emit(new JSONObject().put("event", "recommendation_afterfilter_receipt")
                                .put("invocation", p.getObjectExtra(ENTRY)).put("low_callback_observed_first", false)
                                .put("result_class", p.getResult().getClass().getName()).put("callback_priority", XC_MethodHook.PRIORITY_HIGHEST));
                    }
                } catch (Throwable error) { observerError("highest", error); }
            }
        });
        emit(new JSONObject().put("event", "recommendation_metadata_ready").put("protocol", "prefilter_metadata_with_priority_receipt")
                .put("lowest_method_count", low.size()).put("highest_method_count", high.size())
                .put("registration_complete", !low.isEmpty() && !high.isEmpty())
                .put("lowest_priority", XC_MethodHook.PRIORITY_LOWEST).put("highest_priority", XC_MethodHook.PRIORITY_HIGHEST)
                .put("discovery_limit", DISCOVERY_LIMIT).put("event_limit", EVENT_LIMIT).put("byte_limit", BYTE_LIMIT)
                .put("raw_body_capture", false));
    }
    private JSONObject metadata(Object model) throws Throwable {
        String cls = model.getClass().getName(), type = null; Object id = null;
        JSONObject row = new JSONObject().put("model_class", cls);
        if (cls.equals("com.zhihu.android.api.model.PaidContent")) {
            type = "paid_content"; row.put("id_unavailable_in_declared_model", true); label(row, optional(model, "labelInfo"));
        } else if (cls.equals("com.zhihu.android.api.model.extension.MomentsPaidColumn")) {
            type = text(optional(model, "type")); if (type == null) type = "paid_column_moment"; id = optional(model, "id");
            row.put("model_family", "paid_column_moment");
        } else if (cls.equals("com.zhihu.android.api.model.extension.MomentsManuscript")) {
            type = text(optional(model, "type")); id = optional(model, "id");
            row.put("model_family", "manuscript_discriminator_required");
        } else if (cls.equals("com.zhihu.android.api.model.template.api.ApiTemplateRoot")) {
            Object extra = optional(model, "extra"); id = optional(extra, "id"); type = text(optional(extra, "type"));
            row.put("model_family", "legacy_template");
        } else if (cls.equals("com.zhihu.android.api.model.MarketCard")) {
            Object extra = optional(model, "extra"); id = optional(extra, "id"); type = text(optional(extra, "type"));
            row.put("model_family", "membership_promotion_not_paid_answer"); safeEnum(row, "card_type", optional(model, "cardType"));
        } else if (cls.equals("com.zhihu.android.ui.shared.sdui.model.Card")) {
            Object extra = optional(model, "extra"); id = optional(extra, "contentId"); type = text(optional(extra, "contentType"));
            row.put("model_family", "sdui_card");
            JSONObject fields = new JSONObject(); int[] budget = {0, 0};
            mapSummary(optional(extra, "businessExtJson"), "business_ext_map", 0, fields, budget);
            mapSummary(optional(extra, "customInfo"), "custom_info", 0, fields, budget);
            mapSummary(optional(extra, "scm"), "scm", 0, fields, budget);
            row.put("extra_metadata", fields).put("map_probe_capped", budget[0] >= 128 || budget[1] != 0);
            JSONArray matches = new JSONArray(); int[] elementBudget = {0, 0}; scanElements(model, matches, elementBudget, 0);
            row.put("exact_label_matches", matches).put("element_probe_capped", elementBudget[0] >= 256 || elementBudget[1] != 0);
        } else if (cls.equals("com.zhihu.android.api.model.Answer")) {
            type = "answer"; id = optional(model, "id"); label(row, optional(model, "labelInfo"));
            safeEnum(row, "business_type", optional(model, "businessType"));
        } else return null;
        safeEnum(row, "json_discriminator", type);
        if (id != null && String.valueOf(id).matches("[A-Za-z0-9_-]{1,80}")) row.put("public_id", String.valueOf(id));
        else row.put("public_id_present", false);
        return row;
    }
    private void mapSummary(Object node, String path, int depth, JSONObject result, int[] budget) throws Throwable {
        if (node == null) { result.put(path, JSONObject.NULL); return; }
        if (budget[0]++ >= 128) return;
        if (depth > 3) { budget[1] = 1; result.put(path + ".probe_capped", true); return; }
        if (node instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) node; result.put(path + ".field_count", map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (budget[0] >= 128) break;
                String key = String.valueOf(entry.getKey());
                if (key.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) mapSummary(entry.getValue(), path + "." + key, depth + 1, result, budget);
            }
        } else if (node.getClass().getName().startsWith("com.fasterxml.jackson.")) {
            if ((boolean) call(node, "isObject")) {
                result.put(path + ".kind", "object"); Iterator<?> keys = (Iterator<?>) call(node, "fieldNames");
                while (keys.hasNext() && budget[0] < 128) {
                    String key = String.valueOf(keys.next());
                    if (key.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) mapSummary(call(node, "get", key), path + "." + key, depth + 1, result, budget);
                }
            } else if ((boolean) call(node, "isArray")) result.put(path + ".array_count", call(node, "size"));
            else metadataScalar(result, path, (String) call(node, "asText"));
        } else metadataScalar(result, path, String.valueOf(node));
    }
    private static void metadataScalar(JSONObject result, String path, String value) throws Throwable {
        String key = path.substring(path.lastIndexOf('.') + 1);
        if (ENUM_KEYS.contains(key)) safeEnum(result, path, value);
        else if (FLAG_KEYS.contains(key) && value.matches("true|false|null|0|1")) result.put(path, value);
        else result.put(path + ".kind", "uninspected_scalar");
    }
    private void scanElements(Object element, JSONArray matches, int[] budget, int depth) throws Throwable {
        if (element == null) return;
        if (budget[0]++ >= 256 || depth > 8) { budget[1] = 1; return; }
        String cls = element.getClass().getName();
        if (cls.endsWith(".TextElement") || cls.endsWith(".HtmlTextElement")) {
            Object value = optional(element, "text");
            if (value instanceof String && ((String) value).length() <= 16384) {
                String plain = Html.fromHtml((String) value, Html.FROM_HTML_MODE_LEGACY).toString();
                for (int i = 0; i < LABELS.length; i++) if (plain.contains(LABELS[i])) {
                    if (matches.length() < 8) matches.put(i); else budget[1] = 1;
                }
            } else if (value instanceof String) budget[1] = 1;
        }
        Object children = optional(element, "elements");
        if (children instanceof Iterable) for (Object child : (Iterable<?>) children) {
            if (budget[0] >= 256) break; scanElements(child, matches, budget, depth + 1);
        }
    }
    private static void label(JSONObject row, Object label) throws Throwable {
        JSONArray ids = new JSONArray(); Object raw = optional(label, "text");
        if (raw instanceof String) for (int i = 0; i < LABELS.length; i++) if (((String) raw).contains(LABELS[i])) ids.put(i);
        row.put("exact_label_matches", ids);
    }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static void safeEnum(JSONObject row, String key, Object value) throws Throwable {
        if (value == null) row.put(key, JSONObject.NULL);
        else if (String.valueOf(value).matches("[A-Za-z0-9_.:-]{1,80}")) row.put(key, String.valueOf(value));
        else row.put(key + "_recognized", false);
    }
    private static Object optional(Object object, String name) throws Throwable {
        if (object == null) return null;
        Class<?> cls = object.getClass();
        while (cls != null) {
            try { Field field = cls.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException missing) { cls = cls.getSuperclass(); }
        }
        return null;
    }
    private static Object call(Object target, String name, Object... args) { return XposedHelpers.callMethod(target, name, args); }
    private boolean stopped() { return events >= EVENT_LIMIT || bytes >= BYTE_LIMIT; }
    private static File file() { return new File(Helper.context.getFilesDir(), ".zhiliao-recommendation-metadata.jsonl"); }
    private void observerError(String phase, Throwable error) {
        try { emit(new JSONObject().put("event", "recommendation_observer_error").put("phase", phase).put("exception_class", error.getClass().getName())); }
        catch (Throwable ignored) { /* An observer must not fail application callbacks. */ }
    }
    private synchronized void emit(JSONObject row) throws Throwable {
        if (stopped()) return;
        if (events == EVENT_LIMIT - 1) row = new JSONObject().put("event", "recommendation_probe_limit").put("reason", "event_count");
        row.put("sequence", events).put("elapsed_ms", android.os.SystemClock.elapsedRealtime());
        byte[] data = (row.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes + data.length > BYTE_LIMIT - 128) {
            data = "{\"event\":\"recommendation_probe_limit\",\"reason\":\"log_bytes\"}\n".getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(file(), true)) { out.write(data); }
            bytes = BYTE_LIMIT; return;
        }
        try (FileOutputStream out = new FileOutputStream(file(), true)) { out.write(data); }
        events++; bytes += data.length;
    }
}
