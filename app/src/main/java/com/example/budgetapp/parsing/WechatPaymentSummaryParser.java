package com.example.budgetapp.parsing;

import com.example.budgetapp.capture.notification.NotificationSnapshot;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;
import com.example.budgetapp.parsing.ParsedTransaction.*;

import java.util.*;
import java.util.regex.*;

/**
 * User-approved compact receipt format; the unread counter is never a transaction count.
 */
final class WechatPaymentSummaryParser {
    private static final Pattern UNREAD = Pattern.compile("^\\[[1-9][0-9]*条\\]");
    private static final Pattern PAYMENT = Pattern.compile("^微信支付：已支付[￥¥]([0-9]+(?:\\.[0-9]{1,2})?)$");

    static String normalize(String text) {
        String body = UNREAD.matcher(text.trim()).replaceFirst("").replace(':', '：')
                .replaceFirst("^微信支付： *", "微信支付：");
        // A single payment notification keeps the account name only in EXTRA_TITLE.
        return body.startsWith("已支付") ? "微信支付：" + body : body;
    }

    static ParseResult parse(NotificationSnapshot snapshot, TemplateCatalog.Template template) {
        List<String> texts = new ArrayList<>();
        texts.add(snapshot.text);
        texts.add(snapshot.bigText);
        texts.addAll(snapshot.textLines);
        Long amount = null;
        try {
            for (String text : texts) {
                if (text.trim().isEmpty()) continue;
                Matcher matcher = PAYMENT.matcher(normalize(text));
                if (!matcher.matches())
                    return result(ParseResult.Disposition.REVIEW, null, "INVALID_OR_CONFLICTING_FACTS", template);
                long value = Money.parseFen(matcher.group(1));
                if (value <= 0 || amount != null && amount != value)
                    return result(ParseResult.Disposition.REVIEW, null, "INVALID_OR_CONFLICTING_FACTS", template);
                amount = value;
            }
            if (amount == null)
                return result(ParseResult.Disposition.REVIEW, null, "MISSING_FACTS", template);
            ParsedTransaction candidate = new ParsedTransaction();
            candidate.kind = Kind.EXPENSE;
            candidate.currency = "CNY";
            candidate.amountMinor = amount;
            candidate.merchant = "";
            candidate.tradeStatus = TradeStatus.SUCCESS;
            candidate.dateBasis = snapshot.postTimeMs > 0 ? DateBasis.NOTIFICATION_POST_TIME : DateBasis.NOTIFICATION_CAPTURE_TIME;
            candidate.timePrecision = TimePrecision.SECOND;
            candidate.occurredAtMs = snapshot.postTimeMs > 0 ? snapshot.postTimeMs : snapshot.capturedAtMs;
            if (candidate.occurredAtMs <= 0) throw new IllegalArgumentException("Missing notification time");
            candidate.proofs.addAll(EnumSet.allOf(Proof.class));
            return result(ParseResult.Disposition.CANDIDATE, candidate, candidate.dateBasis.name(), template);
        } catch (RuntimeException invalid) {
            return result(ParseResult.Disposition.REVIEW, null, "INVALID_OR_CONFLICTING_FACTS", template);
        }
    }

    private static ParseResult result(ParseResult.Disposition disposition, ParsedTransaction candidate, String reason, TemplateCatalog.Template template) {
        return new ParseResult(disposition, candidate, reason, template.id, template.version);
    }
}
