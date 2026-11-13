# راهنمای انتشار در Google Play و دلیل هشدار Play Protect

## چرا سپر ایمنی گوگل پلی (Google Play Protect) موقع نصب APK ظاهر می‌شود؟

هشدار **«برنامه توسعه‌دهنده ناشناس / اسکن برنامه»** در Google Play Protect به‌دلیل وجود ویروس یا مشکل در کد این بازی **نیست**؛ بلکه به دلیل نحوه کارکرد سیستم امنیتی اندروید است:

1. هر فایل `.apk` که خارج از گوگل پلی (Sideload) نصب شود و با یک کلید جدید (Self-Signed Keystore) امضا شده باشد که قبلاً در سرورهای گوگل پلی ثبت نشده، به‌طور خودکار باعث فعال شدن **Play Protect** می‌شود.
2. هیچ ابزاری نمی‌تواند بدون ثبت و تأیید برنامه در **Google Play Console** این هشدار را در حالت نصب دستی دور بزند.
3. به‌محض اینکه برنامه را با فرمت **Android App Bundle (`.aab`)** در **Google Play Console** آپلود و منتشر کنید (و Google Play App Signing روی آن اعمال شود)، کاربران بدون هیچ هشدار Play Protect آن را مستقیم از گوگل پلی نصب می‌کنند.

---

## استانداردهای Google Play که در نسخه 2.0.0 رعایت شده است

- **فرمت رسمی Google Play (`.aab`):** فایل `SnakeGame.aab` با `aapt2 --proto-format` و `bundletool 1.15.1` ساخته و اعتبارسنجی شده است.
- **Target API Level 35 (Android 15):** مطابق با الزامات ۲۰۲۵/۲۰۲۶ گوگل پلی (`minSdk 21`, `targetSdk 35`, `compileSdk 35`).
- **سازگاری کامل با 16KB Page Size:** بدون هیچ کتابخانه بومی `.so` (۱۰۰٪ سازگار با اندروید ۱۵+).
- **Predictive Back (اندروید ۱۳ تا ۱۵):** پشتیبانی رسمی از `OnBackInvokedCallback` و `enableOnBackInvokedCallback="true"`.
- **Edge-to-Edge تمام‌صفحه:** پشتیبانی از `WindowInsets` و `displayCutout` (ناچ/پانچ‌هول).
- **برچسب رسمی بازی و قوانین بکاپ:** `appCategory="game"`, `isGame="true"`, `debuggable="false"`, `backup_rules.xml`, `data_extraction_rules.xml`.
- **حفظ حریم خصوصی (Zero Permissions):** بدون نیاز به هیچ دسترسی و همراه با متن آماده سیاست حریم خصوصی (`PRIVACY_POLICY.md`).
- **پروژه کامل Android Studio / Gradle:** فایل‌های `settings.gradle`, `build.gradle`, `app/build.gradle`, `proguard-rules.pro` آماده هستند.

---

## مراحل انتشار در Google Play Console

1. **تغییر شناسه پکیج (Application ID):** پیش از انتشار، در `app/build.gradle` و `AndroidManifest.xml` مقدار `com.snakestudio.snake` را به دامنه اختصاصی خودتان (مثلاً `com.yourname.snakepro`) تغییر دهید (پکیج در گوگل پلی باید یکتا باشد).
2. **ساخت کلید اختصاصی Upload Key:** پوشه `snake-android/` را در **Android Studio** باز کنید و از مسیر `Build > Generate Signed Bundle / APK > Android App Bundle` یک Keystore اختصاصی برای خودتان بسازید و خروجی `.aab` بگیرید (یا برای تست اولیه از همین `SnakeGame.aab` استفاده کنید).
3. **تکمیل صفحه فروشگاه در Play Console:**
   - آپلود فایل `.aab` در بخش **Production** یا **Closed Testing**.
   - قرار دادن لینک صفحه سیاست حریم خصوصی (متن آماده در `PRIVACY_POLICY.md` هست؛ می‌توانید روی GitHub Pages قرار دهید).
   - پر کردن فرم **Data Safety** (اعلام اینکه هیچ داده‌ای جمع‌آوری یا ارسال نمی‌شود).
   - آپلود آیکون ۵۱۲×۵۱۲، بنر ۱۰۲۴×۵۰۰ و حداقل ۲ اسکرین‌شات از گوشی.
4. پس از بررسی و تأیید گوگل پلی، بازی با امضای رسمی **Google Play App Signing** منتشر شده و هشدار Play Protect برای همیشه حذف می‌شود.
