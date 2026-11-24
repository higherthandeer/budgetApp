package com.example.budgetapp.parsing;

import android.content.Context;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class TemplateCatalog {
    static final class Template {
        String id, provider, packageName, title, bodyPrefix, parserType;
        int version;
        Set<String> appVersions = new HashSet<>(), channels = new HashSet<>();
        Map<String, String> anchors = new HashMap<>();
    }

    final List<Template> templates;
    private final boolean synthetic;

    private TemplateCatalog(List<Template> templates, boolean synthetic) {
        this.templates = Collections.unmodifiableList(templates);
        this.synthetic = synthetic;
    }

    public int size() {
        return templates.size();
    }

    public static TemplateCatalog load(Context context) {
        try (InputStream stream = context.getAssets().open("notification_templates.json"); Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            StringBuilder text = new StringBuilder();
            int n;
            while ((n = reader.read(buffer)) != -1) text.append(buffer, 0, n);
            TemplateCatalog result = fromJson(text.toString());
            if (result.synthetic)
                throw new IllegalArgumentException("Synthetic templates are forbidden in production");
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Missing template catalog", e);
        }
    }

    public static TemplateCatalog fromJson(String text) {
        try {
            JSONObject root = new JSONObject(text);
            if (root.getInt("formatVersion") != 1)
                throw new IllegalArgumentException("Unsupported catalog version");
            List<Template> templates = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            JSONArray array = root.getJSONArray("templates");
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Template t = new Template();
                t.parserType = o.optString("parserType", "ANCHORED");
                if (!Arrays.asList("ANCHORED", "WECHAT_PAYMENT_SUMMARY", "ALIPAY_EXPENSE_REMINDER").contains(t.parserType))
                    throw new IllegalArgumentException("Unknown parser type");
                t.id = o.getString("id");
                t.version = o.getInt("version");
                t.provider = o.getString("provider");
                t.packageName = o.getString("packageName");
                t.title = o.optString("title", "");
                t.bodyPrefix = o.getString("bodyPrefix");
                if (t.id.isEmpty() || t.version < 1 || t.bodyPrefix.isEmpty() || !ids.add(t.id))
                    throw new IllegalArgumentException("Invalid template");
                if("ANCHORED".equals(t.parserType)){
                    JSONArray versions=o.getJSONArray("appVersions"),channels=o.getJSONArray("channels");
                    for(int j=0;j<versions.length();j++)t.appVersions.add(versions.getString(j));
                    for(int j=0;j<channels.length();j++)t.channels.add(channels.getString(j));
                    if(t.title.isEmpty()||t.appVersions.isEmpty()||t.channels.isEmpty()||t.channels.contains(""))throw new IllegalArgumentException("Unbounded anchored template");
                }
                if ("WECHAT_PAYMENT_SUMMARY".equals(t.parserType)) {
                    if (!"WECHAT".equals(t.provider) || !"com.tencent.mm".equals(t.packageName) || !"微信支付：已支付".equals(t.bodyPrefix))
                        throw new IllegalArgumentException("Invalid WeChat summary origin");
                    templates.add(t);
                    continue;
                }
                if ("ALIPAY_EXPENSE_REMINDER".equals(t.parserType)) {
                    if (!"ALIPAY".equals(t.provider) || !"com.eg.android.AlipayGphone".equals(t.packageName) || !"你有一笔".equals(t.bodyPrefix))
                        throw new IllegalArgumentException("Invalid Alipay expense reminder origin");
                    templates.add(t);
                    continue;
                }
                JSONObject anchors = o.getJSONObject("anchors");
                for (String key : new String[]{"amount", "currency", "kind", "status", "date", "merchant"}) {
                    String anchor = anchors.getString(key);
                    if (anchor.isEmpty()) throw new IllegalArgumentException("Empty field anchor");
                    t.anchors.put(key, anchor);
                }
                templates.add(t);
            }
            return new TemplateCatalog(templates, root.optBoolean("synthetic", false));
        } catch (JSONException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid catalog", e);
        }
    }
}
