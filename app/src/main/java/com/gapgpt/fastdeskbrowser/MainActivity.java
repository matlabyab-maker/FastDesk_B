package com.gapgpt.fastdeskbrowser;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.*;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.OpenableColumns;
import android.view.*;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_SAVE_EXPORT = 4101;
    private static final int REQ_SAVE_DOWNLOAD = 4102;
    private static final int REQ_UPLOAD = 4103;
    private WebView web;
    private EditText address;
    private TextView status, downloadButton, copyButton, appName;
    private ProgressBar progress;
    private LinearLayout root, toolbar, titleBar, navBar, tabStrip;
    private final ArrayList<TabState> tabs = new ArrayList<>();
    private int currentTab = 0;
    private boolean toolbarButtonsVisible = true;
    private boolean fullScreenEnabled = false;
    private TextView toolButton;
    private PopupWindow mouseWindow;
    private int mouseWindowWidth = 270;
    private int mouseWindowHeight = 255;
    private PopupWindow fullScreenExitWindow;
    private TextView mouseCursorButton;
    private float pageMouseX = 0.5f, pageMouseY = 0.35f;
    private boolean pageMouseReady = false;
    private long lastPointerUpdateMs = 0L;
    private float pendingPointerX = 0.5f, pendingPointerY = 0.35f;
    private boolean pointerUpdateScheduled = false;
    private boolean copyMode = false;
    private boolean textOnly = false;
    private boolean desktopMode = false;
    private boolean compactToolbar = false;
    private int browserViewScale = 100;
    private boolean androidTheme = false;
    private String searchTemplate = "https://www.google.com/search?q=%s";
    private String pendingExport = "";
    private String pendingDownloadUrl = "";
    private String pendingDownloadName = "download";
    private String detectedMediaUrl = "";
    private boolean mediaUiPending = false;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermissionRequest;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private android.content.SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static class TabState { Bundle state; String url; String title; TabState(Bundle b,String u,String t){state=b;url=u;title=t;} }
    private final Runnable hideStatusMessage = () -> { if (status != null && !fullScreenEnabled) status.setVisibility(View.GONE); };
    private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateNetworkStatus(); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("browser", MODE_PRIVATE);
        textOnly = prefs.getBoolean("textOnly", false);
        desktopMode = prefs.contains("desktop") ? prefs.getBoolean("desktop", true) : true;
        compactToolbar = prefs.getBoolean("compact", false);
        searchTemplate = prefs.getString("searchTemplate", searchTemplate);
        buildUi();
        configureWebView();
        if (state != null && web.restoreState(state) != null) {
            updateAddress(web.getUrl());
        }
        updateNetworkStatus();
        try { registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)); } catch (Exception ignored) {}
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(236,233,216));
        // Windows XP title bar
        titleBar = new LinearLayout(this); LinearLayout title = titleBar; title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(dp(8),dp(5),dp(6),dp(5));
        title.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xff0a59d5,0xff3a91ff,0xff0750b5}));
        ImageView xpIcon = new ImageView(this);
        xpIcon.setImageResource(com.gapgpt.fastdeskbrowser.R.drawable.ic_desktop_browser);
        xpIcon.setContentDescription("نمای دسکتاپ");
