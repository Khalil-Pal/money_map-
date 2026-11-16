<p align="center">
  <img src="jeib-logo-rounded.png" alt="Jeib logo" width="120">
</p>

<h1 align="center">Jeib · جيب</h1>

<p align="center">A simple desktop app to track your spending, set monthly limits, and see how much you can
still spend each day. In English and Arabic.</p>

## What it does

- **Add spending in seconds:** type `250 food lunch` and press Enter, or use the New transaction form.
- **Monthly limits** for each category and overall, with a warning at 80% and an alert at 100%.
- **Left per day:** how much you can still spend each day until the month ends.
- **Dashboard:** where your money went, as a chart, plus your limits at a glance.
- **Your own categories,** each with its own color.
- **Three currencies:** US dollar, Israeli shekel, and Russian ruble, with your own exchange rates.
- **Undo anything** with Ctrl+Z.
- **English and Arabic**, with light, dark, or automatic (by time of day) theme.
- **Private:** your data stays on your computer, with an automatic backup every day.

## Download (Windows)

1. Open the [Releases page](https://github.com/Khalil-Pal/money_map-/releases).
2. Download **Jeib-0.1.0-windows.zip**.
3. Right-click the zip and choose **Extract All**, then **Extract**.
4. Open the **Jeib** folder and double-click **Jeib.exe**. You don't need to install Java.
5. If Windows says **"Windows protected your PC"**, click **More info**, then **Run anyway**. This only
   happens the first time, because the test build isn't signed yet.
6. Choose your language and main currency, and start adding.

Found a problem or have an idea? In Jeib, open **Settings → Help**.

## Build from source

You need JDK 21. Maven comes with the project.

```bash
mvnw.cmd javafx:run                                                   # run the app
mvnw.cmd test                                                         # run the tests
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1  # build the zip
```
