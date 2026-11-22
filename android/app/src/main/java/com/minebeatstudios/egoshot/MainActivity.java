package com.minebeatstudios.egoshot;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.Manifest;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.webkit.WebViewAssetLoader;

// EgoShot pour Android : le jeu (index.html) est dans l'application et s'ouvre en plein écran, sans internet.
// Il est servi à l'adresse https://appassets.androidplatform.net/assets/ pour fonctionner comme sur le site
// (réglages du game master, parties en ligne).
public class MainActivity extends Activity {
  private static final String HOST = "appassets.androidplatform.net";
  private WebView web;
  private PermissionRequest pendingMic; // demande du micro en attente de la réponse du joueur

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
    web = new WebView(this);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setAllowFileAccess(false);
    String ver = "?";
    try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { /* ignoré */ }
    s.setUserAgentString(s.getUserAgentString() + " EgoShotApp/" + ver); // le jeu sait qu'il tourne dans l'appli (et sa version)
    web.setBackgroundColor(0xff000000);
    // micro (chat vocal) : Android demande l'autorisation la première fois qu'on touche 🎤, puis s'en souvient
    web.setWebChromeClient(new WebChromeClient() {
      @Override public void onPermissionRequest(final PermissionRequest req) {
        runOnUiThread(() -> {
          boolean audio = false;
          for (String r : req.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) audio = true;
          if (!audio) { req.deny(); return; }
          if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { req.grant(new String[] { PermissionRequest.RESOURCE_AUDIO_CAPTURE }); return; }
          pendingMic = req;
          requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 42);
        });
      }
    });
    web.addJavascriptInterface(new Bridge(), "EgoShotApp"); // le jeu peut ouvrir une page dans le navigateur (connexion)
    web.setWebViewClient(new WebViewClient() {
      @Override
      public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
        return loader.shouldInterceptRequest(req.getUrl());
      }
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
        Uri u = req.getUrl();
        if (HOST.equals(u.getHost())) return false;
        // liens extérieurs (sites, réseaux, connexion) : dans le navigateur du téléphone
        openExternal(u);
        return true;
      }
    });
    setContentView(web);
    fullscreen();
    if (state == null) web.loadUrl(startUrl(getIntent()));
    else web.restoreState(state);
  }

  // egoshot://auth?at=…&rt=…&exp=… : la session revient du navigateur, le jeu la lit dans l'adresse (#access_token=…)
  private String startUrl(Intent i) {
    String base = "https://" + HOST + "/assets/index.html";
    Uri d = i != null ? i.getData() : null;
    if (d != null && "egoshot".equals(d.getScheme())) {
      String at = d.getQueryParameter("at"), rt = d.getQueryParameter("rt"), exp = d.getQueryParameter("exp");
      // ?login=… : adresse différente, donc vraie recharge du jeu (sinon seul le # change et la session n'est pas lue)
      if (at != null) return base + "?login=" + System.currentTimeMillis() + "#access_token=" + Uri.encode(at) + "&refresh_token=" + Uri.encode(rt == null ? "" : rt) + "&expires_in=" + (exp == null ? "3600" : Uri.encode(exp));
    }
    // QR code de la télé / lien d'un salon : egoshot://room/CODE?salon=1 ou https://hamedkm.github.io/egoshot-play/?room=CODE
    if (d != null) {
      String code = null, salon = d.getQueryParameter("salon");
      if ("egoshot".equals(d.getScheme()) && "room".equals(d.getHost()) && d.getLastPathSegment() != null) code = d.getLastPathSegment();
      else if ("https".equals(d.getScheme())) code = d.getQueryParameter("room");
      if (code != null && code.matches("[A-Za-z]{4}")) return base + "?room=" + code.toUpperCase() + ("1".equals(salon) ? "&salon=1" : "");
    }
    // sur une télé Android : le jeu s'ouvre directement en mode télé (QR code à scanner avec les téléphones)
    if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return base + "?screen=tv";
    return base;
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    Uri d = i.getData();
    if (d != null && ("egoshot".equals(d.getScheme()) || "https".equals(d.getScheme()))) web.loadUrl(startUrl(i)); // connexion ou salon (QR code)
  }

  private void openExternal(Uri u) {
    String sc = u.getScheme();
    if (!"https".equals(sc) && !"http".equals(sc)) return;
    // fenêtre du navigateur qui glisse par-dessus le jeu (Google l'autorise, contrairement à la page intégrée)
    try {
      CustomTabsIntent tab = new CustomTabsIntent.Builder()
          .setDefaultColorSchemeParams(new CustomTabColorSchemeParams.Builder().setToolbarColor(0xff0b1118).build())
          .setShowTitle(true)
          .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
          .build();
      tab.launchUrl(this, u);
      return;
    } catch (Exception e) { /* pas de navigateur compatible : navigateur normal */ }
    try {
      Intent it = new Intent(Intent.ACTION_VIEW, u);
      it.addCategory(Intent.CATEGORY_BROWSABLE);
      it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(it);
    } catch (Exception e) { /* aucun navigateur */ }
  }

  private class Bridge {
    @JavascriptInterface public void open(final String url) { runOnUiThread(() -> openExternal(Uri.parse(url))); }
    // micro : le jeu peut ouvrir la page des réglages d'EgoShot (Autorisations → Micro)
    @JavascriptInterface public void appSettings() { runOnUiThread(() -> { try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); } catch (Exception e) { /* ignoré */ } }); }
    // numéro de version de l'appli installée : le jeu le compare à la dernière version publiée
    @JavascriptInterface public int versionCode() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { return 0; } }
    @JavascriptInterface public boolean micGranted() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }
  }

  private void fullscreen() {
    if (Build.VERSION.SDK_INT >= 30) {
      WindowInsetsController c = getWindow().getInsetsController();
      if (c != null) {
        c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
          | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
  }

  @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) fullscreen(); }
  @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
  // le son et le jeu s'arrêtent quand on quitte l'appli (le jeu coupe aussi son propre son), et reviennent au retour
  @Override protected void onPause() { try { web.evaluateJavascript("window.__egoPause&&window.__egoPause(true)", null); } catch (Exception e) { } super.onPause(); web.onPause(); }
  @Override protected void onResume() { super.onResume(); web.onResume(); try { web.evaluateJavascript("window.__egoPause&&window.__egoPause(false)", null); } catch (Exception e) { } }
  @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
    super.onRequestPermissionsResult(code, perms, res);
    if (code != 42 || pendingMic == null) return;
    if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) pendingMic.grant(new String[] { PermissionRequest.RESOURCE_AUDIO_CAPTURE });
    else pendingMic.deny();
    pendingMic = null;
  }
  @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
