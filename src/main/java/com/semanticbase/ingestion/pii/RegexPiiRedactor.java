package com.semanticbase.ingestion.pii;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RegexPiiRedactor implements PiiRedactor {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?\\d{1,3}[\\s-]?)?(?:\\(\\d{3}\\)|\\d{3})[\\s.-]?\\d{3}[\\s.-]?\\d{4}(?!\\d)");

    private static final Pattern SSN = Pattern.compile(
            "(?<!\\d)\\d{3}-\\d{2}-\\d{4}(?!\\d)");

    private static final Pattern AADHAAR = Pattern.compile(
            "(?<!\\d)\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}(?!\\d)");

    private static final Pattern PAN = Pattern.compile(
            "\\b[A-Z]{5}\\d{4}[A-Z]\\b");

    private static final Pattern CREDIT_CARD = Pattern.compile(
            "(?<!\\d)\\d(?:[ -]?\\d){12,18}(?!\\d)");

    private final List<Rule> rules = List.of(
            new Rule(EMAIL, "[EMAIL]", false),
            new Rule(SSN, "[SSN]", false),
            new Rule(CREDIT_CARD, "[CREDIT_CARD]", true),
            new Rule(AADHAAR, "[AADHAAR]", false),
            new Rule(PAN, "[PAN]", false),
            new Rule(PHONE, "[PHONE]", false)
    );

    @Override
    public String redact(String input) {
        if (input == null || input.isEmpty()) return input;
        String result = input;
        for (Rule rule : rules) {
            result = applyRule(result, rule);
        }
        return result;
    }

    private String applyRule(String text, Rule rule) {
        Matcher m = rule.pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String match = m.group();
            String replacement = (rule.luhnValidate && !passesLuhn(match)) ? match : rule.replacement;
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private boolean passesLuhn(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 19) return false;
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    private record Rule(Pattern pattern, String replacement, boolean luhnValidate) {}
}
