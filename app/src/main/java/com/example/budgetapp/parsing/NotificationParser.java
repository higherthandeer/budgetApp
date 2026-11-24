package com.example.budgetapp.parsing;

import com.example.budgetapp.capture.notification.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.parsing.ParsedTransaction.*;
import com.example.budgetapp.parsing.ParseResult.Disposition;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class NotificationParser {
    public enum IntakeDecision {DROP, TRADE, SUSPECTED_TRADE}

    private final TemplateCatalog catalog;

    public NotificationParser(TemplateCatalog catalog) {
        this.catalog = catalog;
    }

    private String body(NotificationSnapshot s) {
        return s.text + "\n" + s.bigText + "\n" + String.join("\n", s.textLines);
    }

    private List<TemplateCatalog.Template> origins(NotificationSnapshot s) {
        List<TemplateCatalog.Template> found = new ArrayList<>();
        if (!NotificationPackages.isSupported(s.packageName)) return found;
        String body = body(s);
        for (var t : catalog.templates)
            if (t.packageName.equals(s.packageName) && (!"ANCHORED".equals(t.parserType) || t.channels.contains(s.channelId) && t.title.equals(s.title)) && Arrays.stream(body.split("\\R")).anyMatch(line -> ("WECHAT_PAYMENT_SUMMARY".equals(t.parserType) ? WechatPaymentSummaryParser.normalize(line) : line).startsWith(t.bodyPrefix)))
                found.add(t);
        return found;
    }

    public IntakeDecision classify(NotificationSnapshot s) {
        var matches = origins(s);
        if (matches.isEmpty()) return IntakeDecision.DROP;
        return matches.size() == 1 && (!"ANCHORED".equals(matches.get(0).parserType) || matches.get(0).appVersions.contains(s.appVersion)) && !s.truncated && !s.groupSummary ? IntakeDecision.TRADE : IntakeDecision.SUSPECTED_TRADE;
    }

    public ParseResult parse(NotificationSnapshot s) {
        List<TemplateCatalog.Template> matches = origins(s);
        if (matches.isEmpty())
            return new ParseResult(Disposition.IGNORED, null, "NON_TRADE", null, 0);
        if (matches.size() != 1)
            return new ParseResult(Disposition.REVIEW, null, "AMBIGUOUS_TEMPLATE", null, 0);
        var t = matches.get(0);
        String text = body(s);
        if (text.contains("退款成功"))
            return result(Disposition.IGNORED, null, "REFUND_UNSUPPORTED", t);
        if (text.contains("充值成功") || text.contains("提现成功") || text.contains("还本金"))
            return result(Disposition.IGNORED, null, "NON_LIVING_TRANSFER", t);
        ParsedTransaction p = new ParsedTransaction();
        p.proofs.add(Proof.ORIGIN);
        if (("ANCHORED".equals(t.parserType) && !t.appVersions.contains(s.appVersion)) || s.truncated || s.groupSummary)
            return result(Disposition.REVIEW, p, "UNVERIFIED_OR_INCOMPLETE", t);
        if ("WECHAT_PAYMENT_SUMMARY".equals(t.parserType))
            return WechatPaymentSummaryParser.parse(s, t);
        if ("ALIPAY_EXPENSE_REMINDER".equals(t.parserType))
            return AlipayExpenseReminderParser.parse(s, t);
        try {
            String status = value(text, t.anchors.get("status"));
            p.tradeStatus = "成功".equals(status) ? TradeStatus.SUCCESS : "处理中".equals(status) ? TradeStatus.PROCESSING : "失败".equals(status) ? TradeStatus.FAILED : "关闭".equals(status) ? TradeStatus.CLOSED : TradeStatus.UNKNOWN;
            if (p.tradeStatus == TradeStatus.FAILED || p.tradeStatus == TradeStatus.CLOSED)
                return result(Disposition.IGNORED, p, "PAYMENT_NOT_SUCCESSFUL", t);
            if (p.tradeStatus == TradeStatus.SUCCESS) p.proofs.add(Proof.STATUS);
            String amount = value(text, t.anchors.get("amount"));
            if (amount != null) {
                p.amountMinor = Money.parseFen(amount);
                p.proofs.add(Proof.AMOUNT);
            }
            p.currency = value(text, t.anchors.get("currency"));
            if ("CNY".equals(p.currency)) p.proofs.add(Proof.CURRENCY);
            String kind = value(text, t.anchors.get("kind"));
            p.kind = "支出".equals(kind) ? Kind.EXPENSE : "收入".equals(kind) ? Kind.INCOME : null;
            if (p.kind != null) p.proofs.add(Proof.KIND);
            String date = value(text, t.anchors.get("date"));
            if (date != null) {
                if (date.length() == 10) {
                    p.occurredAtMs = TimeWindows.dayAnchor(LocalDate.parse(date));
                    p.timePrecision = TimePrecision.DAY;
                } else {
                    LocalDateTime time = LocalDateTime.parse(date.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    p.occurredAtMs = time.atZone(TimeWindows.LEDGER_ZONE).toInstant().toEpochMilli();
                    p.timePrecision = date.length() == 16 ? TimePrecision.MINUTE : TimePrecision.SECOND;
                }
                p.proofs.add(Proof.DATE);
            }
            p.merchant = value(text, t.anchors.get("merchant"));
            return result(p.proofs.containsAll(EnumSet.allOf(Proof.class)) ? Disposition.CANDIDATE : Disposition.REVIEW, p, p.proofs.containsAll(EnumSet.allOf(Proof.class)) ? "OK" : "MISSING_FACTS", t);
        } catch (RuntimeException invalid) {
            return result(Disposition.REVIEW, p, "INVALID_OR_CONFLICTING_FACTS", t);
        }
    }

    private String value(String body, String anchor) {
        Set<String> found = new HashSet<>();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(anchor)) {
                String value = trimmed.substring(anchor.length()).trim();
                if (!value.isEmpty()) found.add(value);
            }
        }
        if (found.size() > 1) throw new IllegalArgumentException("Conflicting anchors");
        return found.isEmpty() ? null : found.iterator().next();
    }

    private ParseResult result(Disposition d, ParsedTransaction p, String reason, TemplateCatalog.Template t) {
        return new ParseResult(d, p, reason, t.id, t.version);
    }
}
