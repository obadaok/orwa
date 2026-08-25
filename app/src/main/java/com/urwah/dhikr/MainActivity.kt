package com.urwah.dhikr

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.urwah.dhikr.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Urwah)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createChannel(this)
        initializeDefaultReminders()

        val navView: BottomNavigationView = binding.bottomNav
        val circularMenu = binding.circularMenu
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val navAnimOptions = androidx.navigation.NavOptions.Builder()
            .setEnterAnim(R.anim.nav_fade_in)
            .setExitAnim(R.anim.nav_fade_out)
            .setPopEnterAnim(R.anim.nav_fade_in)
            .setPopExitAnim(R.anim.nav_fade_out)
            .build()

        navView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_menu) {
                if (circularMenu.visibility == View.VISIBLE) {
                    circularMenu.hide()
                } else {
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        ?.childFragmentManager?.fragments?.firstOrNull()
                    if (currentFragment is CircularMenuProvider) {
                        circularMenu.clearMenuItems()
                        currentFragment.setupCircularMenu(circularMenu)
                    } else {
                        circularMenu.clearMenuItems()
                        setupDefaultCircularMenu(circularMenu, navController, navAnimOptions)
                    }
                    circularMenu.show()
                }
                return@setOnItemSelectedListener false
            }
            val currentDest = navController.currentDestination?.id
            if (currentDest == item.itemId) return@setOnItemSelectedListener false
            if (circularMenu.visibility == View.VISIBLE) {
                circularMenu.hide()
            }
            navController.navigate(item.itemId, null, navAnimOptions)
            true
        }

        setupDefaultCircularMenu(circularMenu, navController, navAnimOptions)

        checkConsent()
        showArabicIdentityDialogIfNeeded()
    }

    private fun checkConsent() {
        val prefs = getSharedPreferences(PREFS_CONSENT, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CONSENT_GIVEN, false)) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_consent, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<Button>(R.id.btnConsentPrivacy).setOnClickListener {
            showPolicyDialog(this, "privacy")
        }
        view.findViewById<Button>(R.id.btnConsentTerms).setOnClickListener {
            showPolicyDialog(this, "terms")
        }
        view.findViewById<Button>(R.id.btnConsentAgree).setOnClickListener {
            prefs.edit().putBoolean(KEY_CONSENT_GIVEN, true).apply()
            dialog.dismiss()
            showArabicIdentityDialogIfNeeded()
        }
        view.findViewById<Button>(R.id.btnConsentExit).setOnClickListener {
            finishAffinity()
        }
        dialog.show()
    }

    private fun showArabicIdentityDialogIfNeeded() {
        val prefs = getSharedPreferences(PREFS_CONSENT, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ARABIC_IDENTITY_SEEN, false)) return
        if (!prefs.getBoolean(KEY_CONSENT_GIVEN, false)) return
        showArabicIdentityDialog(this, markSeen = true)
    }

    override fun onResume() {
        super.onResume()
        rescheduleReminders()
    }

    private fun setupDefaultCircularMenu(
        circularMenu: UrwahCircularMenu,
        navController: androidx.navigation.NavController,
        navAnimOptions: androidx.navigation.NavOptions
    ) {
        circularMenu.addMenuItem(R.drawable.ic_search, "بحث") {
            val navFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            val childFragment = navFragment?.childFragmentManager?.fragments?.firstOrNull()
            if (childFragment is SearchableFragment) {
                childFragment.showSearch()
            } else {
                navController.navigate(R.id.nav_home, null, navAnimOptions)
            }
        }
        circularMenu.addMenuItem(R.drawable.ic_favorites, "المفضلة") {
            navController.navigate(R.id.nav_favorites, null, navAnimOptions)
        }
        circularMenu.addMenuItem(R.drawable.ic_statistics, "الإحصائيات") {
            navController.navigate(R.id.nav_stats, null, navAnimOptions)
        }
        circularMenu.addMenuItem(R.drawable.ic_dua, "الأدعية") {
            navController.navigate(R.id.nav_dua, null, navAnimOptions)
        }
        circularMenu.addMenuItem(R.drawable.ic_settings, "الإعدادات") {
            navController.navigate(R.id.nav_settings, null, navAnimOptions)
        }
        circularMenu.addMenuItem(R.drawable.ic_khatmah, "الختمات") {
            navController.navigate(R.id.nav_khatma, null, navAnimOptions)
        }
    }

    private fun initializeDefaultReminders() {
        val prefs = getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("defaults_initialized", false)) return

        prefs.edit().apply {
            putBoolean("defaults_initialized", true)
            putBoolean("${NotificationHelper.TYPE_MORNING}_enabled", true)
            putInt("${NotificationHelper.TYPE_MORNING}_hour", 6)
            putInt("${NotificationHelper.TYPE_MORNING}_min", 0)
            putBoolean("${NotificationHelper.TYPE_EVENING}_enabled", true)
            putInt("${NotificationHelper.TYPE_EVENING}_hour", 17)
            putInt("${NotificationHelper.TYPE_EVENING}_min", 0)
            putBoolean("${NotificationHelper.TYPE_BEDTIME}_enabled", true)
            putInt("${NotificationHelper.TYPE_BEDTIME}_hour", 22)
            putInt("${NotificationHelper.TYPE_BEDTIME}_min", 0)
            putBoolean("${NotificationHelper.TYPE_KAHF}_enabled", true)
            putInt("${NotificationHelper.TYPE_KAHF}_hour", 6)
            putInt("${NotificationHelper.TYPE_KAHF}_min", 0)
            putBoolean("${NotificationHelper.TYPE_KHATMA}_enabled", true)
            putInt("${NotificationHelper.TYPE_KHATMA}_hour", 8)
            putInt("${NotificationHelper.TYPE_KHATMA}_min", 0)
            apply()
        }
        NotificationHelper.scheduleAll(this)
    }

    private fun rescheduleReminders() {
        NotificationHelper.scheduleAll(this)
    }

    companion object {
        private const val PREFS_CONSENT = "urwah_consent"
        private const val KEY_CONSENT_GIVEN = "consent_given"
        private const val KEY_ARABIC_IDENTITY_SEEN = "arabic_identity_seen"

        fun showPolicyDialog(context: Context, type: String) {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_policy_viewer, null)
            val title = if (type == "privacy") "سياسة الخصوصية" else "شروط الاستخدام"
            val content = if (type == "privacy") PRIVACY_POLICY_TEXT else TERMS_TEXT
            view.findViewById<TextView>(R.id.tvPolicyTitle).text = title
            view.findViewById<TextView>(R.id.tvPolicyContent).text = content

            val dialog = AlertDialog.Builder(context)
                .setView(view)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            view.findViewById<Button>(R.id.btnPolicyClose).setOnClickListener { dialog.dismiss() }
            dialog.show()
        }

        fun showArabicIdentityDialog(context: Context, markSeen: Boolean = false) {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_arabic_identity, null)
            val dialog = AlertDialog.Builder(context)
                .setView(view)
                .setCancelable(false)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            view.findViewById<Button>(R.id.btnArabicIdentityContinue).setOnClickListener {
                if (markSeen) {
                    context.getSharedPreferences(PREFS_CONSENT, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_ARABIC_IDENTITY_SEEN, true).apply()
                }
                dialog.dismiss()
            }
            dialog.show()
        }

        val PRIVACY_POLICY_TEXT = """
عروة — سياسة الخصوصية

آخر تحديث: 10 أغسطس 2026

المطور: عبادة كمال

١. جمع البيانات
لا يقوم تطبيق عروة بجمع أي بيانات شخصية على الإطلاق. التطبيق لا يرسل أي بيانات عنك أو عن استخدامك إلى أي خادم خارجي. لا توجد تحليلات، ولا إعلانات، ولا خدمات تابعة لطرف ثالث تجمع أي معلومات عنك.

الاتصال الوحيد بالإنترنت هو عند تحميل الكتب من المكتبة الشاملة أو قراءتها مباشرة — ولا يتم إرسال أي معلومات عنك أثناء ذلك.

٢. التخزين المحلي
يخزن التطبيق جميع بياناتك محلياً على جهازك فقط، ولا يشاركها مع أي طرف ثالث. تشمل البيانات المخزنة:

القرآن والختمات:
- المصحف الشريف بروايتي حفص وورش
- تقدمك في الختمات (عدد الأيام، مواضع القراءة، الرواية)
- آخر موضع قراءة في المصحف وفي كل ختمة
- العلامات المرجعية على الآيات
- إعدادات عرض المصحف (حجم الخط، وضع العرض، التمرير التلقائي)

الأذكار والأدعية:
- إنجازات الأذكار اليومية (صباح، مساء، بعد الصلاة، نوم)
- الأدعية المفضلة والمحفوظة
- إعدادات التذكيرات (الأوقات، التفعيل)

المكتبة الشاملة والكتب:
- جميع محتويات الكتب المحملة (النصوص، الفهرس، البيانات الوصفية)
- آخر موضع قراءة في كل كتاب
- العلامات المرجعية في الكتب (مع حفظ موضع الحرف في النص)
- إعدادات القراءة لكل كتاب (حجم الخط، تباعد الأسطر، المحاذاة، نوع الخط، عرض القراءة، حجم الهامش)
- سجل البحث داخل الكتاب
- الكتب المفضلة

محرر الاقتباسات:
- الصور المنشأة من الاقتباسات (تُحفظ في معرض الجهاز أو ذاكرة التخزين المؤقتة)

الإعدادات العامة:
- الوضع الداكن، حجم الخط العام، الاهتزاز، إبقاء الشاشة قيد التشغيل
- الإشعارات المجدولة للتذكيرات

يمكنك مسح جميع بيانات التطبيق بالكامل من إعدادات النظام (الإعدادات ← التطبيقات ← عروة ← مسح التخزين).

٣. الصلاحيات المطلوبة — شرح تفصيلي

أ. صلاحية الإنترنت (INTERNET)
- لماذا نحتاجها: لتحميل الكتب من المكتبة الشاملة عند الطلب، أو لقراءتها مباشرة عبر الإنترنت دون تحميل.
- كيف نستخدمها: فقط عند النقر على "تحميل" أو "قراءة" لأي كتاب في المكتبة. يمكن استخدام جميع ميزات التطبيق الأخرى بدون إنترنت.
- لا نستخدمها: لجمع البيانات، أو الإعلانات، أو التحليلات، أو أي اتصال غير مصرح به.

ب. صلاحية حالة الشبكة (ACCESS_NETWORK_STATE)
- لماذا نحتاجها: للتحقق من وجود اتصال بالإنترنت قبل القراءة المباشرة أو التحقق من تحديثات الكتب، وعرض رسالة واضحة للمستخدم عند انقطاع الاتصال.
- كيف نستخدمها: فقط لمعرفة حالة الاتصال. لا تُقرأ بيانات الشبكة أو تُرسل.
- لا نستخدمها: لأي غرض آخر غير التحقق من توفر الاتصال.

ج. صلاحية الاهتزاز (VIBRATE)
- لماذا نحتاجها: رد فعل لمسي (haptic feedback) عند النقر على البطاقات والأزرار.
- كيف نستخدمها: فقط عند تفعيل خيار "الاهتزاز عند الضغط" في الإعدادات. يحدث الاهتزاز لحظياً عند النقر فقط.
- لا نستخدمها: لأي غرض آخر غير التفاعل مع واجهة المستخدم.

د. صلاحية الإشعارات (POST_NOTIFICATIONS)
- لماذا نحتاجها: لإرسال تذكيرات الأذكار في الأوقات التي يحددها المستخدم.
- كيف نستخدمها: فقط عند تفعيل التذكيرات. الإشعارات محلية بالكامل — لا يتم إرسال أي بيانات خارج جهازك.
- لا نستخدمها: للإعلانات أو الرسائل التسويقية أو أي غرض آخر.

هـ. صلاحية الجدولة الدقيقة (SCHEDULE_EXACT_ALARM)
- لماذا نحتاجها: لضمان وصول تذكيرات الأذكار في الوقت المحدد بدقة (مطلوبة في Android 12+).
- كيف نستخدمها: فقط للتذكيرات التي يضبطها المستخدم بوقت محدد، ويمكنك تعطيلها من إعدادات النظام.
- لا نستخدمها: لأي غرض آخر غير التذكيرات.

و. حفظ الصور
- تُحفظ صور الاقتباسات التي تنشئها عبر MediaStore (Android 10+) دون أي صلاحية تخزين.
- لا يطلب التطبيق صلاحيات قراءة أو كتابة الملفات العامة.

ز. صلاحيات أخرى (RECEIVE_BOOT_COMPLETED، FOREGROUND_SERVICE، WAKE_LOCK)
- RECEIVE_BOOT_COMPLETED: لإعادة جدولة تذكيرات الأذكار بعد إعادة تشغيل الجهاز.
- FOREGROUND_SERVICE وWAKE_LOCK: لتشغيل التلاوات الصوتية والحفاظ على تشغيلها في الخلفية عند الاستماع.

٤. المكتبة الشاملة والكتب
يحتوي التطبيق على مكتبة إلكترونية تضم آلاف الكتب العربية من المكتبة الشاملة. عند التعامل مع كتاب:
- تُحفظ صفحات أي كتاب يتم تحميله وفهرسه محلياً على جهازك للقراءة بدون إنترنت
- يمكن قراءة أي كتاب مباشرة عبر الإنترنت دون تحميله مسبقاً (تظهر أولى الصفحات أثناء الجلب التدريجي)
- يمكن حذف أي كتاب في أي وقت من قائمة الإجراءات (ضغط مطول)
- الكتب متاحة من مستودع Hugging Face (مصدر المكتبة الشاملة)
- يمكنك التحقق من تحديثات الكتب المحملة وتحديثها عند توفر نسخة أحدث دون فقدان موضع القراءة أو العلامات المرجعية
- يمكنك مشاركة رابط الكتاب مع الآخرين عبر خيار "مشاركة الكتاب"

٥. المحتوى الديني
- النصوص القرآنية مأخوذة من مصاحف موثوقة ومتاحة للجمهور
- الأذكار والأدعية من مصادر دينية موثوقة (حصن المسلم)
- محتوى المكتبة الشاملة هو محتوى عام متاح للجميع

٦. الخطوط
الخطوط المستخدمة في التطبيق هي خطوط مفتوحة المصدر ومتاحة للاستخدام التجاري (Alyamama، Noto Naskh Arabic، Amiri).

٧. الأطفال
التطبيق آمن للاستخدام من قبل جميع الأعمار. لا يحتوي على أي محتوى غير لائق ولا يجمع أي بيانات من الأطفال.

٨. التعديلات
قد يتم تحديث سياسة الخصوصية عند الحاجة. سيتم إعلام المستخدمين بأي تغييرات جوهرية عبر التطبيق.

٩. الاتصال بنا
للاستفسارات والدعم: hamdrake1@gmail.com
المطور: عبادة كمال
        """.trimIndent()

        val TERMS_TEXT = """
عروة — شروط الاستخدام

آخر تحديث: 10 أغسطس 2026

المطور: عبادة كمال

١. الموافقة
باستخدام تطبيق عروة، فإنك توافق على هذه الشروط. إذا كنت لا توافق، يرجى عدم استخدام التطبيق.

٢. الاستخدام المسموح
- يُسمح باستخدام التطبيق للأغراض الشخصية والدينية والتعليمية فقط
- لا يُسمح بنسخ أو تعديل أو إعادة نشر محتوى التطبيق أو كود المصدر لأغراض تجارية دون إذن كتابي
- جميع النصوص القرآنية متاحة للقراءة والتدبر والتلاوة في الروايتين (حفص وورش)
- محتوى المكتبة الشاملة متاح للاستخدام الشخصي والعلمي

٣. الميزات
يوفر التطبيق الميزات التالية:

المصحف الشريف:
- المصحف بروايتي حفص وورش مع العرض المتواصل
- نظام الختمات (إنشاء ختمات، متابعة القراءة اليومية، حفظ الموضع، اختيار الرواية)
- العلامات المرجعية على الآيات
- التمرير التلقائي أثناء القراءة
- إعدادات عرض (حجم الخط، وضع عرض الآيات)

الأذكار والأدعية:
- الأذكار اليومية (الصباح والمساء وبعد الصلاة والنوم)
- حصن المسلم (الأدعية المأخوذة من الكتاب)
- تذكيرات بالإشعارات للأوقات التي يحددها المستخدم
- إحصاءات إنجاز الأذكار

المكتبة الشاملة:
- تصفح المكتبة حسب الأقسام (المواضيع)
- تصفح الكتب حسب المؤلفين
- عرض الكتب المحملة
- عرض آخر الكتب المقروءة
- عرض الكتب المفضلة
- البحث في المكتبة (عن كتب ومؤلفين)
- تحميل الكتب للقراءة بدون إنترنت
- القراءة المباشرة للكتب عبر الإنترنت دون تحميل (مع عرض أولى الصفحات أثناء الجلب)
- التحقق من تحديثات الكتب المحملة وتحديثها عند توفر نسخة أحدث
- إلغاء التحميل وحذف الكتب

قارئ الكتب:
- تقليب صفحات بواقعية مع تمرير رأسي داخل الصفحة
- فهرس الكتاب مع تنقل سريع
- البحث داخل نص الكتاب
- الانتقال إلى صفحة محددة
- إعدادات قراءة مخصصة: حجم الخط، تباعد الأسطر، المحاذاة (يمين/وسط/يسار/ضبط)، نوع الخط، عرض القراءة، حجم الهامش
- إضافة وإدارة العلامات المرجعية (تحفظ موضع الحرف في النص لتبقى صحيحة حتى بعد تغيير الإعدادات)
- حفظ آخر موضع قراءة تلقائياً
- فتح الكتاب من البداية أو متابعة القراءة من حيث توقفت
- نسخ نص إلى محرر الاقتباسات

محرر الاقتباسات:
- تحديد النص المراد اقتباسه
- تطبيق تأثيرات: تمييز، غامق، تسطير، تكبير/تصغير الخط، تعتيم، إخفاء
- اختيار خلفيات متعددة (فاتح، دافئ، ورقي، عروة، ليلي، بني)
- تغيير المحاذاة (يمين، وسط، يسار، ضبط)
- تحكم في حجم الخط
- حفظ الصورة في معرض الجهاز أو مشاركتها مباشرة

الإعدادات العامة:
- الوضع الداكن
- حجم الخط العام
- الاهتزاز عند الضغط
- إبقاء الشاشة قيد التشغيل أثناء القراءة
- إدارة التذكيرات (تحديد أوقات الأذكار)

٤. المحتوى
- النصوص القرآنية كما هي من المصادر الموثوقة، والتطبيق غير مسؤول عن أي اختلافات في القراءات
- الأذكار والأدعية مأخوذة من مصادر دينية موثوقة
- محتوى المكتبة الشاملة متاح من مستودع Hugging Face — التطبيق لا يتحقق من صحة محتوى الكتب المنشورة
- يُنصح بالرجوع للمصادر الأصلية للتحقق من صحة النصوص

٥. تحميل الكتب والتخزين
- الكتب تُحمل وتُخزن محلياً على جهاز المستخدم للقراءة بدون إنترنت
- يمكن أيضًا قراءة الكتب مباشرة عبر الإنترنت دون تحميلها، ويتطلب ذلك اتصالاً بالإنترنت
- المستخدم هو المسؤول عن إدارة مساحة التخزين التي تشغلها الكتب المحملة
- يمكن حذف أي كتاب في أي وقت
- الكتب تُحمل من مستودع Hugging Face (المكتبة الشاملة) عبر اتصال إنترنت آمن
- عند تحديث كتاب يُحافظ على موضع القراءة والعلامات المرجعية والمفضلة
- التطبيق لا يشارك الكتب المحملة مع أي طرف آخر

٦. الإشعارات
باستخدام ميزة التذكيرات، توافق على استلام إشعارات على جهازك في الأوقات التي تحددها. يمكن إلغاء الإشعارات في أي وقت من إعدادات التطبيق أو النظام.

٧. المسؤولية
- التطبيق يُقدم "كما هو" دون أي ضمانات
- المطور غير مسؤول عن أي أضرار ناتجة عن استخدام التطبيق
- التطبيق غير مسؤول عن نسيان الأذكار أو التذكيرات
- قد تختلف القراءات القرآنية بين الروايات
- محتوى المكتبة الشاملة منقول من مصدر خارجي — التطبيق غير مسؤول عن دقة محتوى الكتب أو تحديثها

٨. الملكية الفكرية
- جميع حقوق التطبيق محفوظة للمطور: عبادة كمال
- المحتوى الديني (القرآن، الأذكار، الأدعية) هو ملك للجميع ومتاح للاستخدام الشخصي
- الخطوط المستخدمة مرخصة للاستخدام التجاري
- محتوى المكتبة الشاملة يُستخدم بموجب تراخيص المصادر المفتوحة

٩. التعديلات
تحتفظ إدارة التطبيق بالحق في تعديل هذه الشروط في أي وقت. سيتم إعلام المستخدمين بالتغييرات عبر التطبيق.

١٠. الاتصال
للاستفسارات والدعم: hamdrake1@gmail.com
المطور: عبادة كمال
        """.trimIndent()
    }
}
