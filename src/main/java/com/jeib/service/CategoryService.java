package com.jeib.service;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Category;
import com.jeib.model.TransactionType;
import com.jeib.repository.CategoryRepository;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Category rules (CLAUDE.md, jeib-color-system.md §5):
 * <ul>
 *   <li>names are required, trimmed, at most 40 characters, and unique per kind ignoring case;</li>
 *   <li>every category gets its own color; a color may repeat only once all 14 are taken;</li>
 *   <li>"Other" and "Other income" are built in and can't be deleted;</li>
 *   <li>deleting a category moves its transactions to "Other" / "Other income".</li>
 * </ul>
 */
public final class CategoryService {

    public static final int MAX_NAME_LENGTH = 40;

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    public List<Category> list() throws SQLException {
        return categories.findActive();
    }

    /** How many live categories use each palette color (index = color), for the color picker. */
    public int[] colorUsage() throws SQLException {
        return categories.colorUsage();
    }

    /**
     * @param colorIndex the color picked by the user, or null to take the first free one
     */
    public Category create(String name, TransactionType kind, Integer colorIndex) throws SQLException {
        Objects.requireNonNull(kind, "kind");
        String cleanName = cleanName(name);
        requireNameFree(kind, cleanName, null);
        int[] usage = categories.colorUsage();
        int color = colorIndex == null ? leastUsedColor(usage) : requireUsableColor(colorIndex, usage);

        Category category = Category.create(cleanName, kind, color);
        categories.insert(category);
        return category;
    }

    /** Renames and/or recolors. The kind can't change: existing transactions depend on it. */
    public Category update(String id, String name, int colorIndex) throws SQLException {
        Category current = requireActive(id);
        String cleanName = cleanName(name);
        requireNameFree(current.kind(), cleanName, id);
        int[] usage = categories.colorUsage();
        usage[current.colorIndex()]--; // a category may keep its own color
        requireUsableColor(colorIndex, usage);

        categories.update(id, cleanName, colorIndex);
        return new Category(id, cleanName, current.kind(), colorIndex, current.builtIn());
    }

    /**
     * Soft-deletes the category and moves its transactions to the built-in fallback.
     *
     * @return ids of the moved transactions
     */
    public List<String> delete(String id) throws SQLException {
        Category category = requireActive(id);
        if (category.builtIn()) {
            throw new ValidationException(t("\"{0}\" is built in and can't be deleted.", category.name()));
        }
        Category fallback = categories.findBuiltIn(category.kind());
        return categories.softDelete(id, fallback.id());
    }

    /** The color used by the fewest categories; the lowest index wins a tie. Free colors have 0 uses. */
    public static int leastUsedColor(int[] usage) {
        int best = 0;
        for (int i = 1; i < usage.length; i++) {
            if (usage[i] < usage[best]) {
                best = i;
            }
        }
        return best;
    }

    private static int requireUsableColor(int colorIndex, int[] usage) {
        if (colorIndex < 0 || colorIndex >= Category.PALETTE_SIZE) {
            throw new ValidationException("color", t("Pick one of the {0} colors.", Category.PALETTE_SIZE));
        }
        boolean taken = usage[colorIndex] > 0;
        boolean anyFree = Arrays.stream(usage).anyMatch(uses -> uses == 0);
        if (taken && anyFree) {
            throw new ValidationException("color", t("That color is already used by another category. Pick a free one."));
        }
        return colorIndex;
    }

    private static String cleanName(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw new ValidationException("name", t("Give the category a name."));
        }
        if (clean.codePointCount(0, clean.length()) > MAX_NAME_LENGTH) {
            throw new ValidationException("name", t("Keep the name to {0} characters or fewer.", MAX_NAME_LENGTH));
        }
        return clean;
    }

    private void requireNameFree(TransactionType kind, String name, String exceptId) throws SQLException {
        if (categories.nameTaken(kind, name, exceptId)) {
            throw new ValidationException("name", kind == TransactionType.EXPENSE
                    ? t("You already have an expense category called \"{0}\".", name)
                    : t("You already have an income category called \"{0}\".", name));
        }
    }

    private Category requireActive(String id) throws SQLException {
        return categories.findActiveById(id)
                .orElseThrow(() -> new ValidationException(t("This category no longer exists.")));
    }
}
