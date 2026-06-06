package com.semanticbase.ingestion.pii;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegexPiiRedactorTest {

    private final RegexPiiRedactor redactor = new RegexPiiRedactor();

    @Test
    void redactsEmail() {
        assertThat(redactor.redact("Contact alice.smith@example.com for info"))
                .isEqualTo("Contact [EMAIL] for info");
    }

    @Test
    void redactsPhone() {
        assertThat(redactor.redact("Call (555) 123-4567 today"))
                .isEqualTo("Call [PHONE] today");
        assertThat(redactor.redact("Call 555-123-4567 today"))
                .isEqualTo("Call [PHONE] today");
        assertThat(redactor.redact("Call +1 555 123 4567 today"))
                .isEqualTo("Call [PHONE] today");
    }

    @Test
    void redactsSsn() {
        assertThat(redactor.redact("SSN: 123-45-6789."))
                .isEqualTo("SSN: [SSN].");
    }

    @Test
    void redactsAadhaar() {
        assertThat(redactor.redact("Aadhaar 1234 5678 9012 noted"))
                .isEqualTo("Aadhaar [AADHAAR] noted");
    }

    @Test
    void redactsPan() {
        assertThat(redactor.redact("PAN: ABCDE1234F"))
                .isEqualTo("PAN: [PAN]");
    }

    @Test
    void redactsValidCreditCard() {
        // Valid Visa test number (Luhn-passing)
        assertThat(redactor.redact("Card 4111 1111 1111 1111 expires soon"))
                .isEqualTo("Card [CREDIT_CARD] expires soon");
    }

    @Test
    void preservesInvalidCreditCard() {
        // Looks like a card but fails Luhn — leave intact
        String invalid = "Order 1234567890123456 confirmed";
        assertThat(redactor.redact(invalid)).isEqualTo(invalid);
    }

    @Test
    void leavesNonPiiAlone() {
        String input = "The capital of France is Paris.";
        assertThat(redactor.redact(input)).isEqualTo(input);
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(redactor.redact(null)).isNull();
        assertThat(redactor.redact("")).isEmpty();
    }

    @Test
    void redactsMultiplePiiInOneString() {
        String result = redactor.redact("Reach me at jane@x.com or 555-867-5309");
        assertThat(result).isEqualTo("Reach me at [EMAIL] or [PHONE]");
    }
}
