package com.jeib.service;

import static com.jeib.model.SupportedCurrency.ILS;
import static com.jeib.model.SupportedCurrency.RUB;
import static com.jeib.model.SupportedCurrency.USD;
import static com.jeib.model.TransactionType.EXPENSE;
import static com.jeib.model.TransactionType.INCOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.model.Category;
import com.jeib.model.SupportedCurrency;
import com.jeib.model.TransactionType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuickAddParserTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-12");

    private static Category category(String name, TransactionType kind, boolean builtIn) {
        return new Category(name.toLowerCase().replace(' ', '-'), name, kind, 0, builtIn);
    }

    private static final List<Category> CATEGORIES = List.of(
            category("Food", EXPENSE, false),
            category("Rent", EXPENSE, false),
            category("Shopping", EXPENSE, false),
            category("Transport", EXPENSE, false),
            category("Entertainment", EXPENSE, false),
            category("Subscriptions", EXPENSE, false),
            category("Health", EXPENSE, false),
            category("Coffee shops", EXPENSE, false),
            category("Car", EXPENSE, false),
            category("Cafe", EXPENSE, false),
            category("Other", EXPENSE, true),
            category("Salary", INCOME, false),
            category("Freelance", INCOME, false),
            category("Other income", INCOME, true));

    private static QuickAddParser.Parsed parse(String text) {
        return QuickAddParser.parse(text, CATEGORIES, RUB, TODAY);
    }

    @Test
    void amountCategoryAndNote() {
        QuickAddParser.Parsed parsed = parse("250 food lunch with Sam");

        assertEquals(EXPENSE, parsed.input().type());
        assertEquals("250", parsed.input().amountText());
        assertEquals(25_000, parsed.amountMinor());
        assertEquals(RUB, parsed.input().currency());
        assertEquals("Food", parsed.category().name());
        assertTrue(parsed.categoryMatched());
        assertEquals("lunch with Sam", parsed.input().note());
        assertEquals(TODAY, parsed.input().occurredOn());
    }

    @ParameterizedTest(name = "\"{0}\" is in {1}")
    @CsvSource({
            "12.50 usd coffee,   USD",
            "12.50 USD coffee,   USD",
            "$12.50 coffee,      USD",
            "12.50$ coffee,      USD",
            "45 ils gift,        ILS",
            "45 nis gift,        ILS",
            "₪45 gift,           ILS",
            "45 shekels gift,    ILS",
            "250₽ food,          RUB",
            "250 rub food,       RUB",
            "250 food,           RUB", // no currency: the main one
    })
    void currencies(String text, SupportedCurrency expected) {
        assertEquals(expected, parse(text).input().currency());
    }

    @Test
    void noMatchingCategoryFallsBackToOtherAndKeepsEveryWordAsTheNote() {
        QuickAddParser.Parsed parsed = parse("12.50 usd snacks to go");

        assertEquals("Other", parsed.category().name());
        assertFalse(parsed.categoryMatched());
        assertEquals("snacks to go", parsed.input().note());
    }

    @Test
    void aPlusMakesItIncome() {
        QuickAddParser.Parsed salary = parse("+300 usd salary");
        assertEquals(INCOME, salary.input().type());
        assertEquals("Salary", salary.category().name());
        assertNull(salary.input().note());

        QuickAddParser.Parsed gift = parse("+50 gift from mom");
        assertEquals(INCOME, gift.input().type());
        assertEquals("Other income", gift.category().name());
        assertEquals("gift from mom", gift.input().note());

        assertEquals(INCOME, parse("+ 300 salary").input().type());
    }

    @Test
    void aWordThatOnlyMatchesAnIncomeCategoryMakesItIncome() {
        QuickAddParser.Parsed parsed = parse("2800 salary november");

        assertEquals(INCOME, parsed.input().type());
        assertEquals("Salary", parsed.category().name());
        assertEquals("november", parsed.input().note());
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            "40 ent cinema,          Entertainment",
            "40 ENTERTAINMENT,       Entertainment",
            "5 he pills,             Health",
            "12 coffee shops latte,  Coffee shops",
            "12 co latte,            Coffee shops",
            "12 ca latte,            Other",   // Car or Cafe? Ambiguous: no guess
            "12 s latte,             Other",   // one letter is too short to guess
            "300 other income,       Other",   // expense first: "Other", note "income"
            "+300 other income,      Other income",
    })
    void categoryNamesAndUniqueStarts(String text, String expected) {
        assertEquals(expected, parse(text).category().name());
    }

    @Test
    void aCommaWorksAsTheDecimalSeparator() {
        assertEquals(1_250, parse("12,5 food").amountMinor());
    }

    @Test
    void justAnAmountIsEnough() {
        QuickAddParser.Parsed parsed = parse("99");

        assertEquals("Other", parsed.category().name());
        assertNull(parsed.input().note());
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @NullSource
    @ValueSource(strings = {"", "   ", "lunch 250", "1.234 food", "12. food", "-5 food", "+", "abc"})
    void aLineMustStartWithAnAmount(String text) {
        ValidationException error = assertThrows(ValidationException.class, () -> parse(text));
        assertEquals(QuickAddParser.FIELD, error.field());
    }

    @Test
    void aHugeAmountIsRejectedWithoutCrashing() {
        assertThrows(ValidationException.class, () -> parse("99999999999999999999 food"));
    }
}
