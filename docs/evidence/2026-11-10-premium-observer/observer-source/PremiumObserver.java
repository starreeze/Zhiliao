package com.shatyuka.zhiliao.hooks;

import android.text.Html;
import com.shatyuka.zhiliao.Helper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Temporary observer only. Never changes arguments, results, requests or callbacks. */
public final class PremiumObserver implements IHook {
    private static final int PEEK_BYTES = 2 * 1024 * 1024;
    private static final int MAX_EVENTS = 120, MAX_LOG_BYTES = 512 * 1024;
    private static final int SEGMENT_LIMIT = 4096, SCAN_NODE_LIMIT = 65536, SCAN_CHAR_LIMIT = 2 * 1024 * 1024;
    private static final Set<String> IDS = new HashSet<>();
    private static final Set<String> DISCOVERED = new HashSet<>();
    private static final WeakHashMap<Object, Boolean> SEEN = new WeakHashMap<>();
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "id", "type", "node_type", "business_type", "sub_type", "render_mode", "richType", "cutout_min_height", "cutout_max_height",
            "comment_config", "can_reply", "place_holder", "place_holder_strategy",
            "structured_content", "segments", "content", "text", "html", "body", "value",
            "nodes", "elements", "children", "paragraphs", "blocks", "spans", "payload", "contents", "list",
            "paragraph", "heading", "blockquote", "reference_block", "code_block", "list_node", "items", "table", "cells", "card", "card_type",
            "data", "paging", "next", "is_end", "third_business",
            "is_paid", "is_preview", "is_lock", "is_free", "authentication_result",
            "is_truncated", "truncate_ratio", "has_purchased", "paid_info", "label_info",
            "can_comment", "comment_permission", "comment_status", "text_length", "length"));
    private static final Set<String> FLAGS = new HashSet<>(Arrays.asList(
            "can_reply", "is_paid", "is_preview", "is_lock", "is_free", "authentication_result",
            "is_truncated", "truncate_ratio", "has_purchased", "can_comment", "is_end", "render_mode", "cutout_min_height", "cutout_max_height"));
    private static final Set<String> ENUMS = new HashSet<>(Arrays.asList(
            "type", "node_type", "card_type", "richType", "business_type", "sub_type", "comment_permission", "place_holder_strategy"));
    private static final String[] GATES = {"精彩内容待解锁", "看广告免费解锁", "付费阅读内容仅盐选会员可评论", "选自盐选专栏"};
    private ClassLoader loader;
    private String anchor = "";
    private boolean privateCaptureEnabled;
    private volatile int events, bytes;
    private int installedHooks, missingHooks;

    @Override public String getName() { return "临时正文结构观察"; }

    @Override public void init(ClassLoader classLoader) throws Throwable {
        loader = classLoader;
        privateCaptureEnabled = file(".zhiliao-observer-capture-once").exists();
        bytes = (int) Math.min(MAX_LOG_BYTES, file(".zhiliao-premium-observer.jsonl").length());
        IDS.add("1977823141666501380");
        File ids = file(".zhiliao-observer-ids");
        if (ids.exists()) {
            String data = new String(Files.readAllBytes(ids.toPath()), StandardCharsets.UTF_8);
            if (data.length() > 512) throw new IllegalArgumentException("observer ID file too large");
            for (String id : data.split("\\s+")) if (id.matches("[0-9]{1,22}") && IDS.size() < 8) IDS.add(id);
        }
        File anchorFile = file(".zhiliao-observer-anchor");
        if (anchorFile.exists()) {
            anchor = new String(Files.readAllBytes(anchorFile.toPath()), StandardCharsets.UTF_8).trim();
            if (anchor.length() > 256) throw new IllegalArgumentException("observer anchor too large");
        }
    }

    @Override public void hook() throws Throwable {
        install("okhttp3.internal.http.RealInterceptorChain", "proceed", "network", true);
        install("com.zhihu.android.service.short_container_service.dataflow.repo.c.c", "a", "short_parser", false);
        install("com.zhihu.android.service.short_container_service.dataflow.repo.b.a", "a", "short_render", false);
        install("com.zhihu.android.service.short_container_service.dataflow.repo.c", "a", "expanded_continuation", false);
        install("com.zhihu.android.question.api.model.AnswerListWrapper", "setupAnswerData", "answer_list", false);
        install("com.zhihu.android.comment.delegate.e", "a", "comment_config", false);
        install("com.zhihu.android.api.util.ZHObjectRegistryCenter$InnerDeserializer", "deserialize", "legacy", false);
        emit(new JSONObject().put("event", "ready").put("fixture_ids", new JSONArray(IDS))
                .put("registration_status", missingHooks == 0 ? "all_requested_methods_registered" : "partial")
                .put("installed_hook_groups", installedHooks).put("missing_hook_groups", missingHooks)
                .put("metadata_discovery_limit", 32)
                .put("segment_aggregate_limit", SEGMENT_LIMIT).put("segment_scan_node_limit", SCAN_NODE_LIMIT)
                .put("segment_scan_char_limit", SCAN_CHAR_LIMIT)
                .put("private_capture_protocol", "exact_selected_detail_once").put("private_capture_enabled", privateCaptureEnabled)
                .put("peek_limit_bytes", PEEK_BYTES).put("event_limit", MAX_EVENTS)
                .put("log_limit_bytes", MAX_LOG_BYTES).put("anchor_enabled", !anchor.isEmpty()));
    }

    private void install(String name, String method, String kind, boolean network) throws Throwable {
        Class<?> cls;
        try { cls = loader.loadClass(name); }
        catch (ClassNotFoundException e) {
            missingHooks++;
            emit(new JSONObject().put("event", "hook_unavailable").put("kind", kind));
            return;
        }
        Set<XC_MethodHook.Unhook> registered = XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                // Diagnostic failures must never change application/network completion.
                try {
                    if (events >= MAX_EVENTS || bytes >= MAX_LOG_BYTES || p.hasThrowable()) return;
                    if (network) observeNetwork(p.getResult());
                    else if (kind.equals("short_parser") && p.args.length == 1
                            && p.args[0] != null && p.args[0].getClass().getName().startsWith("com.fasterxml.jackson.")) {
                        Object raw = p.args[0];
                        String id = nodeId(raw);
                        if (p.getResult() != null) discover(p.getResult(), null, id,
                                String.valueOf(optional(p.getResult(), "contentType")));
                        if (IDS.contains(id)) {
                            emit(summary("short_parser_input", raw).put("answer_id", id));
                            observeShortModel(p.getResult(), id);
                        }
                    } else if (kind.equals("expanded_continuation") && p.args.length == 3 && p.args[0] != null
                            && p.args[0].getClass().getName().equals("com.zhihu.android.service.short_container_service.dataflow.model.ShortContent")
                            && p.args[1] != null && p.args[1].getClass().getName().startsWith("com.fasterxml.jackson.")
                            && p.args[2] != null && p.args[2].getClass().getName().equals("com.zhihu.android.service.short_container_service.a.a")) {
                        String id = String.valueOf(call(p.args[0], "getContentId"));
                        if (IDS.contains(id)) {
                            JSONObject r = summary("expanded_continuation", p.args[1]).put("answer_id", id);
                            if (p.getResult() instanceof java.util.List) r.put("render_aggregate", renderAggregate((java.util.List<?>) p.getResult()));
                            emit(r); observeShortModel(p.args[0], id);
                        }
                    } else if (kind.equals("short_render") && p.args.length == 3 && p.args[0] != null
                            && p.args[0].getClass().getName().equals("com.zhihu.android.service.short_container_service.dataflow.model.ShortContent")
                            && p.args[1] instanceof Boolean && p.args[2] instanceof Boolean) {
                        String id = String.valueOf(call(p.args[0], "getContentId"));
                        if (IDS.contains(id)) {
                            JSONObject r = new JSONObject().put("event", "render_mapping").put("answer_id", id)
                                    .put("cutout_requested", p.args[1]).put("render_arg2", p.args[2]);
                            if (p.getResult() instanceof java.util.List) {
                                java.util.List<?> list = (java.util.List<?>) p.getResult();
                                JSONArray types = new JSONArray();
                                for (Object item : list.subList(0, Math.min(40, list.size())))
                                    types.put(item == null ? "null" : item.getClass().getName());
                                r.put("render_node_count", list.size()).put("node_classes", types).put("node_probe_capped", list.size() > 40);
                                r.put("render_aggregate", renderAggregate(list));
                            }
                            emit(r); observeShortModel(p.args[0], id);
                        }
                    } else if (kind.equals("answer_list") && p.args.length == 2) {
                        Object list = XposedHelpers.callStaticMethod(loader.loadClass("com.zhihu.android.api.model.IAnswerListKt"), "data", p.args[0]);
                        if (list instanceof java.util.List) {
                            java.util.List<?> items = (java.util.List<?>) list;
                            JSONArray fixtures = new JSONArray();
                            for (Object item : items.subList(0, Math.min(80, items.size()))) {
                                if (item == null) continue;
                                if (item.getClass().getName().equals("com.zhihu.android.api.model.MixShortCardTargetWrapper")) item = call(item, "getTarget");
                                if (item != null && item.getClass().getName().equals("com.zhihu.android.api.model.Answer")) {
                                    String id = String.valueOf(field(item, "id"));
                                    if (IDS.contains(id)) fixtures.put(id);
                                }
                            }
                            if (fixtures.length() > 0) emit(new JSONObject().put("event", "question_answer_list")
                                    .put("fixture_ids", fixtures).put("list_size", items.size()).put("list_probe_capped", items.size() > 80));
                        }
                    } else if (kind.equals("comment_config") && p.args.length == 1 && p.args[0] != null
                            && p.args[0].getClass().getName().equals("com.zhihu.android.comment.a.a")) {
                        Object config = p.args[0];
                        JSONObject record = new JSONObject().put("event", "comment_hint")
                                .put("can_reply", field(config, "canReply"));
                        addText(record, String.valueOf(field(config, "placeHolder")));
                        emit(record.put("association", "UI chronology required; config model has no content ID"));
                    } else if (kind.equals("legacy")) observeLegacy(p.getResult());
                } catch (Throwable e) {
                    try { emit(new JSONObject().put("event", "observer_error").put("kind", kind)
                            .put("exception_class", e.getClass().getName())); }
                    catch (Throwable ignored) { /* Do not interfere with the host on logging failures. */ }
                }
            }
        });
        if (registered.isEmpty()) {
            missingHooks++;
            emit(new JSONObject().put("event", "hook_unavailable").put("kind", kind).put("reason", "method_missing"));
            return;
        }
        installedHooks++;
        emit(new JSONObject().put("event", "hook_installed").put("kind", kind));
    }

    private void observeNetwork(Object response) throws Throwable {
        if (response == null) return;
        Object request = call(response, "request"), url = call(request, "url");
        String path = (String) call(url, "encodedPath");
        String category = null, requestFixture = null;
        for (String id : IDS) {
            if (path.equals("/answers/v2/" + id)) { category = "answer_detail"; requestFixture = id; }
            if (path.equals("/comment_v5/answers/" + id + "/config")) { category = "comment_config"; requestFixture = id; }
        }
        if (path.equals("/next-render") || path.equals("/simple-next-render")) category = "answer_stream";
        if (category == null) return;
        Object body = call(response, "body");
        synchronized (SEEN) { if (SEEN.put(body, true) != null) return; }
        Object peek = call(response, "peekBody", (long) PEEK_BYTES);
        byte[] data = (byte[]) call(peek, "bytes");
        JSONObject probe = new JSONObject().put("event", "response_probe").put("endpoint", category)
                .put("requested_fixture_id", requestFixture)
                .put("status", call(response, "code")).put("peek_bytes", data.length)
                .put("probe_capped", data.length >= PEEK_BYTES)
                .put("declared_body_length", call(body, "contentLength"));
        emit(probe);
        if (data.length >= PEEK_BYTES) return; // Incomplete probe is not a server-truncation observation.
        String json = new String(data, StandardCharsets.UTF_8);
        boolean selected = category.equals("comment_config");
        for (String id : IDS) selected |= json.contains(id);
        if (!selected) return;
        Object mapper = XposedHelpers.callStaticMethod(loader.loadClass("com.zhihu.android.api.util.j"), "a");
        Object tree = call(mapper, "readTree", json);
        captureSelectedDetail(tree, category, requestFixture, (int) call(response, "code"), body);
        JSONObject record = summary("response_structure", tree).put("endpoint", category)
                .put("requested_fixture_id", requestFixture)
                .put("status", call(response, "code")).put("peek_bytes", data.length)
                .put("probe_capped", data.length >= PEEK_BYTES)
                .put("declared_body_length", call(body, "contentLength"))
                .put("network_response_present", call(response, "networkResponse") != null)
                .put("cache_response_present", call(response, "cacheResponse") != null);
        emit(record);
    }

    /** Explicit temporary one-shot capture; no headers, unrelated IDs or raw data in JSONL. */
    private synchronized void captureSelectedDetail(Object tree, String category, String requestedId, int status, Object body) throws Throwable {
        String selectedId = "1977823141666501380";
        if (!privateCaptureEnabled || !category.equals("answer_detail") || status != 200
                || !selectedId.equals(requestedId) || !IDS.contains(selectedId)
                || !(boolean) call(tree, "isObject") || !selectedId.equals(nodeId(tree))) return;
        Object media = call(body, "contentType");
        if (media == null || !String.valueOf(media).toLowerCase(java.util.Locale.ROOT).contains("json")) return;
        File target = file(".zhiliao-observer-response.json");
        if (target.exists()) return;
        byte[] raw = String.valueOf(tree).getBytes(StandardCharsets.UTF_8);
        if (raw.length >= PEEK_BYTES) return;
        if (!target.createNewFile()) return;
        android.system.Os.chmod(target.getAbsolutePath(), 0600);
        try (FileOutputStream out = new FileOutputStream(target, false)) { out.write(raw); }
        privateCaptureEnabled = false;
        emit(new JSONObject().put("event", "private_capture_saved").put("answer_id", selectedId)
                .put("status", status).put("bytes", raw.length));
    }

    private void observeShortModel(Object model, String id) throws Throwable {
        if (model == null) return;
        JSONObject r = new JSONObject().put("event", "short_model").put("answer_id", id);
        safeEnum(r, "content_type", call(model, "getContentType"));
        safeEnum(r, "business_type", call(model, "getBusinessType"));
        safeEnum(r, "sub_type", call(model, "getSubType"));
        r.put("cutout_min_height", call(model, "getCutoutMinHeight"));
        r.put("cutout_max_height", call(model, "getCutoutMaxHeight"));
        Object content = call(model, "getContentJsonNode");
        if (content != null) r.put("received_segments_aggregate", segmentAggregate(content));
        Object config = call(model, "getCommentStrategy");
        if (config != null) {
            r.put("can_reply", call(config, "getCanReply"));
            JSONObject hint = new JSONObject();
            addText(hint, (String) call(config, "getPlaceHolder"));
            r.put("comment_hint", hint);
        }
        Object wrapper = call(model, "getWrapper");
        if (wrapper != null) {
            r.put("expanded", field(wrapper, "isExpanded"));
            r.put("expanded_paging_present", field(wrapper, "expandedContentNodePaging") != null);
            r.put("expanded_tmp_paging_present", field(wrapper, "expandedContentNodeTmpPaging") != null);
            addPaging(r, "expanded_paging", field(wrapper, "expandedContentNodePaging"));
            addPaging(r, "expanded_tmp_paging", field(wrapper, "expandedContentNodeTmpPaging"));
            Object cutout = field(wrapper, "cutoutNode");
            r.put("cutout_node_present", cutout != null);
            if (cutout != null) {
                r.put("cutout_index", call(cutout, "getIndex")).put("cutout_show_height", call(cutout, "getShowHeight"))
                        .put("cutout_show_max_line", call(cutout, "getShowMaxLine")).put("cutout_show_mask", call(cutout, "getShowMask"));
            }
            Object list = field(wrapper, "contentNodeList");
            if (list instanceof java.util.List) r.put("render_node_count", ((java.util.List<?>) list).size());
        }
        emit(r);
    }

    private static void addPaging(JSONObject record, String key, Object paging) throws Throwable {
        if (paging == null) return;
        Object next = call(paging, "getNext");
        record.put(key + "_is_end", field(paging, "isEnd"));
        record.put(key + "_next_present", next != null && !String.valueOf(next).isEmpty());
    }

    private void observeLegacy(Object model) throws Throwable {
        if (model == null) return;
        String cls = model.getClass().getName();
        if (cls.equals("com.zhihu.android.api.model.Answer")) {
            String id = String.valueOf(field(model, "id"));
            discover(model, null, id, "answer");
            if (!IDS.contains(id)) return;
            JSONObject r = new JSONObject().put("event", "legacy_answer").put("answer_id", id)
                    .put("paid_info_present", field(model, "answerPaidInfo") != null);
            safeEnum(r, "business_type", field(model, "businessType"));
            safeEnum(r, "comment_permission", field(model, "commentPermission"));
            Object label = field(model, "labelInfo");
            if (label != null) { JSONObject l = new JSONObject(); addText(l, (String) field(label, "text")); r.put("label", l); }
            emit(r);
        } else if (cls.startsWith("com.zhihu.android.") && cls.contains("Card")) {
            Object extra = optional(model, "extra");
            if (extra == null) return;
            Object contentId = optional(extra, "contentId");
            if (contentId == null) contentId = optional(extra, "content_id");
            String id = String.valueOf(contentId);
            discover(model, extra, id, String.valueOf(optional(extra, "contentType")));
            if (!IDS.contains(id)) return;
            JSONObject r = new JSONObject().put("event", "feed_card").put("answer_id", id);
            safeEnum(r, "content_type", field(extra, "contentType"));
            emit(r);
        }
    }

    /** Discovery emits selected metadata only, never passes unknown IDs to body probes. */
    private void discover(Object model, Object extra, String id, String type) throws Throwable {
        if (!id.matches("[0-9]{1,22}")) return;
        synchronized (DISCOVERED) {
            if (DISCOVERED.size() >= 32 || !DISCOVERED.add(model.getClass().getName() + "#" + id)) return;
        }
        JSONObject r = new JSONObject().put("event", "metadata_discovery").put("answer_id", id)
                .put("model_class", model.getClass().getName());
        safeEnum(r, "content_type", type);
        safeEnum(r, "business_type", optional(model, "businessType"));
        safeEnum(r, "sub_type", optional(model, "subType"));
        Object ext = extra == null ? null : optional(extra, "businessExtJson");
        if (ext == null && extra != null) ext = optional(extra, "business_ext_map");
        Object info = ext == null ? null : optional(ext, "content_info");
        if (info != null) {
            safeEnum(r, "feed_sub_type", optional(info, "sub_type"));
            safeEnum(r, "feed_business_type", optional(info, "business_type"));
        }
        Object label = optional(model, "labelInfo");
        if (label != null) { JSONObject l = new JSONObject(); addKnownGates(l, String.valueOf(optional(label, "text"))); r.put("known_label", l); }
        Object config = optional(model, "commentStrategy");
        if (config != null) {
            JSONObject hint = new JSONObject(); addKnownGates(hint, String.valueOf(optional(config, "placeHolder")));
            r.put("known_comment_hint", hint);
        }
        emit(r);
    }

    private JSONObject summary(String event, Object node) throws Throwable {
        JSONArray rows = new JSONArray(); int[] budget = {0};
        walk(node, "$", "", 0, rows, budget);
        return new JSONObject().put("event", event).put("shape", rows)
                .put("shape_probe_capped", budget[0] >= 240).put("received_segments_aggregate", segmentAggregate(node));
    }

    /** Independent traversal; completeness refers to these ceilings, not to server story completeness. */
    private JSONObject segmentAggregate(Object root) throws Throwable {
        Object structured = call(root, "get", "structured_content");
        Object segments = call(structured == null ? root : structured, "get", "segments");
        JSONObject result = new JSONObject().put("aggregate_kind", "selected_segments");
        if (segments == null || !(boolean) call(segments, "isArray")) return result.put("segments_present", false);
        int count = (int) call(segments, "size"), inspected = 0;
        Scan scan = new Scan();
        Map<String, JSONObject> types = new LinkedHashMap<>();
        java.util.ArrayList<JSONObject> tail = new java.util.ArrayList<>();
        JSONArray gates = new JSONArray();
        int unknownSegments = 0, paidTailCount = 0;
        for (int i = 0; i < Math.min(count, SEGMENT_LIMIT); i++) {
            Object segment = call(segments, "get", i);
            String type = enumNode(segment, "type");
            Object card = call(segment, "get", "card");
            String cardType = enumNode(card == null ? segment : card, "card_type");
            String category = type.equals("card") ? "card:" + cardType : type;
            boolean story = Arrays.asList("paragraph", "heading", "blockquote", "reference_block", "code_block", "list_node", "table", "text").contains(type);
            boolean paidTail = cardType.equals("paid_column_tail_truncate");
            if (paidTail) paidTailCount++;
            if (!story && !paidTail) unknownSegments++;
            int before = scan.story.length(), beforeStrings = scan.stringChars;
            boolean[] matched = new boolean[GATES.length];
            scanSegment(segment, "", 0, story, scan, matched);
            int chars = scan.story.length() - before;
            JSONObject row = new JSONObject().put("index", i).put("type", category)
                    .put("story_chars", chars).put("all_string_chars", scan.stringChars - beforeStrings)
                    .put("story_type_recognized", story).put("paid_column_tail", paidTail);
            JSONArray known = new JSONArray();
            for (int g = 0; g < matched.length; g++) if (matched[g]) known.put(g);
            row.put("known_gate_ids", known);
            if (paidTail || known.length() > 0) {
                if (gates.length() < 32) gates.put(new JSONObject(row.toString()).put("story_chars_before", before));
                else scan.gatePositionsCapped = true;
            }
            tail.add(row); if (tail.size() > 12) tail.remove(0);
            String bucket = category;
            if (!types.containsKey(bucket) && types.size() >= 48) { bucket = "other_types"; scan.typeNamesCapped = true; }
            JSONObject stats = types.get(bucket);
            if (stats == null) { stats = new JSONObject().put("count", 0).put("story_chars", 0).put("all_string_chars", 0); types.put(bucket, stats); }
            stats.put("count", stats.getInt("count") + 1).put("story_chars", stats.getInt("story_chars") + chars)
                    .put("all_string_chars", stats.getInt("all_string_chars") + scan.stringChars - beforeStrings);
            inspected++;
            if (scan.capped) break;
        }
        for (int i = 0; i < gates.length(); i++) {
            JSONObject gate = gates.getJSONObject(i);
            gate.put("story_chars_after", scan.story.length() - gate.getInt("story_chars_before") - gate.getInt("story_chars"));
        }
        result.put("segments_present", true).put("segment_count", count).put("segments_inspected", inspected)
                .put("scan_complete", inspected == count && !scan.capped).put("segment_probe_capped", count > SEGMENT_LIMIT)
                .put("traversal_probe_capped", scan.capped).put("scan_nodes", scan.nodes).put("all_string_chars", scan.stringChars)
                .put("story_chars", scan.story.length()).put("per_type", new JSONObject(types)).put("ordered_tail", new JSONArray(tail))
                .put("gate_positions", gates).put("gate_positions_probe_capped", scan.gatePositionsCapped)
                .put("type_names_probe_capped", scan.typeNamesCapped).put("non_story_or_unclassified_segments", unknownSegments)
                .put("paid_column_tail_count", paidTailCount).put("opaque_json_strings", scan.opaqueStrings)
                .put("string_field_names", new JSONArray(scan.fieldNames)).put("field_names_probe_capped", scan.fieldNamesCapped)
                .put("story_extraction", "recognized types; text/html/content/items/cells leaves; marks excluded; normalized leaves concatenated without separators; opaque strings not expanded");
        if (!anchor.isEmpty()) {
            JSONArray matches = new JSONArray(); int position = scan.story.indexOf(anchor), total = 0;
            while (position >= 0) {
                total++;
                if (matches.length() < 8) matches.put(new JSONObject().put("story_offset", position)
                        .put("story_chars_after", scan.story.length() - position - anchor.length()));
                position = scan.story.indexOf(anchor, position + Math.max(1, anchor.length()));
            }
            result.put("anchor_story_matches", total).put("anchor_positions", matches).put("anchor_positions_probe_capped", total > 8);
        }
        return result;
    }

    private static final class Scan {
        int nodes, stringChars, opaqueStrings;
        boolean capped, gatePositionsCapped, typeNamesCapped, fieldNamesCapped;
        final StringBuilder story = new StringBuilder();
        final Set<String> fieldNames = new java.util.LinkedHashSet<>();
    }

    private void scanSegment(Object node, String key, int depth, boolean story, Scan scan, boolean[] gates) throws Throwable {
        if (node == null || scan.capped) return;
        if (++scan.nodes > SCAN_NODE_LIMIT || depth > 20) { scan.capped = true; return; }
        if ((boolean) call(node, "isObject")) {
            Iterator<?> names = (Iterator<?>) call(node, "fieldNames");
            while (names.hasNext() && !scan.capped) {
                String name = (String) names.next();
                scanSegment(call(node, "get", name), name, depth + 1, story && !name.equals("marks"), scan, gates);
            }
        } else if ((boolean) call(node, "isArray")) {
            int size = (int) call(node, "size");
            for (int i = 0; i < size && !scan.capped; i++) scanSegment(call(node, "get", i), key, depth + 1, story, scan, gates);
        } else if ((boolean) call(node, "isTextual")) {
            String text = (String) call(node, "asText");
            if (scan.stringChars + text.length() > SCAN_CHAR_LIMIT) { scan.capped = true; return; }
            scan.stringChars += text.length();
            if (key.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
                if (scan.fieldNames.size() < 64) scan.fieldNames.add(key); else if (!scan.fieldNames.contains(key)) scan.fieldNamesCapped = true;
            }
            for (int i = 0; i < gates.length; i++) gates[i] |= text.contains(GATES[i]);
            if (text.startsWith("{") || text.startsWith("[")) scan.opaqueStrings++;
            if (story && Arrays.asList("text", "html", "content", "items", "cells").contains(key))
                scan.story.append(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString());
        }
    }

    private static String enumNode(Object node, String key) throws Throwable {
        if (node == null) return "absent";
        Object value = call(node, "get", key);
        if (value == null) return "absent";
        String text = (String) call(value, "asText");
        return text.matches("[A-Za-z0-9_.:-]{1,64}") ? text : "unrecognized";
    }

    private static JSONObject renderAggregate(java.util.List<?> list) throws Throwable {
        Map<String, Integer> classes = new LinkedHashMap<>(); JSONArray tail = new JSONArray();
        int count = Math.min(list.size(), SEGMENT_LIMIT); boolean classNamesCapped = false;
        for (int i = 0; i < count; i++) {
            Object item = list.get(i); String name = item == null ? "null" : item.getClass().getName();
            if (!classes.containsKey(name) && classes.size() >= 48) { name = "other_classes"; classNamesCapped = true; }
            classes.put(name, classes.containsKey(name) ? classes.get(name) + 1 : 1);
            if (i >= Math.max(0, count - 12)) tail.put(new JSONObject().put("index", i).put("class", name));
        }
        return new JSONObject().put("node_count", list.size()).put("nodes_inspected", count)
                .put("scan_complete", list.size() <= SEGMENT_LIMIT).put("node_probe_capped", list.size() > SEGMENT_LIMIT)
                .put("class_counts", new JSONObject(classes)).put("class_names_probe_capped", classNamesCapped).put("ordered_tail", tail);
    }

    private void walk(Object n, String path, String key, int depth, JSONArray rows, int[] budget) throws Throwable {
        if (n == null || budget[0]++ >= 240) return;
        JSONObject r = new JSONObject().put("path", path);
        if ((boolean) call(n, "isObject")) {
            r.put("kind", "object").put("field_count", call(n, "size")); rows.put(r);
            if (depth >= 10) { r.put("depth_probe_capped", true); return; }
            Iterator<?> names = (Iterator<?>) call(n, "fieldNames"); int skipped = 0;
            JSONArray skippedNames = new JSONArray();
            while (names.hasNext()) {
                String k = (String) names.next();
                if (KEYS.contains(k)) walk(call(n, "get", k), path + "." + k, k, depth + 1, rows, budget);
                else {
                    skipped++;
                    if (skippedNames.length() < 24 && k.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) skippedNames.put(k);
                }
            }
            r.put("uninspected_fields", skipped).put("uninspected_schema_names", skippedNames)
                    .put("schema_name_probe_capped", skipped > skippedNames.length());
        } else if ((boolean) call(n, "isArray")) {
            int size = (int) call(n, "size"); r.put("kind", "array").put("size", size).put("array_probe_capped", size > 40); rows.put(r);
            if (depth >= 10) { r.put("depth_probe_capped", true); return; }
            for (int i = 0; i < Math.min(size, 40); i++) walk(call(n, "get", i), path + "[" + i + "]", key, depth + 1, rows, budget);
        } else {
            r.put("kind", "scalar"); String value = (String) call(n, "asText");
            if (FLAGS.contains(key) && value.matches("(?:true|false|null|-?[0-9]+(?:\\.[0-9]+)?)")) r.put("flag", value);
            else if (ENUMS.contains(key)) safeEnum(r, "enum", value);
            else if (key.equals("id")) { if (IDS.contains(value)) r.put("fixture_id", value); }
            else if (key.equals("next")) r.put("next_present", !value.isEmpty() && !value.equals("null"));
            else addText(r, value);
            rows.put(r);
        }
    }

    private void addText(JSONObject r, String text) throws Throwable {
        if (text == null) { r.put("null", true); return; }
        r.put("chars", text.length());
        addKnownGates(r, text);
        if (!anchor.isEmpty()) {
            String plain = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString();
            int pos = plain.indexOf(anchor);
            r.put("anchor_match", pos >= 0);
            if (pos >= 0) r.put("chars_after_anchor", plain.length() - pos - anchor.length());
        }
    }

    private static void addKnownGates(JSONObject r, String text) throws Throwable {
        JSONArray gates = new JSONArray(); for (int i = 0; i < GATES.length; i++) if (text.contains(GATES[i])) gates.put(i);
        r.put("known_gate_ids", gates);
    }

    private static void safeEnum(JSONObject r, String key, Object value) throws Throwable {
        if (value == null) { r.put(key, JSONObject.NULL); return; }
        String text = String.valueOf(value);
        if (text.matches("[A-Za-z0-9_.:-]{1,64}")) r.put(key, text);
        else r.put(key + "_chars", text.length());
    }
    private String nodeId(Object n) throws Throwable { Object id = call(n, "get", "id"); return id == null ? "" : (String) call(id, "asText"); }
    private static Object call(Object o, String name, Object... args) { return XposedHelpers.callMethod(o, name, args); }
    private static Object optional(Object o, String name) throws Throwable {
        if (o instanceof java.util.Map) return ((java.util.Map<?, ?>) o).get(name);
        try { return field(o, name); }
        catch (NoSuchFieldException e) { return null; }
    }
    private static Object field(Object o, String name) throws Throwable {
        Class<?> c = o.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static File file(String name) { return new File(Helper.context.getFilesDir(), name); }
    private synchronized void emit(JSONObject r) throws Throwable {
        if (events >= MAX_EVENTS || bytes >= MAX_LOG_BYTES) return;
        if (events == MAX_EVENTS - 1) r = new JSONObject().put("event", "observer_limit").put("reason", "event_count");
        r.put("sequence", events).put("elapsed_ms", android.os.SystemClock.elapsedRealtime());
        byte[] data = (r.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes + data.length > MAX_LOG_BYTES - 128) {
            byte[] limit = "{\"event\":\"observer_limit\",\"reason\":\"log_bytes\"}\n".getBytes(StandardCharsets.UTF_8);
            if (bytes + limit.length <= MAX_LOG_BYTES)
                try (FileOutputStream out = new FileOutputStream(file(".zhiliao-premium-observer.jsonl"), true)) { out.write(limit); }
            bytes = MAX_LOG_BYTES;
            return;
        }
        try (FileOutputStream out = new FileOutputStream(file(".zhiliao-premium-observer.jsonl"), true)) { out.write(data); }
        events++; bytes += data.length;
    }
}
