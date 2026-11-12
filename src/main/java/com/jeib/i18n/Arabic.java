package com.jeib.i18n;

import java.util.HashMap;
import java.util.Map;

/**
 * Arabic for every English sentence the app shows (the keys are the English text, see
 * {@link Messages}). Counts are phrased as "عدد المعاملات: 12" so Arabic number agreement never
 * goes wrong. ArabicCoverageTest fails if a sentence used in code or FXML is missing here.
 */
final class Arabic {

    static final Map<String, String> TEXTS = build();

    private Arabic() {
    }

    private static Map<String, String> build() {
        Map<String, String> m = new HashMap<>();

        // ---- navigation, pages, common words
        m.put("Dashboard", "لوحة المعلومات");
        m.put("Transactions", "المعاملات");
        m.put("Budgets", "الميزانيات");
        m.put("Categories", "الفئات");
        m.put("Reports", "التقارير");
        m.put("Accounts", "الحسابات");
        m.put("Recurring", "المتكررة");
        m.put("Goals", "الأهداف");
        m.put("Settings", "الإعدادات");
        m.put("Dark mode", "الوضع الليلي");
        m.put("Light mode", "الوضع النهاري");
        m.put("Automatic", "تلقائي");
        m.put("Automatic: dark from 7 PM to 7 AM", "تلقائي: ليلي من 7 مساءً حتى 7 صباحًا");
        m.put("Click to switch", "انقر للتبديل");
        m.put("Jeib remembers your choice. Automatic is dark from 7 PM to 7 AM.",
                "يتذكر جيب اختيارك. الوضع التلقائي ليلي من 7 مساءً حتى 7 صباحًا.");
        m.put("Previous month", "الشهر السابق");
        m.put("Next month", "الشهر التالي");
        m.put("Quick add", "إضافة سريعة");
        m.put("Quick add — type 250 food lunch and press Enter", "إضافة سريعة — اكتب 250 طعام غداء ثم اضغط Enter");
        m.put("New transaction", "معاملة جديدة");
        m.put("Edit transaction", "تعديل المعاملة");
        m.put("Add transaction", "إضافة معاملة");
        m.put("Delete transaction", "حذف معاملة");
        m.put("Save", "حفظ");
        m.put("Cancel", "إلغاء");
        m.put("Delete", "حذف");
        m.put("Edit", "تعديل");
        m.put("Add", "إضافة");
        m.put("Continue", "متابعة");
        m.put("Undo", "تراجع");
        m.put("Expense", "مصروف");
        m.put("Income", "دخل");
        m.put("Amount", "المبلغ");
        m.put("Date", "التاريخ");
        m.put("Category", "الفئة");
        m.put("Note", "ملاحظة");
        m.put("Name", "الاسم");
        m.put("Color", "اللون");
        m.put("Status", "الحالة");
        m.put("Used", "المستخدَم");
        m.put("spent", "مصروف");
        m.put("Something went wrong: {0}", "حدث خطأ: {0}");
        m.put("Built in Week 6: monthly report and Excel export.", "سيُضاف لاحقًا: التقرير الشهري والتصدير إلى Excel.");
        m.put("Built in Week 8: multiple accounts and currencies.", "سيُضاف لاحقًا: حسابات متعددة.");
        m.put("Built in Week 8: recurring payments and bill reminders.", "سيُضاف لاحقًا: المدفوعات المتكررة وتذكير الفواتير.");
        m.put("Built in Week 8: savings goals.", "سيُضاف لاحقًا: أهداف الادخار.");

        // ---- language
        m.put("Language", "اللغة");
        m.put("Jeib speaks English and Arabic. The window reloads in the language you choose.",
                "يدعم جيب العربية والإنجليزية. تُعاد تهيئة النافذة باللغة التي تختارها.");

        // ---- default categories (shown in Arabic while their names are unchanged)
        m.put("Food", "طعام");
        m.put("Rent", "إيجار");
        m.put("Shopping", "تسوق");
        m.put("Transport", "مواصلات");
        m.put("Entertainment", "ترفيه");
        m.put("Subscriptions", "اشتراكات");
        m.put("Other", "أخرى");
        m.put("Health", "صحة");
        m.put("Salary", "راتب");
        m.put("Freelance", "عمل حر");
        m.put("Other income", "دخل آخر");
        m.put("Unknown category", "فئة غير معروفة");

        // ---- colors (jeib-color-system.md §5) and currencies
        m.put("Orange", "برتقالي");
        m.put("Blue", "أزرق");
        m.put("Purple", "بنفسجي");
        m.put("Teal", "أزرق مخضر");
        m.put("Pink", "وردي");
        m.put("Ochre", "أصفر ترابي");
        m.put("Slate", "رمادي أزرق");
        m.put("Lime", "ليموني");
        m.put("Cyan", "سماوي");
        m.put("Fuchsia", "فوشيا");
        m.put("Maroon", "عنابي");
        m.put("Brown", "بني");
        m.put("Plum", "برقوقي");
        m.put("Taupe", "رمادي بني");
        m.put("US dollar", "دولار أمريكي");
        m.put("Israeli shekel", "شيكل إسرائيلي");
        m.put("Russian ruble", "روبل روسي");

        // ---- setup
        m.put("Welcome to Jeib", "أهلًا بك في جيب");
        m.put("Choose your main currency. Your totals, limits, and daily budget are shown in it. You can still enter amounts in the other two currencies.",
                "اختر عملتك الرئيسية. تُعرض بها مجاميعك وحدودك وميزانيتك اليومية، ويمكنك إدخال مبالغ بالعملتين الأخريين أيضًا.");
        m.put("Couldn't save: {0}", "تعذّر الحفظ: {0}");

        // ---- transactions list
        m.put("Showing only", "يُعرض فقط");
        m.put("Show all", "عرض الكل");
        m.put("No note", "بلا ملاحظة");
        m.put("1 transaction", "معاملة واحدة");
        m.put("{0} transactions", "عدد المعاملات: {0}");
        m.put("1 transaction in {0}", "معاملة واحدة في {0}");
        m.put("{0} transactions in {1}", "عدد المعاملات في {1}: {0}");
        m.put("No transactions in {0}.", "لا توجد معاملات في {0}.");
        m.put("No {0} transactions in {1}.", "لا توجد معاملات في فئة {0} خلال {1}.");
        m.put("Couldn't load transactions: {0}", "تعذّر تحميل المعاملات: {0}");
        m.put("Couldn't delete: {0}", "تعذّر الحذف: {0}");
        m.put("Transaction deleted.", "حُذفت المعاملة.");

        // ---- undo / redo
        m.put("Undone: {0}", "تم التراجع: {0}");
        m.put("Redone: {0}", "تمت الإعادة: {0}");
        m.put("Nothing to undo.", "لا شيء للتراجع عنه.");
        m.put("Nothing to redo.", "لا شيء لإعادته.");
        m.put("Can't undo: {0}", "تعذّر التراجع: {0}");
        m.put("Can't redo: {0}", "تعذّرت الإعادة: {0}");

        // ---- transaction dialog
        m.put("Exchange rate", "سعر الصرف");
        m.put("Counts this amount in your totals. Update it whenever the rate changes.",
                "يُستخدم لاحتساب هذا المبلغ في مجاميعك. حدّثه كلما تغيّر السعر.");
        m.put("Optional, e.g. Lunch with Sam", "اختياري، مثلًا غداء مع سامي");
        m.put("+ New category", "+ فئة جديدة");
        m.put("New category name, e.g. Coffee", "اسم الفئة الجديدة، مثلًا قهوة");
        m.put("e.g. 92.50", "مثلًا 92.50");
        m.put("1 {0} =", "سعر 1 {0}"); // "price of 1 USD": an "=" lands on the wrong side in right-to-left

        // ---- quick add
        m.put("{0}   ·   Enter to add, Esc to clear", "{0}   ·   Enter للإضافة، Esc للمسح");
        m.put("(no category matched)", "(لم تُطابق أي فئة)");
        m.put("Added {0} · {1}.", "أُضيف {0} · {1}.");
        m.put("Use New transaction to enter the rate.", "استخدم «معاملة جديدة» لإدخال السعر.");
        m.put("Type an amount first, e.g. 250 food lunch.", "اكتب المبلغ أولًا، مثلًا 250 طعام غداء.");
        m.put("Start with an amount, e.g. 250 food lunch (at most two decimals).",
                "ابدأ بالمبلغ، مثلًا 250 طعام غداء (منزلتان عشريتان كحد أقصى).");

        // ---- validation (services)
        m.put("Choose expense or income.", "اختر مصروفًا أو دخلًا.");
        m.put("Enter an amount.", "أدخل مبلغًا.");
        m.put("Enter an amount like 12.50 (at most two decimals).", "أدخل مبلغًا مثل 12.50 (منزلتان عشريتان كحد أقصى).");
        m.put("The amount must be more than 0.", "يجب أن يكون المبلغ أكبر من 0.");
        m.put("That amount is too large.", "المبلغ كبير جدًا.");
        m.put("Pick a date.", "اختر تاريخًا.");
        m.put("The date is more than a year ahead. Check the year.", "التاريخ بعد أكثر من سنة من الآن. تحقّق من السنة.");
        m.put("Pick a category.", "اختر فئة.");
        m.put("That category no longer exists. Pick another.", "هذه الفئة لم تعد موجودة. اختر فئة أخرى.");
        m.put("\"{0}\" is an income category. Pick an expense category.", "«{0}» فئة دخل. اختر فئة مصروفات.");
        m.put("\"{0}\" is an expense category. Pick an income category.", "«{0}» فئة مصروفات. اختر فئة دخل.");
        m.put("Keep the note to {0} characters or fewer.", "يجب ألا تزيد الملاحظة على {0} حرفًا.");
        m.put("This transaction was deleted or changed elsewhere. Reload and try again.",
                "حُذفت هذه المعاملة أو تغيّرت في مكان آخر. أعد التحميل وحاول مجددًا.");
        m.put("This transaction can't be brought back: it isn't deleted any more.",
                "لا يمكن استعادة هذه المعاملة: لم تعد محذوفة.");
        m.put("This transaction no longer exists.", "هذه المعاملة لم تعد موجودة.");
        m.put("Set how much 1 {0} is in {1} first, so this amount can be counted in your totals.",
                "حدّد أولًا كم يساوي 1 {0} بعملة {1}، حتى يُحتسب هذا المبلغ في مجاميعك.");
        m.put("Set how much 1 {0} is in {1} first, so your amounts and limits can be converted.",
                "حدّد أولًا كم يساوي 1 {0} بعملة {1}، حتى يمكن تحويل مبالغك وحدودك.");
        m.put("Set how much 1 {0} is in {1} first, so your limits can be converted.",
                "حدّد أولًا كم يساوي 1 {0} بعملة {1}، حتى يمكن تحويل حدودك.");
        m.put("Choose your main currency first.", "اختر عملتك الرئيسية أولًا.");
        m.put("{0} is your main currency, so its rate is always 1.", "{0} هي عملتك الرئيسية، لذا سعرها دائمًا 1.");
        m.put("Enter a rate like 92.50 (at most 6 decimals).", "أدخل سعرًا مثل 92.50 (6 منازل عشرية كحد أقصى).");
        m.put("The rate must be more than 0.", "يجب أن يكون السعر أكبر من 0.");
        m.put("That rate is too large. Check it.", "السعر كبير جدًا. تحقّق منه.");
        m.put("\"{0}\" is built in and can't be deleted.", "«{0}» فئة أساسية ولا يمكن حذفها.");
        m.put("Pick one of the {0} colors.", "اختر أحد الألوان الـ{0}.");
        m.put("That color is already used by another category. Pick a free one.", "هذا اللون مستخدم لفئة أخرى. اختر لونًا متاحًا.");
        m.put("Give the category a name.", "اكتب اسمًا للفئة.");
        m.put("Keep the name to {0} characters or fewer.", "يجب ألا يزيد الاسم على {0} حرفًا.");
        m.put("You already have an expense category called \"{0}\".", "لديك بالفعل فئة مصروفات باسم «{0}».");
        m.put("You already have an income category called \"{0}\".", "لديك بالفعل فئة دخل باسم «{0}».");
        m.put("This category no longer exists.", "هذه الفئة لم تعد موجودة.");
        m.put("Limits are for expense categories only.", "الحدود لفئات المصروفات فقط.");
        m.put("Enter a limit, e.g. 10000.", "أدخل حدًا، مثلًا 10000.");
        m.put("Enter a limit like 10000 or 10000.50 (at most two decimals).", "أدخل حدًا مثل 10000 أو 10000.50 (منزلتان عشريتان كحد أقصى).");
        m.put("The limit must be more than 0.", "يجب أن يكون الحد أكبر من 0.");
        m.put("That limit is too large.", "الحد كبير جدًا.");

        // ---- limits and statuses
        m.put("Within limit", "ضمن الحد");
        m.put("Near limit", "قريب من الحد");
        m.put("Limit exceeded", "تجاوز الحد");
        m.put("No limit", "بلا حد");
        m.put("Limits apply to every month and are in {0}. Click one to change it.",
                "تُطبَّق الحدود على كل شهر وهي بعملة {0}. انقر على أي حد لتغييره.");
        m.put("Spending in {0} isn't counted yet: it has no exchange rate. Open one of those transactions and enter the rate.",
                "المصروفات بعملة {0} لا تُحتسب بعد لعدم وجود سعر صرف لها. افتح إحدى تلك المعاملات وأدخل السعر.");
        m.put("Monthly limit · all spending", "الحد الشهري · كل المصروفات");
        m.put("Spent {0} this month. Set an overall limit to see how much is left and how much you can spend per day.",
                "صرفت {0} هذا الشهر. ضع حدًا إجماليًا لترى كم تبقّى وكم يمكنك أن تصرف يوميًا.");
        m.put("Set monthly limit", "ضع حدًا شهريًا");
        m.put("{0} of {1}", "{0} من {1}");
        m.put("{0} left", "المتبقي {0}");
        m.put("{0} over", "تجاوز بمقدار {0}");
        m.put("{0} spent", "صُرف {0}");
        m.put("Spent this month", "المصروف هذا الشهر");
        m.put("Monthly limit", "الحد الشهري");
        m.put("{0} limit", "حد {0}");
        m.put("Limit per month", "الحد الشهري");
        m.put("e.g. 10000", "مثلًا 10000");
        m.put("Remove limit", "إزالة الحد");
        m.put("The most you want to spend in a month, across all categories.", "أقصى ما تريد صرفه في الشهر، في كل الفئات.");
        m.put("The most you want to spend on {0} in a month.", "أقصى ما تريد صرفه على {0} في الشهر.");
        m.put("In {0}; applies to every month.", "بعملة {0}، ويُطبَّق على كل شهر.");
        m.put("You're over your monthly limit ({0}%).", "لقد تجاوزت حدك الشهري ({0}%).");
        m.put("{0} is over its monthly limit ({1}%).", "تجاوزت {0} حدها الشهري ({1}%).");
        m.put("You've used {0}% of your monthly limit.", "استخدمت {0}% من حدك الشهري.");
        m.put("{0} is at {1}% of its monthly limit.", "وصلت {0} إلى {1}% من حدها الشهري.");

        // ---- dashboard
        m.put("No monthly limit set", "لم يُحدَّد حد شهري");
        m.put("of {0} monthly limit", "من حد شهري قدره {0}");
        m.put("Remaining", "المتبقي");
        m.put("Set a monthly limit to see what's left", "ضع حدًا شهريًا لترى المتبقي");
        m.put("Over by {0}", "تجاوز بمقدار {0}");
        m.put("{0}% used", "مستخدم {0}%");
        m.put("Left per day", "المتبقي لكل يوم");
        m.put("{0} is over.", "انتهى {0}.");
        m.put("Your daily budget comes from your monthly limit.", "تُحسب ميزانيتك اليومية من حدك الشهري.");
        m.put("1 day left", "بقي يوم واحد");
        m.put("{0} days left", "الأيام المتبقية: {0}");
        m.put("You've used your whole monthly limit.", "استخدمت حدك الشهري بالكامل.");
        m.put("{0} · based on your monthly limit", "{0} · بحسب حدك الشهري");
        m.put("{0} / day", "{0} / يوم");
        m.put("Where it went", "أين ذهبت أموالك");
        m.put("No spending in {0} yet.", "لا توجد مصروفات في {0} بعد.");
        m.put("All limits", "كل الحدود");
        m.put("Limits", "الحدود");
        m.put("Set limits", "ضع حدودًا");
        m.put("No category limits yet. Give a category a monthly limit to see how you're doing.",
                "لا توجد حدود للفئات بعد. ضع حدًا شهريًا لفئة لترى كيف تسير.");

        // ---- categories
        m.put("Every category has its own color, used in every chart. Deleting one moves its transactions to Other (or Other income).",
                "لكل فئة لونها الخاص المستخدم في كل الرسوم. حذف فئة ينقل معاملاتها إلى «أخرى» (أو «دخل آخر»).");
        m.put("Expense categories", "فئات المصروفات");
        m.put("Income categories", "فئات الدخل");
        m.put("+ New", "+ جديدة");
        m.put("built-in · can't be deleted", "أساسية · لا يمكن حذفها");
        m.put("Couldn't load categories: {0}", "تعذّر تحميل الفئات: {0}");
        m.put("New expense category", "فئة مصروفات جديدة");
        m.put("New income category", "فئة دخل جديدة");
        m.put("Edit category", "تعديل الفئة");
        m.put("e.g. Coffee", "مثلًا قهوة");
        m.put("Greyed-out colors belong to other categories.", "الألوان الباهتة مستخدمة لفئات أخرى.");
        m.put("All 14 colors are in use, so colors can repeat now.", "الألوان الـ14 كلها مستخدمة، لذا يمكن تكرار الألوان الآن.");
        m.put("Keep it", "إبقاؤها");
        m.put("Yes, delete", "نعم، احذف");
        m.put("Couldn't load colors: {0}", "تعذّر تحميل الألوان: {0}");
        m.put("Category “{0}” added.", "أُضيفت الفئة «{0}».");
        m.put("Category “{0}” saved.", "حُفظت الفئة «{0}».");
        m.put("Category “{0}” deleted.", "حُذفت الفئة «{0}».");
        m.put("1 transaction moved to {0}.", "نُقلت معاملة واحدة إلى {0}.");
        m.put("{0} transactions moved to {1}.", "عدد المعاملات المنقولة إلى {1}: {0}.");
        m.put("Delete “{0}”? Its transactions move to {1}. This can't be undone.",
                "حذف «{0}»؟ ستنتقل معاملاتها إلى {1}. لا يمكن التراجع عن ذلك.");

        // ---- settings
        m.put("Appearance", "المظهر");
        m.put("Jeib remembers your choice for the next time you open it.", "يتذكر جيب اختيارك في المرة القادمة التي تفتحه فيها.");
        m.put("Light", "فاتح");
        m.put("Dark", "داكن");
        m.put("Main currency", "العملة الرئيسية");
        m.put("Totals, limits, and your daily budget are shown in it. You can still enter amounts in the other currencies.",
                "تُعرض بها المجاميع والحدود وميزانيتك اليومية، ويمكنك إدخال مبالغ بالعملات الأخرى أيضًا.");
        m.put("Switch", "تبديل");
        m.put("Switch to {0}? Your limits and exchange rates will be converted to {0}. Past transactions keep their own currency.",
                "التبديل إلى {0}؟ ستُحوَّل حدودك وأسعار الصرف إلى {0}، وتبقى المعاملات السابقة بعملتها الأصلية.");
        m.put("Exchange rates", "أسعار الصرف");
        m.put("Used to count other currencies in your totals. Update them whenever rates change; it affects how all amounts in that currency are counted.",
                "تُستخدم لاحتساب العملات الأخرى في مجاميعك. حدّثها كلما تغيّرت الأسعار، فهي تؤثر في احتساب كل المبالغ بتلك العملة.");
        m.put("Save rates", "حفظ الأسعار");
        m.put("not set", "غير محدد");
        m.put("Exchange rates saved.", "حُفظت أسعار الصرف.");
        m.put("Your main currency is now {0}. Limits and rates were converted.", "عملتك الرئيسية الآن {0}. حُوِّلت الحدود والأسعار.");
        m.put("Your data", "بياناتك");
        m.put("Everything stays on this computer, in one file:", "كل بياناتك تبقى على هذا الحاسوب، في ملف واحد:");
        return Map.copyOf(m);
    }
}
