package com.jeib.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.model.Digits;
import com.jeib.model.Money;
import com.jeib.model.SupportedCurrency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @AfterEach
    void backToEnglish() {
        Messages.setLanguage(Language.ENGLISH); // other tests expect English
    }

    @Test
    void englishIsTheKeyAndPlaceholdersAreFilledIn() {
        assertEquals("Enter an amount.", Messages.t("Enter an amount."));
        assertEquals("$1.00 of $2.00", Messages.t("{0} of {1}", "$1.00", "$2.00"));
        assertEquals("Can't undo: x", Messages.t("Can't undo: {0}", "x")); // apostrophes need no escaping
        assertEquals("Not translated yet", Messages.t("Not translated yet"));
    }

    @Test
    void arabicTranslatesAndFallsBackToEnglish() {
        Messages.setLanguage(Language.ARABIC);

        assertEquals("أدخل مبلغًا.", Messages.t("Enter an amount."));
        assertEquals("حد طعام", Messages.t("{0} limit", "طعام"));
        assertEquals("Not translated yet", Messages.t("Not translated yet"));
        assertTrue(Messages.rightToLeft());
        assertEquals("ar", Messages.locale().getLanguage());
    }

    @Test
    void amountsStayInOnePieceInsideArabicText() {
        assertEquals("$300.00", SupportedCurrency.USD.format(30_000)); // English: untouched

        Messages.setLanguage(Language.ARABIC);
        String wrapped = SupportedCurrency.USD.format(30_000);

        assertEquals("‪$300.00‬", wrapped); // left-to-right embedding
        assertFalse(SupportedCurrency.USD.formatPlain(30_000).contains("‪"));
    }

    @Test
    void defaultCategoriesShowInTheLanguageUntilRenamed() {
        String food = "a87a6066-c95c-48d4-b8c7-849a88bcab1f";
        assertEquals("Food", DefaultCategories.displayName(food, "Food"));

        Messages.setLanguage(Language.ARABIC);
        assertEquals("طعام", DefaultCategories.displayName(food, "Food"));
        assertEquals("Groceries", DefaultCategories.displayName(food, "Groceries")); // renamed: as typed
        assertEquals("Coffee", DefaultCategories.displayName("some-other-id", "Coffee"));
    }

    @Test
    void arabicDigitsAreAccepted() {
        assertEquals("250.5", Digits.normalize("٢٥٠٫٥"));
        assertEquals("1250", Digits.normalize("١٬٢٥٠"));
        assertEquals("300", Digits.normalize("۳۰۰")); // Persian keyboard
        assertEquals("abc 12", Digits.normalize("abc 12"));
        assertEquals(25_050, Money.parseMinorUnits("٢٥٠٫٥"));
    }
}
