package com.jeib.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jeib.db.Database;
import com.jeib.model.SupportedCurrency;
import com.jeib.repository.SettingsRepository;
import com.jeib.service.CurrencyService;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetupViewModelTest {

    @TempDir
    Path tempDir;

    @Test
    void continueIsOnlyPossibleOnceACurrencyIsChosen() throws SQLException {
        Database database = new Database(tempDir.resolve("test.db"));
        database.migrate();
        CurrencyService currencies = new CurrencyService(new SettingsRepository(database));
        SetupViewModel viewModel = new SetupViewModel(currencies, null);

        assertFalse(viewModel.canSave().get());
        viewModel.choiceProperty().set(SupportedCurrency.ILS);
        assertTrue(viewModel.canSave().get());

        viewModel.saveNow(viewModel.choiceProperty().get());
        assertEquals(Optional.of(SupportedCurrency.ILS), currencies.mainCurrency());
    }
}
