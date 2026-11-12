package com.jeib.ui;

import static com.jeib.i18n.Messages.t;

import com.jeib.model.Category;
import com.jeib.model.TransactionType;
import com.jeib.service.CategoryService;
import com.jeib.service.ValidationException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/**
 * New / Edit category: a name and one of the 14 palette colors (jeib-color-system.md §5). Colors
 * other categories use are greyed out while any color is free. Deleting needs a second click to
 * confirm, because it moves the category's transactions to "Other" and isn't undoable.
 */
public final class CategoryDialogViewModel {

    @FunctionalInterface
    interface Work {
        String run() throws SQLException;
    }

    private final CategoryService categories;
    private final Category editing; // null when creating

    private final StringProperty name = new SimpleStringProperty("");
    private final ObjectProperty<TransactionType> kind = new SimpleObjectProperty<>();
    private final ObjectProperty<Integer> color = new SimpleObjectProperty<>();
    private final ObjectProperty<int[]> usage = new SimpleObjectProperty<>(new int[Category.PALETTE_SIZE]);
    private final Map<String, StringProperty> errors = Map.of(
            "name", new SimpleStringProperty(""),
            "color", new SimpleStringProperty(""),
            "form", new SimpleStringProperty(""));
    private final BooleanProperty confirmingDelete = new SimpleBooleanProperty(false);
    private final BooleanProperty saving = new SimpleBooleanProperty(false);

    /** @param editing the category to edit, or null to create one of {@code kind} */
    public CategoryDialogViewModel(CategoryService categories, Category editing, TransactionType kind) {
        this.categories = categories;
        this.editing = editing;
        this.kind.set(editing == null ? kind : editing.kind());
        if (editing != null) {
            name.set(editing.name());
            color.set(editing.colorIndex());
        }
        name.addListener((obs, old, now) -> errors.get("name").set(""));
        color.addListener((obs, old, now) -> errors.get("color").set(""));
    }

    // ---- loading -----------------------------------------------------------

    int[] loadData() throws SQLException {
        return categories.colorUsage();
    }

    /** Takes the color usage; a new category starts on the first free color. */
    void apply(int[] colorUsage) {
        usage.set(colorUsage);
        if (color.get() == null) {
            color.set(CategoryService.leastUsedColor(colorUsage));
        }
    }

    public void load() {
        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() throws Exception {
                return loadData();
            }
        };
        task.setOnSucceeded(event -> apply(task.getValue()));
        task.setOnFailed(event -> errors.get("form").set(t("Couldn't load colors: {0}", task.getException().getMessage())));
        Thread.ofVirtual().name("jeib-category-colors").start(task);
    }

    /**
     * Whether color {@code index} is used by another category while some color is still free (the
     * picker greys it out). Once all 14 are used, any color may be picked again.
     */
    public boolean isTaken(int index) {
        int[] used = usage.get().clone();
        if (editing != null) {
            used[editing.colorIndex()]--; // a category may keep its own color
        }
        boolean anyFree = Arrays.stream(used).anyMatch(n -> n == 0);
        return anyFree && used[index] > 0;
    }

    /** True when all 14 colors are in use, so the picker says colors may repeat. */
    public boolean allColorsUsed() {
        int[] used = usage.get().clone();
        if (editing != null) {
            used[editing.colorIndex()]--;
        }
        return Arrays.stream(used).noneMatch(n -> n == 0);
    }

    // ---- saving and deleting -------------------------------------------------

    /** Database work for {@link #save}: creates or updates. Returns the toast message. */
    String saveNow(String nameText, TransactionType type, Integer colorIndex) throws SQLException {
        if (editing == null) {
            Category created = categories.create(nameText, type, colorIndex);
            return t("Category “{0}” added.", created.name());
        }
        Category updated = categories.update(editing.id(), nameText, colorIndex == null ? editing.colorIndex() : colorIndex);
        return t("Category “{0}” saved.", updated.name());
    }

    /** Database work for {@link #delete}. Returns the toast message. */
    String deleteNow() throws SQLException {
        List<String> moved = categories.delete(editing.id());
        String fallback = editing.kind() == TransactionType.EXPENSE ? t("Other") : t("Other income");
        String deleted = t("Category “{0}” deleted.", editing.name());
        if (moved.isEmpty()) {
            return deleted;
        }
        return deleted + " " + (moved.size() == 1 ? t("1 transaction moved to {0}.", fallback)
                : t("{0} transactions moved to {1}.", moved.size(), fallback));
    }

    public void save(Consumer<String> onDone) {
        String nameText = name.get();
        TransactionType type = kind.get();
        Integer colorIndex = color.get();
        run(() -> saveNow(nameText, type, colorIndex), onDone);
    }

    /** First call asks for confirmation; the second deletes. */
    public void delete(Consumer<String> onDone) {
        if (!confirmingDelete.get()) {
            confirmingDelete.set(true);
            return;
        }
        run(this::deleteNow, onDone);
    }

    public void cancelDelete() {
        confirmingDelete.set(false);
    }

    private void run(Work work, Consumer<String> onDone) {
        if (saving.get()) {
            return;
        }
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return work.run();
            }
        };
        task.setOnSucceeded(event -> {
            saving.set(false);
            onDone.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            saving.set(false);
            confirmingDelete.set(false);
            Throwable e = task.getException();
            if (e instanceof ValidationException v) {
                errors.getOrDefault(v.field() == null ? "form" : v.field(), errors.get("form")).set(v.getMessage());
            } else {
                errors.get("form").set(t("Something went wrong: {0}", e.getMessage()));
            }
        });
        errors.values().forEach(error -> error.set(""));
        saving.set(true);
        Thread.ofVirtual().name("jeib-category-save").start(task);
    }

    // ---- for the controller --------------------------------------------------

    public String title() {
        if (editing != null) {
            return t("Edit category");
        }
        return kind.get() == TransactionType.EXPENSE ? t("New expense category") : t("New income category");
    }

    public boolean isEditing() {
        return editing != null;
    }

    /** Built-ins ("Other", "Other income") can be renamed and recolored but never deleted. */
    public boolean canDelete() {
        return editing != null && !editing.builtIn();
    }

    public String deleteQuestion() {
        String fallback = kind.get() == TransactionType.EXPENSE ? t("Other") : t("Other income");
        return editing == null ? "" : t("Delete “{0}”? Its transactions move to {1}. This can't be undone.",
                editing.name(), fallback);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public ObjectProperty<Integer> colorProperty() {
        return color;
    }

    public ObjectProperty<int[]> usageProperty() {
        return usage;
    }

    public ReadOnlyStringProperty error(String key) {
        return errors.get(key);
    }

    public ReadOnlyBooleanProperty confirmingDeleteProperty() {
        return confirmingDelete;
    }

    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }
}
