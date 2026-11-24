package com.example.budgetapp.data;

import com.example.budgetapp.capture.notification.NotificationSnapshot;
import com.example.budgetapp.parsing.*;
import com.example.budgetapp.parsing.ParsedTransaction.*;
import com.example.budgetapp.processing.LedgerTypes.*;

import java.util.*;
import java.math.BigDecimal;

import org.json.*;

public final class EventCodec {
    private EventCodec() {
    }

    private static JSONObject envelope(String type) throws JSONException {
        return new JSONObject().put("formatVersion", 1).put("type", type);
    }

    private static JSONObject read(String text, String type) throws JSONException {
        JSONObject o = new JSONObject(text);
        if (number(o, "formatVersion") != 1 || !type.equals(string(o, "type")))
            throw new IllegalArgumentException("Unsupported payload");
        return o;
    }

    private static String string(JSONObject o, String key) throws JSONException {
        Object value = o.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException("Expected text: " + key);
        return (String) value;
    }

    private static String nullableString(JSONObject o, String key) throws JSONException {
        return o.isNull(key) ? null : string(o, key);
    }

    private static long number(JSONObject o, String key) throws JSONException {
        Object value = o.get(key);
        if (!(value instanceof Number))
            throw new IllegalArgumentException("Expected integer: " + key);
        return new BigDecimal(value.toString()).longValueExact();
    }

    private static Long nullableNumber(JSONObject o, String key) throws JSONException {
        return o.isNull(key) ? null : number(o, key);
    }

    private static Object optional(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static <E extends Enum<E>> E enumValue(JSONObject o, String key, Class<E> type) throws JSONException {
        String value = nullableString(o, key);
        return value == null ? null : Enum.valueOf(type, value);
    }

    public static String encodeSnapshot(NotificationSnapshot s) {
        try {
            JSONArray lines = new JSONArray();
            for (String line : s.textLines) lines.put(line);
            return envelope("snapshot").put("packageName", s.packageName).put("notificationKey", s.notificationKey).put("postTimeMs", s.postTimeMs).put("capturedAtMs", s.capturedAtMs).put("channelId", s.channelId).put("title", s.title).put("text", s.text).put("bigText", s.bigText).put("textLines", lines).put("groupSummary", s.groupSummary).put("truncated", s.truncated).put("appVersion", s.appVersion).toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Snapshot cannot be encoded", e);
        }
    }

    public static NotificationSnapshot decodeSnapshot(String text) {
        try {
            JSONObject o = read(text, "snapshot");
            JSONArray array = o.getJSONArray("textLines");
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (!(item instanceof String))
                    throw new IllegalArgumentException("Invalid text line");
                lines.add((String) item);
            }
            if (!(o.get("groupSummary") instanceof Boolean) || !(o.get("truncated") instanceof Boolean))
                throw new IllegalArgumentException("Invalid flags");
            return new NotificationSnapshot(string(o, "packageName"), string(o, "notificationKey"), number(o, "postTimeMs"), number(o, "capturedAtMs"), string(o, "channelId"), string(o, "title"), string(o, "text"), string(o, "bigText"), lines, o.getBoolean("groupSummary"), o.getBoolean("truncated"), string(o, "appVersion"));
        } catch (JSONException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid snapshot", e);
        }
    }

    public static String encodeParsed(ParseResult result) {
        try {
            JSONObject o = envelope("parsed").put("disposition", result.disposition.name()).put("reasonCode", optional(result.reasonCode)).put("parserId", optional(result.parserId)).put("parserVersion", result.version);
            ParsedTransaction p = result.candidate;
            if (p == null) return o.put("candidate", JSONObject.NULL).toString();
            JSONArray proofs = new JSONArray(), identities = new JSONArray();
            for (Proof proof : p.proofs) proofs.put(proof.name());
            for (Identity id : p.identities)
                identities.put(new JSONObject().put("idKind", id.idKind).put("namespace", id.namespace).put("externalId", id.externalId));
            JSONObject candidate = new JSONObject().put("kind", p.kind == null ? JSONObject.NULL : p.kind.name()).put("amountMinor", optional(p.amountMinor)).put("currency", optional(p.currency)).put("occurredAtMs", optional(p.occurredAtMs)).put("timePrecision", p.timePrecision == null ? JSONObject.NULL : p.timePrecision.name()).put("dateBasis", p.dateBasis.name()).put("merchant", optional(p.merchant)).put("payMethod", optional(p.payMethod)).put("tradeStatus", p.tradeStatus.name()).put("proofs", proofs).put("identities", identities);
            return o.put("candidate", candidate).toString();
        } catch (JSONException | RuntimeException e) {
            throw new IllegalArgumentException("Candidate cannot be encoded", e);
        }
    }

    public static ParseResult decodeParsed(String text) {
        try {
            JSONObject o = read(text, "parsed");
            ParseResult.Disposition disposition = ParseResult.Disposition.valueOf(string(o, "disposition"));
            long version = number(o, "parserVersion");
            if (version < 0 || version > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Invalid parser version");
            ParsedTransaction p = null;
            if (!o.isNull("candidate")) {
                JSONObject c = o.getJSONObject("candidate");
                p = new ParsedTransaction();
                p.kind = enumValue(c, "kind", Kind.class);
                p.amountMinor = nullableNumber(c, "amountMinor");
                p.currency = nullableString(c, "currency");
                p.occurredAtMs = nullableNumber(c, "occurredAtMs");
                p.timePrecision = enumValue(c, "timePrecision", TimePrecision.class);
                p.dateBasis = c.has("dateBasis") ? DateBasis.valueOf(string(c, "dateBasis")) : DateBasis.TRANSACTION_TEXT;
                p.merchant = nullableString(c, "merchant");
                p.payMethod = nullableString(c, "payMethod");
                p.tradeStatus = TradeStatus.valueOf(string(c, "tradeStatus"));
                JSONArray proofs = c.getJSONArray("proofs"), ids = c.getJSONArray("identities");
                for (int i = 0; i < proofs.length(); i++)
                    p.proofs.add(Proof.valueOf(proofs.getString(i)));
                for (int i = 0; i < ids.length(); i++) {
                    JSONObject id = ids.getJSONObject(i);
                    p.identities.add(new Identity(string(id, "idKind"), string(id, "namespace"), string(id, "externalId")));
                }
            }
            if (disposition == ParseResult.Disposition.CANDIDATE && p == null)
                throw new IllegalArgumentException("Missing candidate");
            return new ParseResult(disposition, p, nullableString(o, "reasonCode"), nullableString(o, "parserId"), (int) version);
        } catch (JSONException | RuntimeException e) {
            throw new IllegalArgumentException("Invalid candidate", e);
        }
    }
}
