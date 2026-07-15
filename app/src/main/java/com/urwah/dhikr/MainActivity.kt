package com.urwah.dhikr

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
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

        val navView: BottomNavigationView = binding.bottomNav
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navView.setupWithNavController(navController)

        checkConsent()
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
        }
        view.findViewById<Button>(R.id.btnConsentExit).setOnClickListener {
            finishAffinity()
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        rescheduleReminders()
    }

    private fun rescheduleReminders() {
        val prefs = getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
        val types = listOf(
            NotificationHelper.TYPE_MORNING,
            NotificationHelper.TYPE_EVENING,
            NotificationHelper.TYPE_BEDTIME
        )
        for (type in types) {
            val enabled = prefs.getBoolean("${type}_enabled", false)
            if (enabled) {
                val h = prefs.getInt("${type}_hour", 6)
                val m = prefs.getInt("${type}_min", 0)
                NotificationHelper.scheduleReminder(this, type, h, m)
            }
        }
    }

    companion object {
        private const val PREFS_CONSENT = "urwah_consent"
        private const val KEY_CONSENT_GIVEN = "consent_given"

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

        val PRIVACY_POLICY_TEXT = """
عروة — سياسة الخصوصية

آخر تحديث: 15 يوليو 2026

١. جمع البيانات
لا يقوم تطبيق عروة بجمع أي بيانات شخصية على الإطلاق. التطبيق لا يتصل بالإنترنت ولا يرسل أو يستقبل أي بيانات.

٢. التخزين المحلي
يخزن التطبيق بياناتك محلياً على جهازك فقط، وتشمل:
- تقدمك في الأذكار والإنجازات
- إشاراتك المرجعية في القرآن
- تقدمك في الختمات
- إعدادات التطبيق (الوضع الداكن، التذكيرات، إلخ)
- المفضلة
كل هذه البيانات تبقى على جهازك ولا تشارك مع أي طرف ثالث.

٣. الصلاحيات المطلوبة
- الاهتزاز: لتوفير رد فعل لمسي عند النقر على البطاقات والأزرار
- الإشعارات: لإرسال تذكيرات الأذكار في الأوقات المحددة
- الجدولة الدقيقة: لضمان وصول التذكيرات في الوقت المحدد بدقة

٤. المحتوى الديني
جميع النصوص القرآنية والأدعية والأذكار هي نصوص دينية متاحة للعموم. تم استخراج النصوص القرآنية من مصاحف موثوقة ومتاحة للجمهور.

٥. الخطوط
الخطوط المستخدمة في التطبيق هي خطوط مفتوحة المصدر ومتاحة للاستخدام التجاري.

٦. الأطفال
التطبيق آمن للاستخدام من قبل جميع الأعمار. لا يحتوي على أي محتوى غير لائق ولا يجمع أي بيانات من الأطفال.

٧. التعديلات
قد يتم تحديث سياسة الخصوصية عند الحاجة. سيتم إعلام المستخدمين بأي تغييرات جوهرية عبر التطبيق.

٨. الاتصال بنا
للاستفسارات: obada0k1993@gmail.com
        """.trimIndent()

        val TERMS_TEXT = """
عروة — شروط الاستخدام

آخر تحديث: 15 يوليو 2026

١. الموافقة
باستخدام تطبيق عروة، فإنك توافق على هذه الشروط. إذا كنت لا توافق، يرجى عدم استخدام التطبيق.

٢. الاستخدام المسموح
- يُسمح باستخدام التطبيق للأغراض الشخصية والدينية فقط
- لا يُسمح بنسخ أو تعديل أو إعادة نشر محتوى التطبيق لأغراض تجارية
- جميع النصوص القرآنية متاحة للقراءة والتدبر والتلاوة

٣. المحتوى
- النصوص القرآنية كما هي من المصادر الموثوقة، والتطبيق غير مسؤول عن أي اختلافات في القراءات
- الأذكار والأدعية مأخوذة من مصادر دينية موثوقة
- يُنصح بالرجوع للمصادر الأصلية للتحقق من صحة النصوص

٤. الإشعارات
باستخدام ميزة التذكيرات، توافق على استلام إشعارات على جهازك في الأوقات التي تحددها.

٥. المسؤولية
- التطبيق يُقدم "كما هو" دون أي ضمانات
- المطور غير مسؤول عن أي أضرار ناتجة عن استخدام التطبيق
- التطبيق غير مسؤول عن نسيان الأذكار أو التذكيرات

٦. الملكية الفكرية
- جميع حقوق التطبيق محفوظة للمطور
- المحتوى الديني هو ملك للجميع ومتاح للاستخدام الشخصي
- الخطوط المستخدمة مرخصة للاستخدام التجاري

٧. التعديلات
تحتفظ إدارة التطبيق بالحق في تعديل هذه الشروط في أي وقت. سيتم إعلام المستخدمين بالتغييرات.

٨. الاتصال
للاستفسارات والدعم: obada0k1993@gmail.com
        """.trimIndent()
    }
}
