package com.jeib.i18n;

import java.util.Map;

/**
 * The 11 categories that migration V2 creates, by their fixed ids. While a default still has its
 * original English name, it's shown in the app's language (Food → طعام). Once the user renames it,
 * their name is shown as typed, in every language.
 */
public final class DefaultCategories {

    private static final Map<String, String> ORIGINAL_NAMES = Map.ofEntries(
            Map.entry("a87a6066-c95c-48d4-b8c7-849a88bcab1f", "Food"),
            Map.entry("baa5872b-84d2-4d48-b6f0-a5bd9051e341", "Rent"),
            Map.entry("6259165f-d796-48e0-a505-3c861b02e114", "Shopping"),
            Map.entry("f39f7642-42bd-45e0-bde0-639e1e3a090f", "Transport"),
            Map.entry("816015e5-df17-4751-bafa-48f3b4f3185b", "Entertainment"),
            Map.entry("3be8525e-82ad-49ae-b0dc-19b789ff2f6a", "Subscriptions"),
            Map.entry("85e8c087-43da-4b4f-8800-5b7afb5a2592", "Other"),
            Map.entry("8ef40db2-169b-41ad-80eb-55312f02c840", "Health"),
            Map.entry("ee0d97a3-17cf-4704-ad63-4fe0a9cd69bb", "Salary"),
            Map.entry("bc80ba0f-b779-4321-96f4-aeada52bef68", "Freelance"),
            Map.entry("d9417abf-d031-43ef-bf00-cf67abf03e24", "Other income"));

    private DefaultCategories() {
    }

    /** The name to show for a category with this id and stored name. */
    public static String displayName(String id, String storedName) {
        String original = ORIGINAL_NAMES.get(id);
        return original != null && original.equals(storedName) ? Messages.t(original) : storedName;
    }
}
