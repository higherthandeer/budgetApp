package com.example.budgetapp.parsing;

import com.example.budgetapp.capture.notification.NotificationSnapshot;
import com.example.budgetapp.parsing.ParsedTransaction.*;
import com.example.budgetapp.processing.*;
import com.example.budgetapp.processing.LedgerTypes.*;

import java.util.*;
import java.util.regex.*;

/**
 * The expense phrase owns the amount; coupon amounts and reward counts never do.
 */
final class AlipayExpenseReminderParser {
    private static final Pattern EXPENSE = Pattern.compile(
            "^你有一笔 *([0-9]+(?:\\.[0-9]{1,2})?) *元的支出");

    static ParseResult parse(NotificationSnapshot snapshot, TemplateCatalog.Template template) {
        List<String> texts = new ArrayList<>();
        texts.add(snapshot.text);
        texts.add(snapshot.bigText);
        texts.addAll(snapshot.textLines);
        String combined = String.join("\n", texts);
        for (String unsupported : new String[]{"元的退款", "元的收入", "退款成功", "支付失败", "处理中", "待支付", "已关闭"}) {
            if (combined.contains(unsupported))
                return result(ParseResult.Disposition.IGNORED, null, "NON_EXPENSE_OR_NOT_SUCCESSFUL", template);
        }
        Long amount = null;
        try {
            for (String text : texts) {
                if (text.trim().isEmpty()) continue;
                Matcher matcher = EXPENSE.matcher(text.trim());
                if (!matcher.lookingAt())
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