xpIcon.setClickable(true);
xpIcon.setFocusable(true);
xpIcon.setOnClickListener(v -> toggleDesktopFromXpButton(xpIcon));
title.addView(xpIcon,new LinearLayout.LayoutParams(dp(30),dp(30)));
        appName = new TextView(this); appName.setText("FastDesk Browser"); appName.setTextColor(Color.WHITE); appName.setTextSize(17); appName.setTypeface(null,1);
        title.addView(appName,new LinearLayout.LayoutParams(0,dp(36),1));
        toolButton = xpButton("Tool"); toolButton.setTextColor(Color.WHITE); toolButton.setTextSize(11); toolButton.setBackground(borderDrawable(0xff236acb,0xffd8e8ff));
        TextView setupButton = xpButton("Setup"); setupButton.setTextColor(Color.WHITE); setupButton.setTextSize(11); setupButton.setBackground(borderDrawable(0xff236acb,0xffd8e8ff));
        mouseCursorButton = xpButton("🖱 Mouse"); mouseCursorButton.setTextColor(Color.WHITE); mouseCursorButton.setTextSize(11); mouseCursorButton.setBackground(borderDrawable(0xff236acb,0xffd8e8ff));
        copyButton = xpButton("Copy Mini Win"); copyButton.setTextColor(Color.WHITE); copyButton.setTextSize(10); copyButton.setBackground(borderDrawable(0xff236acb,0xffd8e8ff));
        title.addView(toolButton,new LinearLayout.LayoutParams(dp(52),dp(34)));
        title.addView(setupButton,new LinearLayout.LayoutParams(dp(58),dp(34)));
        LinearLayout.LayoutParams mouseTitleParams = new LinearLayout.LayoutParams(dp(74),dp(34)); mouseTitleParams.setMargins(dp(2),0,dp(2),0); title.addView(mouseCursorButton,mouseTitleParams);
        title.addView(copyButton,new LinearLayout.LayoutParams(dp(72),dp(34)));
        toolButton.setOnClickListener(v -> showToolMenu()); setupButton.setOnClickListener(v -> showSettings()); mouseCursorButton.setOnClickListener(v -> toggleMouseWindow()); copyButton.setOnClickListener(v -> toggleCopyMode());
        TextView mini = xpButton("—"); TextView max = xpButton("□"); TextView close = xpButton("×");
        title.addView(mini); title.addView(max); title.addView(close);
        mini.setOnClickListener(v -> Toast.makeText(this,"برای ادامه، برنامه را به پس‌زمینه ببرید.",Toast.LENGTH_SHORT).show());
        max.setOnClickListener(v -> cycleWindowSize());
        close.setOnClickListener(v -> finish()); root.addView(title);

        navBar = new LinearLayout(this); LinearLayout nav = navBar; nav.setPadding(dp(5),dp(5),dp(5),dp(4)); nav.setGravity(Gravity.CENTER_VERTICAL); nav.setBackgroundColor(0xffece9d8);
        TextView back = xpButton("◀  عقب"); TextView forward = xpButton("جلو  ▶"); back.setTextSize(15); forward.setTextSize(15);
        nav.addView(back,new LinearLayout.LayoutParams(dp(92),dp(45))); nav.addView(forward,new LinearLayout.LayoutParams(dp(92),dp(45)));
        back.setOnClickListener(v -> { if(web.canGoBack()) web.goBack(); }); forward.setOnClickListener(v -> { if(web.canGoForward()) web.goForward(); });
        address = new EditText(this); address.setSingleLine(true); address.setTextSize(14); address.setHint("آدرس سایت یا عبارت جست‌وجو"); address.setPadding(dp(8),0,dp(8),0); address.setSelectAllOnFocus(false); address.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setBackground(borderDrawable(0xffffffff,0xff7f9db9));
        nav.addView(address,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView go = xpButton("برو"); nav.addView(go,new LinearLayout.LayoutParams(dp(48),dp(42)));
        go.setOnClickListener(v -> navigateFromAddress()); address.setOnEditorActionListener((v,action,event)->{navigateFromAddress();return true;});
        tabStrip = new LinearLayout(this); tabStrip.setOrientation(LinearLayout.HORIZONTAL); tabStrip.setGravity(Gravity.CENTER_VERTICAL); tabStrip.setPadding(dp(3),dp(2),dp(3),dp(2)); tabStrip.setBackgroundColor(0xffd6d2c4);
        root.addView(tabStrip,new LinearLayout.LayoutParams(-1,dp(34)));
        root.addView(nav);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgress(0);
        toolbar = new LinearLayout(this); toolbar.setVisibility(View.GONE);
        status = new TextView(this); status.setVisibility(View.GONE); status.setTextSize(11);
        // Treat the top status/message strip as a temporary notification: hide it 3 seconds after each new message.
        status.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(hideStatusMessage);
                status.setVisibility(fullScreenEnabled ? View.GONE : View.VISIBLE);
                if (!fullScreenEnabled) handler.postDelayed(hideStatusMessage, 3000);
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        handler.postDelayed(hideStatusMessage, 3000);
        web = new WebView(this);
        // Use WebView's native pinch-to-zoom handling. Do not intercept touch events here:
        // manual span/JavaScript zoom caused jitter, mixed zoom direction, and touch hangs.
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        createInitialTab();
    }



    private void toggleDesktopFromXpButton(ImageView xpIcon) {
        desktopMode = !desktopMode;
        prefs.edit().putBoolean("desktop", desktopMode).apply();
        applyUserAgent();
        if (web != null) {
            web.stopLoading();
            web.setInitialScale(0);
            web.clearFocus();
            String u = web.getUrl();
            if (u != null && !u.isEmpty()) web.reload();
        }
        xpIcon.setAlpha(desktopMode ? 1.0f : 0.78f);
        xpIcon.setContentDescription(desktopMode ? "نمای دسکتاپ فعال" : "نمای موبایل فعال");
        if (status != null) status.setText(desktopMode
                ? "نمای دسکتاپ فعال شد | چیدمان شبیه مرورگر کامپیوتر"
                : "نمای موبایل فعال شد | چیدمان واکنش‌گرا");
    }

    private void toggleMouseWindow() {
        if (mouseWindow != null && mouseWindow.isShowing()) {
            mouseWindow.dismiss();
            mouseWindow = null;
            if (mouseCursorButton != null) mouseCursorButton.setText("🖱 موس");
            return;
        }
        showMouseWindow();
    }

    private void showMouseWindow() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(borderDrawable(0xffece9d8,0xff142b57));
        panel.setPadding(dp(3),dp(3),dp(3),dp(3));
        TextView pad = new TextView(this); pad.setText("ناحیه حرکت نشانگر\nانگشت را بکشید"); pad.setGravity(Gravity.CENTER); pad.setTextColor(0xff23466f); pad.setTextSize(14);
        pad.setBackground(borderDrawable(0xfff9fbff,0xff9ab3d0));
        FrameLayout.LayoutParams padParams = new FrameLayout.LayoutParams(-1,-1);
        padParams.setMargins(dp(5),dp(5),dp(5),dp(51));
        panel.addView(pad,padParams);
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(4),0,dp(4),dp(4));
        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(-1,dp(47),Gravity.BOTTOM);
        panel.addView(controls,controlParams);
        TextView left = xpButton("کلیک چپ"); left.setTextSize(14); controls.addView(left,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView recenter = xpButton("مرکز"); controls.addView(recenter,new LinearLayout.LayoutParams(dp(62),dp(42)));
        TextView resize = xpButton("↘ تغییر اندازه"); resize.setTextSize(11); controls.addView(resize,new LinearLayout.LayoutParams(dp(94),dp(42)));
        TextView drag = xpButton("☰ درگ"); drag.setTextSize(11); controls.addView(drag,new LinearLayout.LayoutParams(dp(58),dp(42)));
        mouseWindowWidth=270; mouseWindowHeight=255; mouseWindow = new PopupWindow(panel,dp(mouseWindowWidth),dp(mouseWindowHeight),false);
        mouseWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        mouseWindow.setOutsideTouchable(false); mouseWindow.setTouchable(true); mouseWindow.setClippingEnabled(true);
        final int[] popupPos = {Math.max(0, getResources().getDisplayMetrics().widthPixels-dp(282)), dp(100)};
        mouseWindow.setOnDismissListener(() -> hidePagePointer());
        mouseWindow.showAtLocation(root,Gravity.TOP|Gravity.START,popupPos[0],popupPos[1]);
        mouseCursorButton.setText("🖱 باز");
        final float[] dragStart = {0f,0f};
        final int[] dragOrigin = {popupPos[0],popupPos[1]};
        drag.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    dragStart[0]=e.getRawX(); dragStart[1]=e.getRawY();
                    dragOrigin[0]=popupPos[0]; dragOrigin[1]=popupPos[1];
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int nx=(int)(dragOrigin[0]+e.getRawX()-dragStart[0]);
                    int ny=(int)(dragOrigin[1]+e.getRawY()-dragStart[1]);
                    int sw=getResources().getDisplayMetrics().widthPixels;
                    int sh=getResources().getDisplayMetrics().heightPixels;
                    nx=Math.max(0,Math.min(nx,Math.max(0,sw-dp(mouseWindowWidth))));
                    ny=Math.max(0,Math.min(ny,Math.max(0,sh-dp(mouseWindowHeight))));
                    popupPos[0]=nx; popupPos[1]=ny;
                    if(mouseWindow!=null && mouseWindow.isShowing()) mouseWindow.update(nx,ny,-1,-1);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return true;
        });
        final float[] last={0,0};
        pad.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){last[0]=e.getX();last[1]=e.getY();pad.setText("● نشانگر فعال");return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getX()-last[0],dy=e.getY()-last[1];last[0]=e.getX();last[1]=e.getY();movePagePointer(dx,dy);return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){return true;}return true;
        });
        left.setOnClickListener(v -> dispatchPageMouseClick());
        recenter.setOnClickListener(v -> {pageMouseX=.5f;pageMouseY=.35f;pageMouseReady=true;updatePagePointer();});
        resize.setOnClickListener(v -> showMouseResizeDialog());
        updatePagePointer();
    }

    private void movePagePointer(float dx,float dy){
        if(web==null || web.getWidth()<=0 || web.getHeight()<=0)return;
        if(!pageMouseReady){pageMouseX=.5f;pageMouseY=.5f;pageMouseReady=true;}

        // Keep the pointer in the complete visible WebView area.  The page's
        // CSS viewport can have a different size from Android pixels, so the
        // movement is accumulated in normalized WebView coordinates and then
        // converted to the actual CSS viewport by updatePagePointer().
        pageMouseX=Math.max(0f,Math.min(1f,pageMouseX+dx/(float)Math.max(1,web.getWidth())));
        pageMouseY=Math.max(0f,Math.min(1f,pageMouseY+dy/(float)Math.max(1,web.getHeight())));
        updatePagePointer();
    }
    private void updatePagePointer(){
        if(web==null)return;
        pendingPointerX=pageMouseX; pendingPointerY=pageMouseY;
        long now=SystemClock.uptimeMillis();
        if(pointerUpdateScheduled || now-lastPointerUpdateMs<24) {
            if(!pointerUpdateScheduled){
                pointerUpdateScheduled=true;
                handler.postDelayed(()->{pointerUpdateScheduled=false; updatePagePointer();},24);
            }
            return;
        }
        lastPointerUpdateMs=now;
        final float px=pendingPointerX, py=pendingPointerY;
        String js="(function(){try{var vw=Math.max(1,document.documentElement.clientWidth||innerWidth),vh=Math.max(1,document.documentElement.clientHeight||innerHeight);var x=Math.max(0,Math.min(vw-1,Math.round((vw-1)*"+px+")));var y=Math.max(0,Math.min(vh-1,Math.round((vh-1)*"+py+")));var c=document.getElementById('__fastdesk_browser_cursor');if(!c){c=document.createElement('div');c.id='__fastdesk_browser_cursor';c.style.cssText='position:fixed;z-index:2147483647;width:13px;height:18px;pointer-events:none;background:#ffd400;border:1px solid #8a6f00;clip-path:polygon(0 0,0 100%,28% 73%,48% 100%,62% 92%,43% 66%,78% 66%);filter:drop-shadow(1px 1px 1px #555);';document.documentElement.appendChild(c);}c.style.left=x+'px';c.style.top=y+'px';window.__fastdeskMouseX=x;window.__fastdeskMouseY=y;}catch(e){}})()";
        web.evaluateJavascript(js,null);
    }
    private void hidePagePointer(){
        if(web==null)return;
        pointerUpdateScheduled=false;
        web.evaluateJavascript("(function(){var c=document.getElementById('__fastdesk_browser_cursor');if(c)c.remove();window.__fastdeskMouseX=null;window.__fastdeskMouseY=null;})()",null);
    }

    private void dispatchPageMouseClick(){
        if(web==null)return; updatePagePointer();
        String js="(function(){try{var x=window.__fastdeskMouseX||Math.round(innerWidth*.5),y=window.__fastdeskMouseY||Math.round(innerHeight*.35);var e=document.elementFromPoint(x,y);if(!e)return;['mousemove','mousedown','mouseup','click'].forEach(function(t){e.dispatchEvent(new MouseEvent(t,{view:window,bubbles:true,cancelable:true,clientX:x,clientY:y,button:0,buttons:t==='mousedown'?1:0}));});if(e.focus)e.focus();}catch(e){}})()";
        web.evaluateJavascript(js,null);
    }
    private void showMouseResizeDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(6),dp(12),dp(6));
        TextView label=new TextView(this);label.setText("عرض پنجره موس");box.addView(label);
        SeekBar width=new SeekBar(this);width.setMax(250);width.setProgress(100);box.addView(width);
        TextView label2=new TextView(this);label2.setText("ارتفاع پنجره موس");box.addView(label2);
        SeekBar height=new SeekBar(this);height.setMax(300);height.setProgress(100);box.addView(height);
        new AlertDialog.Builder(this).setTitle("تغییر اندازه پنجره شناور").setView(box).setPositiveButton("اعمال",(d,w)->{
            int nw=Math.max(dp(238),dp(170+width.getProgress()));
            int nh=Math.max(dp(112),dp(180+height.getProgress()));
            mouseWindowWidth=(int)Math.ceil(nw/getResources().getDisplayMetrics().density);
            mouseWindowHeight=(int)Math.ceil(nh/getResources().getDisplayMetrics().density);
            if(mouseWindow!=null&&mouseWindow.isShowing()){
                int sw=getResources().getDisplayMetrics().widthPixels;
                int sh=getResources().getDisplayMetrics().heightPixels;
                popupPosClampAndUpdate(sw,sh);
            }
        }).setNegativeButton("لغو",null).show();
    }

    private void popupPosClampAndUpdate(int sw,int sh){
        if(mouseWindow==null||!mouseWindow.isShowing())return;
        int[] loc=new int[2];
        // PopupWindow has no public position getter; keep its last requested position in the
        // drag listener. This helper is only used to resize without losing the current position.
        // update(-1,-1) preserves the current position while changing size.
        mouseWindow.update(-1,-1,dp(mouseWindowWidth),dp(mouseWindowHeight));
    }

    private void cycleWindowSize() {
        // On Android phones the OS owns the Activity window size. This control therefore
        // changes the browser viewport scale, including a genuinely smaller-than-normal view.
        if (browserViewScale >= 115) browserViewScale = 70;
        else if (browserViewScale >= 100) browserViewScale = 85;
        else if (browserViewScale >= 85) browserViewScale = 70;
        else if (browserViewScale >= 70) browserViewScale = 100;
        else browserViewScale = 85;
        if (web != null) {
            web.setInitialScale(browserViewScale);
            web.getSettings().setUseWideViewPort(true);
            web.getSettings().setLoadWithOverviewMode(browserViewScale < 100);
        }
        Toast.makeText(this, "اندازه نمایش مرورگر: " + browserViewScale + "%", Toast.LENGTH_SHORT).show();
    }

    private void createInitialTab(){ if(tabs.isEmpty()){ tabs.add(new TabState(null,prefs.getString("lastUrl","https://www.google.com"),"Google")); } rebuildTabs(); loadUrl(tabs.get(0).url); }
    private void rebuildTabs(){ if(tabStrip==null)return; tabStrip.removeAllViews(); for(int i=0;i<tabs.size();i++){ final int idx=i; TextView t=xpButton(tabTitle(tabs.get(i),i)); t.setTextSize(10); t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.END); t.setBackground(borderDrawable(i==currentTab?0xffffffff:0xffc9c6b8,0xff6f7f95)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(28),1); lp.setMargins(dp(1),0,dp(1),0); tabStrip.addView(t,lp); t.setOnClickListener(v->switchTab(idx)); t.setOnLongClickListener(v->{if(tabs.size()>1){tabs.remove(idx); currentTab=Math.min(currentTab,tabs.size()-1); TabState st=tabs.get(currentTab); if(st.state!=null)web.restoreState(st.state); else loadUrl(st.url); rebuildTabs();} return true;}); } TextView plus=xpButton("+"); plus.setTextSize(16); tabStrip.addView(plus,new LinearLayout.LayoutParams(dp(34),dp(28))); plus.setOnClickListener(v->newTab()); }
    private String tabTitle(TabState t,int i){String x=t.title; if(x==null||x.trim().isEmpty())x=t.url; if(x==null||x.trim().isEmpty())x="تب "+(i+1); return x.length()>18?x.substring(0,17)+"…":x;}
    private void saveCurrentTab(){ if(web==null||tabs.isEmpty())return; Bundle b=new Bundle(); try{web.saveState(b);}catch(Exception ignored){} TabState t=tabs.get(currentTab); t.state=b; t.url=web.getUrl(); t.title=web.getTitle(); }
    private void switchTab(int idx){ if(idx<0||idx>=tabs.size()||idx==currentTab)return; saveCurrentTab(); currentTab=idx; TabState t=tabs.get(idx); web.stopLoading(); web.clearHistory(); if(t.state!=null){ try{web.restoreState(t.state);}catch(Exception e){loadUrl(t.url);} } else loadUrl(t.url==null?"https://www.google.com":t.url); rebuildTabs(); updateAddress(web.getUrl()); }
    private void newTab(){ saveCurrentTab(); tabs.add(new TabState(null,"about:home","خانه")); currentTab=tabs.size()-1; web.stopLoading(); showHome(); rebuildTabs(); address.setText(""); }
    private void closeCurrentTab(){ if(tabs.size()<=1)return; tabs.remove(currentTab); currentTab=Math.max(0,currentTab-1); TabState t=tabs.get(currentTab); if(t.state!=null)web.restoreState(t.state); else loadUrl(t.url); rebuildTabs(); }

    private void configureWebView() {
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(prefs.getBoolean("javascript", true)); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setSupportMultipleWindows(false);
        s.setLoadsImagesAutomatically(!textOnly); s.setBlockNetworkImage(textOnly); s.setMediaPlaybackRequiresUserGesture(false); s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setAllowFileAccess(false); s.setAllowContentAccess(true); s.setCacheMode(prefs.getBoolean("noCache", false) ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        if(Build.VERSION.SDK_INT >= 21) { s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); CookieManager.getInstance().setAcceptThirdPartyCookies(web, true); }
        s.setSupportMultipleWindows(false); s.setJavaScriptCanOpenWindowsAutomatically(true);
        applyUserAgent(); web.addJavascriptInterface(new PageBridge(),"MiniWinBridge");
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){updateAddress(url);setOnlineTitle();progress.setVisibility(fullScreenEnabled?View.GONE:View.VISIBLE);progress.setProgress(5);status.setText("در حال بارگذاری… | "+networkDescription());}
            @Override public void onPageFinished(WebView view,String url){updateAddress(url);progress.setProgress(100);handler.postDelayed(()->progress.setVisibility(View.GONE),120);prefs.edit().putString("lastUrl",url).apply();rememberHistory(url,view.getTitle()); if(!tabs.isEmpty()){tabs.get(currentTab).url=url;tabs.get(currentTab).title=view.getTitle();rebuildTabs();} restoreFormStateIfNeeded(url);if(isOnline()) { appName.setText("🌐  FastDesk Browser"); status.setText("بارگذاری تمام شد | "+networkDescription()+(textOnly?" | فقط متن":"")); } else setOfflineUi();if(copyMode) injectCopyScript();if(desktopMode) { enforceDesktopViewport(); web.getSettings().setLoadWithOverviewMode(false); }if(!prefs.getStringSet("extensions",new HashSet<>()).isEmpty()) runExtensions();}
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error){super.onReceivedError(view,request,error);if(request.isForMainFrame()){progress.setVisibility(View.GONE);if(!isOnline())setOfflineUi();else{appName.setText("🌐  FastDesk Browser");status.setText("خطا در بازکردن سایت: "+error.getDescription()+" | برای تلاش دوباره بارگذاری کنید");}}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){super.onReceivedHttpError(view,request,response);if(request.isForMainFrame())status.setText("پاسخ سایت: HTTP "+response.getStatusCode()+" | "+networkDescription());}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){String u=req.getUrl().toString(); if(u.startsWith("http://")||u.startsWith("https://")) return false; try { Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse(u)); startActivity(i); } catch(Exception ignored) { Toast.makeText(MainActivity.this,"برنامه‌ای برای بازکردن این پیوند پیدا نشد",Toast.LENGTH_SHORT).show(); } return true;}
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){if(url==null)return false;if(url.startsWith("http://")||url.startsWith("https://"))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                // Keep this callback extremely light: it runs for many subresources on a page.
                // Only inspect likely media URLs and avoid posting duplicate UI work.
                String u=request.getUrl().toString();
                String low=u.toLowerCase(Locale.ROOT);
                if(low.contains(".mp4")||low.contains(".webm")||low.contains(".m4v")||low.contains(".m3u8")||low.contains(".mpd")){
                    detectedMediaUrl=u;
                    if(!mediaUiPending){ mediaUiPending=true; handler.post(()->{mediaUiPending=false; setVideoReady(true);}); }
                }
                return super.shouldInterceptRequest(view,request);
            }
});
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){progress.setProgress(p);}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);try{startActivityForResult(i,REQ_UPLOAD);}catch(Exception e){fileCallback=null;Toast.makeText(MainActivity.this,"انتخابگر فایل در دسترس نیست",Toast.LENGTH_SHORT).show();return false;}return true;}
            @Override public void onPermissionRequest(PermissionRequest request){runOnUiThread(()->askWebPermission(request));}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){new AlertDialog.Builder(MainActivity.this).setTitle("دسترسی مکانی سایت").setMessage(origin+" درخواست موقعیت مکانی دارد. فقط در صورت اعتماد اجازه دهید.").setPositiveButton("ادامه",(d,w)->{pendingGeoOrigin=origin;pendingGeoCallback=callback;if(androidx.core.content.ContextCompat.checkSelfPermission(MainActivity.this,android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED||androidx.core.content.ContextCompat.checkSelfPermission(MainActivity.this,android.Manifest.permission.ACCESS_COARSE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)finishGeoPermission(true);else requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},4202);}).setNegativeButton("رد",(d,w)->callback.invoke(origin,false,false)).show();}
        });
        web.setDownloadListener((url,ua,contentDisposition,mimeType,length)->promptDownload(url,guessName(url,contentDisposition)));
    }

    private void askWebPermission(PermissionRequest request){String[] resources=request.getResources();ArrayList<String> androidPermissions=new ArrayList<>();for(String r:resources){if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r))androidPermissions.add(android.Manifest.permission.RECORD_AUDIO);if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r))androidPermissions.add(android.Manifest.permission.CAMERA);}if(androidPermissions.isEmpty()){new AlertDialog.Builder(this).setTitle("درخواست دسترسی سایت").setMessage("این سایت درخواست دسترسی به قابلیت دستگاه دارد. اجازه فقط برای همین درخواست داده می‌شود.").setPositiveButton("اجازه",(d,w)->request.grant(resources)).setNegativeButton("رد",(d,w)->request.deny()).show();return;}pendingWebPermissionRequest=request;new AlertDialog.Builder(this).setTitle("دسترسی صدا/دوربین").setMessage("سایت "+web.getUrl()+" درخواست استفاده از میکروفون یا دوربین دارد. فقط اگر تماس یا قابلیت صوتی/تصویری را خودتان شروع کرده‌اید اجازه دهید.").setPositiveButton("ادامه",(d,w)->{ArrayList<String> missing=new ArrayList<>();for(String p:androidPermissions)if(androidx.core.content.ContextCompat.checkSelfPermission(this,p)!=android.content.pm.PackageManager.PERMISSION_GRANTED)missing.add(p);if(missing.isEmpty())grantPendingWebPermission();else requestPermissions(missing.toArray(new String[0]),4201);}).setNegativeButton("رد",(d,w)->{pendingWebPermissionRequest=null;request.deny();}).show();}
    private void grantPendingWebPermission(){if(pendingWebPermissionRequest==null)return;for(String r:pendingWebPermissionRequest.getResources()){if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)&&androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingWebPermissionRequest.deny();pendingWebPermissionRequest=null;return;}if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)&&androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingWebPermissionRequest.deny();pendingWebPermissionRequest=null;return;}}pendingWebPermissionRequest.grant(pendingWebPermissionRequest.getResources());pendingWebPermissionRequest=null;}
    private void finishGeoPermission(boolean granted){if(pendingGeoCallback!=null){pendingGeoCallback.invoke(pendingGeoOrigin,granted,false);pendingGeoCallback=null;pendingGeoOrigin=null;}}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==4201){boolean ok=grantResults.length>0;for(int g:grantResults)ok&=g==android.content.pm.PackageManager.PERMISSION_GRANTED;if(ok)grantPendingWebPermission();else if(pendingWebPermissionRequest!=null){pendingWebPermissionRequest.deny();pendingWebPermissionRequest=null;}}else if(requestCode==4202){boolean ok=false;for(int g:grantResults)ok|=g==android.content.pm.PackageManager.PERMISSION_GRANTED;finishGeoPermission(ok);}}


    private void saveBrowserSettings(){
        boolean ok=prefs.edit().putBoolean("textOnly",textOnly).putBoolean("desktop",desktopMode).putBoolean("compact",compactToolbar).commit();
        Toast.makeText(this,ok?"تنظیمات مرورگر ذخیره شد":"ذخیره تنظیمات ناموفق بود",Toast.LENGTH_SHORT).show();
    }

    private void showToolMenu(){
        final String[] actions = {"نمایش / پنهان‌کردن دکمه‌های نوار بالا", "New Tab", "Full Screen", "ابزارهای مرورگر", "منابع افزونه‌ها و برنامک‌ها"};
        new AlertDialog.Builder(this).setTitle("Tool").setItems(actions,(dialog,which)->{
            switch(which){
                case 0: toggleToolbarButtons(); break;
                case 1: newTab(); break;
                case 2: toggleFullScreen(); break;
                case 3: showTools(); break;
                case 4: showExtensionSources(); break;
            }
        }).setNegativeButton("بستن",null).show();
    }

    private void toggleToolbarButtons(){
        toolbarButtonsVisible = !toolbarButtonsVisible;
        for(int i=0;i<toolbar.getChildCount();i++){
            View child=toolbar.getChildAt(i);
            if(child!=toolButton) child.setVisibility(toolbarButtonsVisible?View.VISIBLE:View.GONE);
        }
        toolButton.setText(toolbarButtonsVisible?"Tool ▾":"Tool ▸");
        Toast.makeText(this,toolbarButtonsVisible?"دکمه‌های نوار بالا آشکار شدند":"دکمه‌های نوار بالا پنهان شدند",Toast.LENGTH_SHORT).show();
    }

    private void toggleFullScreen(){
        fullScreenEnabled=!fullScreenEnabled;
        int chromeVisibility=fullScreenEnabled?View.GONE:View.VISIBLE;
        titleBar.setVisibility(chromeVisibility);
        navBar.setVisibility(chromeVisibility);
        progress.setVisibility(fullScreenEnabled?View.GONE:View.VISIBLE);
        if(tabStrip!=null) tabStrip.setVisibility(chromeVisibility);
        if(toolbar!=null) toolbar.setVisibility(View.GONE);
        if(status!=null) status.setVisibility(View.GONE);
        if(android.os.Build.VERSION.SDK_INT>=19){
            if(fullScreenEnabled){
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }else{
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
        if(fullScreenEnabled) showFullScreenExitButton();
        else hideFullScreenExitButton();
        Toast.makeText(this,fullScreenEnabled?"حالت تمام‌صفحه فعال شد":"حالت تمام‌صفحه غیرفعال شد",Toast.LENGTH_SHORT).show();
    }

    private void showFullScreenExitButton(){
        if(fullScreenExitWindow!=null && fullScreenExitWindow.isShowing())return;
        TextView b=xpButton("⤢ خروج");
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setBackground(borderDrawable(0xcc236acb,0xffd8e8ff));
        b.setOnClickListener(v->toggleFullScreen());
        fullScreenExitWindow=new PopupWindow(b,dp(72),dp(38),false);
        fullScreenExitWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        fullScreenExitWindow.setClippingEnabled(true);
        fullScreenExitWindow.setTouchable(true);
        fullScreenExitWindow.setOutsideTouchable(false);
        fullScreenExitWindow.showAtLocation(root,Gravity.TOP|Gravity.END,dp(8),dp(8));
    }

    private void hideFullScreenExitButton(){
        if(fullScreenExitWindow!=null){
            fullScreenExitWindow.dismiss();
            fullScreenExitWindow=null;
        }
    }

    private void showTools(){
        final String[] tools={"تب جدید","تنظیمات مرورگر","ترجمه صفحه","ذخیره صفحه","آپلود چندگانه","افزودن نشانک فعلی","مدیریت نشانک‌ها","تاریخچه مرور","دانلود رسانه/فایل","اشتراک صفحه","بارگذاری دوباره","منابع افزونه‌ها و برنامک‌ها"};
        new AlertDialog.Builder(this).setTitle("ابزارها").setItems(tools,(d,which)->{
            switch(which){
                case 0: newTab(); break;
                case 1: showSettings(); break;
                case 2: translatePage(); break;
                case 3: showSaveMenu(); break;
                case 4: chooseUploads(); break;
                case 5: addBookmark(); break;
                case 6: showBookmarks(); break;
                case 7: showHistory(); break;
                case 8: downloadDetectedOrPrompt(); break;
                case 9: shareCurrentPage(); break;
                case 10: if(web!=null)web.reload(); break;
                case 11: showExtensionSources(); break;
            }
        }).setNegativeButton("بستن",null).show();
    }

    private void showExtensionSources(){
        final String[] names={"Chrome Web Store","Firefox Add-ons","Microsoft Edge Add-ons","Greasy Fork (اسکریپت‌های کاربری)","OpenUserJS","GitHub (پروژه‌های افزونه)"};
        final String[] urls={"https://chromewebstore.google.com/","https://addons.mozilla.org/","https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home","https://greasyfork.org/","https://openuserjs.org/","https://github.com/topics/browser-extension"};
        new AlertDialog.Builder(this).setTitle("منابع افزونه‌ها و برنامک‌ها").setMessage("این منابع برای مرور و دریافت افزونه‌ها هستند. افزونه‌های اختصاصی Chrome/Firefox مستقیماً در Android WebView نصب نمی‌شوند؛ کدهای JavaScript سفارشی این مرورگر از بخش مدیریت افزونه‌ها اداره می‌شوند.").setItems(names,(d,which)->loadUrl(urls[which])).setPositiveButton("مدیریت افزونه‌های این مرورگر",(d,w)->showExtensions()).setNegativeButton("بستن",null).show();
    }

    private TextView addTool(String label,Runnable action){TextView b=xpButton(label);b.setTextSize(12);toolbar.addView(b,new LinearLayout.LayoutParams(-2,dp(34))); b.setOnClickListener(v->action.run());return b;}
    private TextView xpButton(String text){TextView b=new TextView(this);b.setText(text);b.setTextColor(0xff102c50);b.setTextSize(13);b.setGravity(Gravity.CENTER);b.setPadding(dp(7),dp(3),dp(7),dp(3));b.setBackground(borderDrawable(0xfff8f7ef,0xff7b9ebd));return b;}
    private GradientDrawable borderDrawable(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(2));d.setStroke(dp(1),stroke);return d;}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}

    private void navigateFromAddress(){String raw=address.getText().toString().trim();if(raw.isEmpty())return;((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);if(raw.matches("(?i)^[a-z][a-z0-9+.-]*://.*")||raw.startsWith("file:"))loadUrl(raw);else if(raw.matches("(?i)^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$")||(raw.contains(".")&&!raw.contains(" ")))loadUrl("https://"+raw);else loadUrl(String.format(Locale.US,searchTemplate,Uri.encode(raw)));}
    private void loadUrl(String u){if(u==null||u.trim().isEmpty())return;web.loadUrl(u);}
    private void updateAddress(String u){if(u!=null&&!u.equals("about:blank"))address.setText(u);}
    private void showHome(){web.loadDataWithBaseURL("https://home.invalid/",homeHtml(),"text/html","UTF-8",null);address.setText("");}
    private String homeHtml(){ String[] links={"Radio Garden|https://radio.garden/|📻","TuneIn|https://tunein.com/radio/Stream-All-Regions-c425242/|📻","SomaFM|https://somafm.com/|🎵","myTuner Radio|https://mytuner-radio.com/|📻","Radio Paradise|https://radioparadise.com/|🎵","BBC Sounds|https://www.bbc.co.uk/sounds|🇬🇧","Al Jazeera Live|https://www.aljazeera.com/video/live/|📺","DW Live|https://www.dw.com/en/live-tv/s-100825|🇩🇪","France 24|https://www.france24.com/en/live|🇫🇷","Euronews|https://www.euronews.com/live|📺","NHK World|https://www3.nhk.or.jp/nhkworld/en/live/|🇯🇵","CNA|https://www.channelnewsasia.com/watch|🇸🇬","Plex Live TV|https://watch.plex.tv/live-tv|📺","Watream|https://watream.com/|🌍","FaraNews Live|https://faranews.auratech.af/live|🌍","OSINT.tv|https://osint.tv/|🌍"}; StringBuilder cards=new StringBuilder(); for(String x:links){String[] p=x.split("\\|",-1); String domain=Uri.parse(p[1]).getHost(); String icon="https://www.google.com/s2/favicons?domain="+domain+"&sz=64"; cards.append("<a class='card' href='").append(p[1]).append("'><img src='").append(icon).append("' onerror=\"this.style.display='none'\"><span>").append(p[2]).append(" ").append(p[0]).append("</span></a>");} return "<html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:Arial;background:#dbe9fa;color:#143b70;padding:14px;text-align:center}.head{background:linear-gradient(#3989f8,#0751b7);color:white;padding:14px;border-radius:7px}.grid{display:grid;grid-template-columns:repeat(2,minmax(140px,1fr));gap:7px;max-width:720px;margin:14px auto}.card{display:flex;align-items:center;gap:8px;text-decoration:none;color:#143b70;background:#f8f7ef;border:1px solid #7b9ebd;border-radius:4px;padding:8px;text-align:left}.card img{width:28px;height:28px}.section{margin-top:18px}</style><div class='head'><h1>FastDesk Browser</h1><p>صفحه خانه</p></div><h2 class='section'>رادیو و تلویزیون زندهٔ جهان</h2><div class='grid'>"+cards.toString()+"</div><h2>سایت‌های آماده</h2><div class='grid'><a class='card' href='https://archive.org'>📚 Archive.org</a><a class='card' href='https://www.nhk.or.jp'>🇯🇵 NHK</a><a class='card' href='https://github.com'>🐙 GitHub</a><a class='card' href='https://chatgpt.com'>🤖 ChatGPT</a><a class='card' href='https://web.telegram.org'>✈️ Telegram</a><a class='card' href='https://web.whatsapp.com'>💬 WhatsApp</a><a class='card' href='https://discord.com/app'>🎮 Discord</a><a class='card' href='https://eitaa.com'>📱 ایتا</a></div><h2>منابع افزونه‌ها</h2><div class='grid'><a class='card' href='https://chromewebstore.google.com/'>🧩 Chrome Web Store</a><a class='card' href='https://addons.mozilla.org/'>🦊 Firefox Add-ons</a><a class='card' href='https://microsoftedge.microsoft.com/addons/Microsoft-Edge-Extensions-Home'>🌐 Edge Add-ons</a><a class='card' href='https://greasyfork.org/'>🧩 Greasy Fork</a></div></html>"; }

    private void toggleCopyMode(){copyMode=!copyMode;copyButton.setText(copyMode?"لغو Copy":"Copy Mini Win");copyButton.setBackground(borderDrawable(copyMode?0xffffe08a:0xfff8f7ef,0xff7b9ebd));if(copyMode){injectCopyScript();Toast.makeText(this,"حالت کپی فعال است؛ روی بخش موردنظر صفحه بزنید.",Toast.LENGTH_LONG).show();}else{web.evaluateJavascript("(function(){if(window.__miniWinHandler){document.removeEventListener('click',window.__miniWinHandler,true);window.__miniWinHandler=null;}document.documentElement.style.cursor='';})()",null);}}
    private void injectCopyScript(){String js="(function(){if(window.__miniWinHandler)return;document.documentElement.style.cursor='crosshair';window.__miniWinHandler=function(e){if(!window.MiniWinBridge)return;e.preventDefault();e.stopPropagation();var n=e.target;var t=(n.innerText||n.alt||n.getAttribute('aria-label')||n.title||'').trim();var img=(n.tagName==='IMG')?(n.currentSrc||n.src):'';var html='';try{html=n.outerHTML||'';}catch(x){}var r=n.getBoundingClientRect();window.MiniWinBridge.copyElement(t,img,html,Math.max(0,r.left),Math.max(0,r.top),Math.max(1,r.width),Math.max(1,r.height));};document.addEventListener('click',window.__miniWinHandler,true);})()";web.evaluateJavascript(js,null);}
    public class PageBridge {
        @JavascriptInterface public void copyElement(String text,String imageUrl,String html,double left,double top,double width,double height){runOnUiThread(()->{String out=(text==null?"":text);if(imageUrl!=null&&!imageUrl.isEmpty())out+=(out.isEmpty()?"":"\n")+"Image URL: "+imageUrl;if(out.isEmpty())out=html==null?"":html;if(out.length()>100000)out=out.substring(0,100000);try{float scale=web.getScale();int x=Math.max(0,(int)(left*scale)),y=Math.max(0,(int)(top*scale));int w=Math.min(web.getWidth()-x,Math.max(1,(int)(width*scale))),h=Math.min(web.getHeight()-y,Math.max(1,(int)(height*scale)));if(w>0&&h>0){Bitmap full=Bitmap.createBitmap(web.getWidth(),web.getHeight(),Bitmap.Config.ARGB_8888);web.draw(new Canvas(full));Bitmap crop=Bitmap.createBitmap(full,x,y,w,h);full.recycle();File dir=new File(getCacheDir(),"clipboard");if(!dir.exists())dir.mkdirs();File file=new File(dir,"copy_"+System.currentTimeMillis()+".png");try(FileOutputStream fos=new FileOutputStream(file)){crop.compress(Bitmap.CompressFormat.PNG,100,fos);}crop.recycle();android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(MainActivity.this,getPackageName()+".fileprovider",file);ClipData clip=new ClipData("Copy Mini Win",new String[]{"text/plain","image/png"},new ClipData.Item(uri));clip.addItem(new ClipData.Item(out));android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(clip);Toast.makeText(MainActivity.this,"تصویر بخش و متن آن کپی شد",Toast.LENGTH_SHORT).show();return;}}catch(Exception ignored){}android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Copy Mini Win",out));Toast.makeText(MainActivity.this,"متن و اطلاعات بخش کپی شد",Toast.LENGTH_SHORT).show();});}
        @JavascriptInterface public void exportContent(String content,String type){runOnUiThread(()->startExport(content,type));}
        @JavascriptInterface public void saveFormState(String url,String json){if(url!=null&&json!=null&&json.length()<500000)prefs.edit().putString("formUrl",url).putString("formState",json).apply();}
    }

    private void rememberHistory(String url,String title){
        if(url==null||!(url.startsWith("http://")||url.startsWith("https://")))return;
        String entry=System.currentTimeMillis()+"\t"+(title==null||title.trim().isEmpty()?url:title.replace("\t"," "))+"\t"+url;
        ArrayList<String> items=new ArrayList<>(prefs.getStringSet("history",new HashSet<>()));
        items.removeIf(x->x.endsWith("\t"+url)); items.add(0,entry); while(items.size()>500)items.remove(items.size()-1);
        prefs.edit().putStringSet("history",new LinkedHashSet<>(items)).apply();
    }
    private void addBookmark(){String url=web.getUrl();if(url==null||url.startsWith("about:")){Toast.makeText(this,"صفحه‌ای برای نشانک وجود ندارد",Toast.LENGTH_SHORT).show();return;}String title=web.getTitle();if(title==null||title.trim().isEmpty())title=url;EditText name=new EditText(this);name.setText(title);new AlertDialog.Builder(this).setTitle("ذخیره نشانک").setView(name).setPositiveButton("ذخیره",(d,w)->{String entry=name.getText().toString().replace("\t"," ")+"\t"+url;ArrayList<String> a=new ArrayList<>(prefs.getStringSet("bookmarks",new HashSet<>()));a.removeIf(x->x.endsWith("\t"+url));a.add(0,entry);prefs.edit().putStringSet("bookmarks",new LinkedHashSet<>(a)).apply();Toast.makeText(this,"نشانک ذخیره شد",Toast.LENGTH_SHORT).show();}).setNegativeButton("لغو",null).show();}
    private void showBookmarks(){showSavedList("نشانک‌ها","bookmarks",true);}
    private void showHistory(){showSavedList("تاریخچه","history",false);}
    private void showSavedList(String title,String key,boolean bookmarks){ArrayList<String> a=new ArrayList<>(prefs.getStringSet(key,new HashSet<>()));Collections.sort(a,(x,y)->{if(bookmarks)return x.compareToIgnoreCase(y);try{return Long.compare(Long.parseLong(y.substring(0,y.indexOf('\t'))),Long.parseLong(x.substring(0,x.indexOf('\t'))));}catch(Exception e){return y.compareTo(x);}});ArrayList<String> labels=new ArrayList<>(),urls=new ArrayList<>();for(String item:a){String[] p=item.split("\\t",3);if(bookmarks){if(p.length>=2){labels.add(p[0]);urls.add(p[1]);}}else if(p.length>=3){labels.add(p[1]);urls.add(p[2]);}}String[] display=labels.toArray(new String[0]);new AlertDialog.Builder(this).setTitle(title+" ("+display.length+")").setItems(display,(d,w)->loadUrl(urls.get(w))).setNeutralButton("پاک‌کردن",(d,w)->new AlertDialog.Builder(this).setMessage("همه موارد این فهرست پاک شوند؟").setPositiveButton("پاک‌کردن",(x,y)->prefs.edit().remove(key).apply()).setNegativeButton("لغو",null).show()).setNegativeButton("بستن",null).show();}
    private void shareCurrentPage(){String u=web.getUrl();if(u==null)return;Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,(web.getTitle()==null?"":web.getTitle()+"\n")+u);startActivity(Intent.createChooser(i,"اشتراک صفحه"));}

    private void showSettings(){String[] options={"مصرف کم اینترنت / فقط متن","حالت دسکتاپ (User-Agent)","تم Windows XP / Android","مدیریت موتورهای جست‌وجو","نوار ابزار فشرده","وضعیت شبکه و سازگاری نسل‌ها","مدیریت افزونه‌ها","JavaScript روشن/خاموش","پاک‌کردن حافظه نهان","پاک‌کردن کوکی‌ها و داده سایت","بارگذاری بدون کش","مدیریت دانلود و صفحه"};new AlertDialog.Builder(this).setTitle("تنظیمات و کنترل مرورگر").setItems(options,(d,which)->{switch(which){case 0:textOnly=!textOnly;prefs.edit().putBoolean("textOnly",textOnly).apply();web.getSettings().setLoadsImagesAutomatically(!textOnly);web.getSettings().setBlockNetworkImage(textOnly);status.setText(textOnly?"حالت کم‌مصرف: تصاویر مسدود شدند":"حالت عادی فعال شد");web.reload();break;case 1:desktopMode=!desktopMode;prefs.edit().putBoolean("desktop",desktopMode).apply();applyUserAgent();status.setText(desktopMode?"حالت دسکتاپ فعال شد؛ سایت دوباره بارگذاری می‌شود":"حالت موبایل فعال شد؛ سایت دوباره بارگذاری می‌شود");web.reload();break;case 2:showThemeChoice();break;case 3:showSearchSettings();break;case 4:compactToolbar=!compactToolbar;prefs.edit().putBoolean("compact",compactToolbar).apply();for(int i=0;i<toolbar.getChildCount();i++){View item=toolbar.getChildAt(i);item.setPadding(dp(compactToolbar?4:7),dp(3),dp(compactToolbar?4:7),dp(3));}break;case 5:updateNetworkStatus();new AlertDialog.Builder(this).setMessage("اتصال فعلی: "+networkDescription()+"\nبهینه‌سازی مرورگر با هر اتصال فعال کار می‌کند. نسل شبکه را مودم و اپراتور تعیین می‌کنند؛ 6G فقط با پشتیبانی واقعی دستگاه/شبکه قابل استفاده است.").setPositiveButton("باشه",null).show();break;case 6:showExtensions();break;case 7:boolean js=!prefs.getBoolean("javascript",true);prefs.edit().putBoolean("javascript",js).apply();web.getSettings().setJavaScriptEnabled(js);Toast.makeText(this,js?"JavaScript روشن شد":"JavaScript خاموش شد؛ بعضی سایت‌ها ممکن است کار نکنند",Toast.LENGTH_LONG).show();break;case 8:web.clearCache(true);Toast.makeText(this,"حافظه نهان پاک شد",Toast.LENGTH_SHORT).show();break;case 9:new AlertDialog.Builder(this).setMessage("با پاک‌کردن کوکی‌ها ممکن است از حساب‌های سایت‌ها خارج شوید.").setPositiveButton("پاک‌کردن",(x,y)->{CookieManager.getInstance().removeAllCookies(v->runOnUiThread(()->Toast.makeText(this,"کوکی‌ها پاک شدند",Toast.LENGTH_SHORT).show()));CookieManager.getInstance().flush();}).setNegativeButton("لغو",null).show();break;case 10:boolean noCache=!prefs.getBoolean("noCache",false);prefs.edit().putBoolean("noCache",noCache).apply();web.getSettings().setCacheMode(noCache?WebSettings.LOAD_NO_CACHE:WebSettings.LOAD_CACHE_ELSE_NETWORK);Toast.makeText(this,noCache?"بارگذاری بدون کش فعال شد":"ذخیره موقت صفحه‌ها فعال شد؛ بازگشت و رفتن به جلو تا حد امکان از داده‌های ذخیره‌شده استفاده می‌کند",Toast.LENGTH_LONG).show();break;case 11:showSaveMenu();break;}}).setNegativeButton("بستن",null).show();}
    private void showThemeChoice(){new AlertDialog.Builder(this).setTitle("سبک نمایش").setItems(new String[]{"Windows XP","Android ساده"},(d,w)->{androidTheme=(w==1);applyTheme(root);status.setText(androidTheme?"تم Android ساده فعال":"تم Windows XP فعال");}).show();}
    private void applyTheme(View view){
        if(view instanceof TextView && !(view instanceof EditText)){
            TextView t=(TextView)view;
            if(t==status){t.setBackgroundColor(androidTheme?0xfff1f3f5:0xffdce8f7);t.setTextColor(androidTheme?0xff333333:0xff17345d);}
            else if(t.getBackground() instanceof GradientDrawable){t.setBackground(borderDrawable(androidTheme?0xfff7f8fa:0xfff8f7ef,androidTheme?0xffb9c0c8:0xff7b9ebd));t.setTextColor(androidTheme?0xff202124:0xff102c50);}
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyTheme(group.getChildAt(i));}
        if(view==root)root.setBackgroundColor(androidTheme?0xfff5f5f5:0xffece9d8);
    }
    private void showSearchSettings(){
        String[] engines={"Google","Bing","DuckDuckGo","Brave Search","افزودن موتور دلخواه"};
        new AlertDialog.Builder(this).setTitle("موتور جست‌وجو").setItems(engines,(d,w)->{
            switch(w){case 0:setSearchTemplate("https://www.google.com/search?q=%s");break;case 1:setSearchTemplate("https://www.bing.com/search?q=%s");break;case 2:setSearchTemplate("https://duckduckgo.com/?q=%s");break;case 3:setSearchTemplate("https://search.brave.com/search?q=%s");break;case 4:showCustomSearchEngine();break;}
        }).show();
    }
    private void setSearchTemplate(String value){searchTemplate=value;prefs.edit().putString("searchTemplate",value).apply();Toast.makeText(this,"موتور جست‌وجو ذخیره شد",Toast.LENGTH_SHORT).show();}
    private void showCustomSearchEngine(){EditText e=new EditText(this);e.setSingleLine(true);e.setText(searchTemplate);e.setHint("https://example.com/search?q=%s");new AlertDialog.Builder(this).setTitle("افزودن موتور دلخواه").setMessage("%s با عبارت جست‌وجو جایگزین می‌شود.").setView(e).setPositiveButton("ذخیره",(d,w)->{String val=e.getText().toString().trim();if(val.contains("%s")&&val.startsWith("http"))setSearchTemplate(val);else Toast.makeText(this,"آدرس باید با http شروع شود و %s داشته باشد",Toast.LENGTH_LONG).show();}).setNegativeButton("لغو",null).show();}
    private void showExtensions(){
        EditText code=new EditText(this);code.setGravity(Gravity.TOP|Gravity.START);code.setMinLines(5);code.setHint("// JavaScript افزونه شما\ndocument.body.style.fontSize='18px';");
        Set<String> saved=prefs.getStringSet("extensions",new HashSet<>());StringBuilder existing=new StringBuilder();for(String item:saved){existing.append("• ").append(item.length()>60?item.substring(0,60)+"…":item).append("\n");}
        new AlertDialog.Builder(this).setTitle("افزونه‌های JavaScript").setMessage("تعداد افزونه‌های ذخیره‌شده: "+saved.size()+"\nکد فقط در صفحه‌های بازشده توسط همین مرورگر اجرا می‌شود.\n"+existing).setView(code).setPositiveButton("افزودن",(d,w)->{String js=code.getText().toString().trim();if(!js.isEmpty()){Set<String> copy=new HashSet<>(prefs.getStringSet("extensions",new HashSet<>()));copy.add(js);prefs.edit().putStringSet("extensions",copy).apply();Toast.makeText(this,"افزونه ذخیره شد؛ صفحه را دوباره بارگذاری کنید.",Toast.LENGTH_LONG).show();}}).setNeutralButton("پاک کردن همه",(d,w)->{prefs.edit().remove("extensions").apply();Toast.makeText(this,"همه افزونه‌ها پاک شدند",Toast.LENGTH_SHORT).show();}).setNegativeButton("بستن",null).show();
    }
    private void runExtensions(){Set<String> scripts=prefs.getStringSet("extensions",new HashSet<>());for(String js:scripts){if(js!=null&&!js.trim().isEmpty())try{web.evaluateJavascript("try{\n"+js+"\n}catch(e){console.error('Browser extension',e)}",null);}catch(Exception ignored){}}}
    private void applyUserAgent(){
        if(web==null)return;
        WebSettings s=web.getSettings();
        if(desktopMode){
            // Use a complete desktop Chrome UA rather than trying to rewrite Android's UA.
            s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
            // Fit the full desktop-width page into the available screen, like a desktop browser overview.
            web.setInitialScale(0);
            s.setSupportZoom(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
        }else{
            s.setUserAgentString(WebSettings.getDefaultUserAgent(this));
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
            web.setInitialScale(0);
        }
    }
    private void enforceDesktopViewport(){
        if(web==null)return;
        String js="(function(){try{"
                +"var m=document.querySelector(\"meta[name=\\\'viewport\\\"]\");"
                +"if(!m){m=document.createElement(\"meta\");m.name=\"viewport\";(document.head||document.documentElement).appendChild(m);}"
                +"m.setAttribute(\"content\",\"width=1024, initial-scale=1, minimum-scale=0.10, maximum-scale=10, user-scalable=yes\");"
                +"document.documentElement.style.minWidth=\"1024px\";"
                +"document.body.style.minWidth=\"1024px\";"
                +"}catch(e){}})()";
        web.evaluateJavascript(js,null);
    }

    private void translatePage(){String u=web.getUrl();if(u==null)return;loadUrl("https://translate.google.com/translate?sl=auto&tl=fa&u="+Uri.encode(u));}

    private void showSaveMenu(){new AlertDialog.Builder(this).setTitle("ذخیره صفحه").setItems(new String[]{"متن صفحه (.txt)","کد صفحه (.xml / HTML)","تصویر صفحه (.jpg)","چاپ / ذخیره PDF"},(d,w)->{if(w==0)web.evaluateJavascript("document.body?document.body.innerText:''",v->startExport(unquoteJs(v),"txt"));else if(w==1)web.evaluateJavascript("document.documentElement?document.documentElement.outerHTML:''",v->startExport(unquoteJs(v),"xml"));else if(w==2)saveScreenshot();else savePdf();}).show();}
    private String unquoteJs(String v){if(v==null||v.equals("null"))return "";try{return new org.json.JSONTokener(v).nextValue().toString();}catch(Exception e){return v;}}
    private void startExport(String content,String type){pendingExport=type;String ext=type.equals("txt")?"txt":"xml";String mime=type.equals("txt")?"text/plain":"application/xml";String name="webpage_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+"."+ext;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name);pendingExportContent=content;try{startActivityForResult(i,REQ_SAVE_EXPORT);}catch(Exception e){Toast.makeText(this,"ذخیره‌سازی در این دستگاه پشتیبانی نشد",Toast.LENGTH_LONG).show();}}
    private String pendingExportContent="";
    private void saveScreenshot(){try{if(web.getWidth()<=0||web.getHeight()<=0)return;Bitmap b=Bitmap.createBitmap(web.getWidth(),web.getHeight(),Bitmap.Config.ARGB_8888);web.draw(new Canvas(b));pendingExport="jpg";Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/jpeg");i.putExtra(Intent.EXTRA_TITLE,"webpage_"+System.currentTimeMillis()+".jpg");pendingBitmap=b;startActivityForResult(i,REQ_SAVE_EXPORT);}catch(Exception e){Toast.makeText(this,"گرفتن تصویر ناموفق بود",Toast.LENGTH_SHORT).show();}}
    private Bitmap pendingBitmap;
    private void savePdf(){try{PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);String name="WebPage_"+System.currentTimeMillis();pm.print(name,web.createPrintDocumentAdapter(name),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(new PrintAttributes.Resolution("pdf","PDF",300,300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());}catch(Exception e){Toast.makeText(this,"چاپ PDF در دسترس نیست",Toast.LENGTH_SHORT).show();}}

    private void promptDownload(String url,String name){pendingDownloadUrl=url;pendingDownloadName=safeFileName(name);EditText e=new EditText(this);e.setSingleLine(true);e.setText(pendingDownloadName);new AlertDialog.Builder(this).setTitle("دانلود فایل").setMessage("نام فایل را بررسی کنید؛ برای انتخاب محل ذخیره ادامه دهید.").setView(e).setPositiveButton("انتخاب محل ذخیره",(d,w)->{pendingDownloadName=safeFileName(e.getText().toString());launchDownloadSave();}).setNeutralButton("پوشه Downloads",(d,w)->{pendingDownloadName=safeFileName(e.getText().toString());startDownloadManager();}).setNegativeButton("لغو",null).show();}
    private void launchDownloadSave(){String ext="";int p=pendingDownloadName.lastIndexOf('.');if(p>=0)ext=pendingDownloadName.substring(p+1).toLowerCase(Locale.ROOT);String mime=URLConnection.guessContentTypeFromName(pendingDownloadName);if(mime==null)mime="application/octet-stream";Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,pendingDownloadName);try{startActivityForResult(i,REQ_SAVE_DOWNLOAD);}catch(Exception e){startDownloadManager();}}
    private void startDownloadManager(){try{DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);DownloadManager.Request r=new DownloadManager.Request(Uri.parse(pendingDownloadUrl));r.setTitle(pendingDownloadName);r.setDescription("FastDesk Browser");r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);String cookie=CookieManager.getInstance().getCookie(pendingDownloadUrl);if(cookie!=null)r.addRequestHeader("Cookie",cookie);r.addRequestHeader("User-Agent",web.getSettings().getUserAgentString());r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,pendingDownloadName);dm.enqueue(r);Toast.makeText(this,"دانلود به پوشه Downloads فرستاده شد",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"شروع دانلود ممکن نشد: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void downloadDetectedOrPrompt(){if(detectedMediaUrl==null||detectedMediaUrl.isEmpty()){Toast.makeText(this,"هنوز لینک مستقیم رسانه‌ای شناسایی نشده است؛ ممکن است سایت لینک را پنهان یا محافظت کرده باشد.",Toast.LENGTH_LONG).show();return;}promptDownload(detectedMediaUrl,guessName(detectedMediaUrl,null));}
    private void setVideoReady(boolean ready){if(downloadButton==null)return;if(ready){downloadButton.setText("⬇ ویدیو آماده");downloadButton.setTextColor(0xffa00000);AlphaAnimation a=new AlphaAnimation(0.35f,1f);a.setDuration(600);a.setRepeatMode(AlphaAnimation.REVERSE);a.setRepeatCount(AlphaAnimation.INFINITE);downloadButton.startAnimation(a);status.setText("رسانه قابل‌شناسایی پیدا شد؛ دکمه دانلود را بزنید.");}else{downloadButton.clearAnimation();downloadButton.setText("دانلود");}}
    private String guessName(String url,String disposition){if(url!=null&&url.toLowerCase(Locale.ROOT).contains(".m3u8"))return "video.ts";if(disposition!=null){java.util.regex.Matcher m=java.util.regex.Pattern.compile("filename\\*=UTF-8''([^;]+)|filename=\\\"?([^;\\\"]+)\\\"?",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(disposition);if(m.find()){String n=m.group(1)!=null?m.group(1):m.group(2);try{return Uri.decode(n);}catch(Exception ignored){return n;}}}try{String path=Uri.parse(url).getLastPathSegment();if(path!=null&&!path.isEmpty())return path;}catch(Exception ignored){}return "download_"+System.currentTimeMillis();}
    private String safeFileName(String s){if(s==null||s.trim().isEmpty())s="download";s=s.replaceAll("[\\\\/:*?\"<>|]","_").trim();if(s.length()>120)s=s.substring(0,120);return s;}
    private void chooseUploads(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,REQ_UPLOAD);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_UPLOAD){if(fileCallback!=null){Uri[] results=null;if(resultCode==RESULT_OK&&data!=null){if(data.getClipData()!=null){int n=data.getClipData().getItemCount();results=new Uri[n];for(int i=0;i<n;i++)results[i]=data.getClipData().getItemAt(i).getUri();}else if(data.getData()!=null)results=new Uri[]{data.getData()};}fileCallback.onReceiveValue(results);fileCallback=null;}return;}
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(requestCode==REQ_SAVE_EXPORT){try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Cannot open output");if("jpg".equals(pendingExport)&&pendingBitmap!=null){pendingBitmap.compress(Bitmap.CompressFormat.JPEG,92,out);pendingBitmap.recycle();pendingBitmap=null;}else{String text=pendingExportContent==null?"":pendingExportContent;if("xml".equals(pendingExport)){text="<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<webpage sourceUrl=\""+xmlEscape(web.getUrl()==null?"":web.getUrl())+"\"><![CDATA[\n"+text.replace("]]>", "]]]]><![CDATA[>")+"\n]]></webpage>";}out.write(text.getBytes(StandardCharsets.UTF_8));}Toast.makeText(this,"صفحه ذخیره شد",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"ذخیره ناموفق: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
        else if(requestCode==REQ_SAVE_DOWNLOAD){downloadToUri(pendingDownloadUrl,uri,pendingDownloadName);}
    }
    private void downloadToUri(String url,Uri dest,String name){Toast.makeText(this,"دانلود آغاز شد: "+name,Toast.LENGTH_SHORT).show();new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(dest)){if(out==null)throw new IOException("مقصد ذخیره باز نشد");if(url.toLowerCase(Locale.ROOT).contains(".m3u8")){downloadHls(url,out,0); }else{HttpURLConnection c=openConnection(url);try(InputStream in=c.getInputStream()){copyStream(in,out);}finally{c.disconnect();}}handler.post(()->Toast.makeText(this,"دانلود کامل شد",Toast.LENGTH_LONG).show());}catch(Exception e){String msg=e.getMessage();handler.post(()->new AlertDialog.Builder(this).setTitle("خطا در دانلود").setMessage(String.valueOf(msg)+"\nاین لینک ممکن است منقضی شده، نیازمند ورود، یا دارای رمزگذاری محافظت‌شده باشد.").setPositiveButton("تلاش با Downloads",(d,w)->startDownloadManager()).setNegativeButton("بستن",null).show());}}).start();}
    private HttpURLConnection openConnection(String url)throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(25000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(true);String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)c.setRequestProperty("Cookie",cookie);c.setRequestProperty("User-Agent",web.getSettings().getUserAgentString());String ref=web.getUrl();if(ref!=null)c.setRequestProperty("Referer",ref);c.setRequestProperty("Accept","*/*");c.connect();if(c.getResponseCode()>=400)throw new IOException("HTTP "+c.getResponseCode());return c;}
    private byte[] readUrl(String url)throws IOException{HttpURLConnection c=openConnection(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){copyStream(in,out);return out.toByteArray();}finally{c.disconnect();}}
    private void copyStream(InputStream in,OutputStream out)throws IOException{byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
    private void downloadHls(String url,OutputStream out,int depth)throws IOException{if(depth>3)throw new IOException("Playlist nesting too deep");String playlist=new String(readUrl(url),StandardCharsets.UTF_8);String[] lines=playlist.split("\\r?\\n");String variant=null;long best=-1;for(int i=0;i<lines.length;i++){String line=lines[i].trim();if(line.startsWith("#EXT-X-STREAM-INF:")){long bw=0;java.util.regex.Matcher m=java.util.regex.Pattern.compile("BANDWIDTH=(\\d+)").matcher(line);if(m.find())try{bw=Long.parseLong(m.group(1));}catch(Exception ignored){}for(int j=i+1;j<lines.length;j++){String next=lines[j].trim();if(!next.isEmpty()&&!next.startsWith("#")){if(bw>=best){best=bw;variant=new URL(new URL(url),next).toString();}break;}}}}if(variant!=null){downloadHls(variant,out,depth+1);return;}if(playlist.contains("#EXT-X-KEY:")&&!playlist.contains("METHOD=NONE"))throw new IOException("این HLS رمزگذاری شده است؛ دانلود خودکار این نسخهٔ محافظت‌شده انجام نشد");java.util.regex.Matcher map=java.util.regex.Pattern.compile("URI=\"([^\"]+)\"").matcher(playlist);boolean mapPending=false;for(String line:lines){line=line.trim();if(line.startsWith("#EXT-X-MAP:")&&map.find(0)){String init=new URL(new URL(url),map.group(1)).toString();out.write(readUrl(init));mapPending=true;}else if(!line.isEmpty()&&!line.startsWith("#")){String segment=new URL(new URL(url),line).toString();out.write(readUrl(segment));}}if(!playlist.contains("#EXTINF"))throw new IOException("Playlist contains no media segments");}

    private String xmlEscape(String s){return s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;");}
    private boolean isOnline(){try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm==null)return false;Network n=cm.getActiveNetwork();if(n==null)return false;NetworkCapabilities c=cm.getNetworkCapabilities(n);return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}catch(Exception e){return false;}}
    private void setOfflineUi(){if(appName!=null)appName.setText("🌐  FastDesk Browser — Offline");if(status!=null)status.setText("Offline | اتصال اینترنت در دسترس نیست؛ پس از اتصال دوباره بارگذاری کنید");}
    private void setOnlineTitle(){if(appName!=null)appName.setText("🌐  FastDesk Browser");}
    private void updateNetworkStatus(){if(!isOnline()){setOfflineUi();return;}setOnlineTitle();status.setText("آماده | "+networkDescription()+(textOnly?" | کم‌مصرف":""));}
    private String networkDescription(){try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);Network n=cm.getActiveNetwork();if(n==null)return "بدون اتصال";NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c==null)return "شبکه نامشخص";if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return "Wi‑Fi";if(c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)){if(Build.VERSION.SDK_INT>=29){int t=cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getSubtype();switch(t){case 1:case 2:case 4:case 7:case 11:return "شبکه موبایل 2G";case 3:case 5:case 6:case 8:case 9:case 10:case 12:case 14:case 15:return "شبکه موبایل 3G";case 13:case 18:return "شبکه موبایل 4G";case 20:return "شبکه موبایل 5G";default:return "شبکه موبایل";}}return "شبکه موبایل";}if(c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return "Ethernet";return "اتصال فعال";}catch(Exception e){return "شبکه نامشخص";}}
    private void saveCurrentFormState(){if(web==null||web.getUrl()==null)return;String js="(function(){try{var a=Array.prototype.slice.call(document.querySelectorAll('input,textarea,select'));var r=[];a.forEach(function(e,i){var t=(e.type||'').toLowerCase();if(t==='password'||t==='file'||t==='hidden')return;r.push({i:i,tag:e.tagName,type:t,name:e.name||'',id:e.id||'',value:(e.value||'').toString().slice(0,20000),checked:!!e.checked});});window.MiniWinBridge.saveFormState(location.href,JSON.stringify(r));}catch(e){}})()";try{web.evaluateJavascript(js,null);}catch(Exception ignored){}}
    private void restoreFormStateIfNeeded(String url){String savedUrl=prefs.getString("formUrl","");String json=prefs.getString("formState","");if(json.isEmpty()||!url.equals(savedUrl))return;prefs.edit().remove("formUrl").remove("formState").apply();try{String quoted=org.json.JSONObject.quote(json);web.evaluateJavascript("(function(){try{var a=JSON.parse("+quoted+");var els=document.querySelectorAll('input,textarea,select');a.forEach(function(x){var e=els[x.i];if(!e||((e.type||'').toLowerCase()==='password')||((e.type||'').toLowerCase()==='file'))return;if(x.id&&document.getElementById(x.id))e=document.getElementById(x.id);if(e.tagName==='INPUT'&&['checkbox','radio'].indexOf((e.type||'').toLowerCase())>=0)e.checked=!!x.checked;else e.value=x.value;});}catch(e){}})()",null);}catch(Exception ignored){}}

    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(web!=null)web.saveState(out);}
    @Override protected void onPause(){saveCurrentFormState();prefs.edit().putBoolean("textOnly",textOnly).putBoolean("desktop",desktopMode).putBoolean("compact",compactToolbar).apply();saveCurrentTab();super.onPause();if(web!=null)web.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){try{unregisterReceiver(connectivityReceiver);}catch(Exception ignored){}if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}if(mouseWindow!=null){mouseWindow.dismiss();mouseWindow=null;}if(web!=null){web.removeJavascriptInterface("MiniWinBridge");web.destroy();}super.onDestroy();}
}
