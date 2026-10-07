با توجه به اینکه فایل `MainActivity.java` در لیست فایل‌های شما موجود است، من نسخه اصلاح شده و کامل را بر اساس منطق جدید (اضافه کردن منوی تنظیمات، انتخاب موتور جستجو و قابلیت Custom URL) آماده کرده‌ام.

**تغییرات اعمال شده:**
1.  **Menu Implementation:** اضافه شدن `onCreateOptionsMenu` و `onOptionsItemSelected` برای نمایش تنظیمات.
2.  **Search Engine Switcher:** یک `AlertDialog` که لیست موتورهای جستجو را نمایش می‌دهد.
3.  **Custom URL Support:** امکان وارد کردن یک URL دلخواه برای جستجو.
4.  **Persistence:** استفاده از `SharedPreferences` برای اینکه با بستن و باز کردن اپلیکیشن، انتخاب کاربر باقی بماند.
5.  **Redirection Fix:** حفظ کد قبلی برای جلوگیری از باز شدن مرورگرهای خارجی.

**لطفاً کد زیر را کپی کرده و در فایل `/FastDesk_Browser/app/src/main/java/com/gapgpt/fastdeskbrowser/MainActivity.java` جایگزین محتوای قبلی کنید:**

```java
package com.gapgpt.fastdeskbrowser;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "FastDeskPrefs";
    private static final String KEY_SEARCH_TEMPLATE = "searchTemplate";
    private static final String USER_AGENT_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36";

    // Templates
    private final String GOOGLE = "https://www.google.com/search?q=";
    private final String BING = "https://www.bing.com/search?q=";
    private final String DUCKDUCKGO = "https://duckduckgo.com/?q=";
    private final String BRAVE = "https://search.brave.com/search?q=";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupWebView();

        // Load initial page
        webView.loadUrl("https://www.google.com");
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        
        // [FIX] Prevent mobile detection/redirection
        settings.setUserAgentString(USER_AGENT_DESKTOP);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // [FIX] Intercept intent:// and other schemes to stay inside app
                if (url.startsWith("intent://") || url.contains("market://") || url.startsWith("tel:") || url.startsWith("mailto:")) {
                    return true; 
                }
                return false;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu); // Ensure menu_main.xml exists in res/menu
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            showSearchSettingsDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showSearchSettingsDialog() {
        String[] engines = {"Google", "Bing", "DuckDuckGo", "Brave", "Custom URL..."};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Search Engine");
        builder.setItems(engines, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: saveSearchTemplate(GOOGLE); break;
                    case 1: saveSearchTemplate(BING); break;
                    case 2: saveSearchTemplate(DUCKDUCKGO); break;
                    case 3: saveSearchTemplate(BRAVE); break;
                    case 4: showCustomUrlDialog(); break;
                }
            }
        });
        builder.show();
    }

    private void showCustomUrlDialog() {
        final EditText input = new EditText(this);
        input.setHint("e.g. https://example.com/search?q=");
        
        new AlertDialog.Builder(this)
                .setTitle("Enter Custom Search Template")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String customUrl = input.getText().toString();
                    if (!customUrl