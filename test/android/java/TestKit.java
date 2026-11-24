package com.minebeatstudios.egoshot;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

// Tout ce qui est propre à l'appli de TEST (« EgoShot Test ») :
//  1. mises à jour sans réinstaller : contenu du jeu téléchargé à l'ouverture, nouvelle appli proposée quand la partie native change ;
//  2. mesures pour le suivi automatique des tests : modèle du téléphone, batterie, température.
// Rien de tout cela n'existe dans l'appli publique.
public final class TestKit {
  static final String SITE = "https://hamedkm.github.io/egoshot-play/test/";
  static final String APK_BASE = "https://github.com/HamedKM/egoshot-play/releases/download/apk-test/";
  static final long LAUNCH = System.currentTimeMillis(); // identifie cette ouverture de l'appli
  private static final String HOST = "appassets.androidplatform.net";

  private final Activity act; private final WebView web; private final File root;
  private final Updater.Manifest embedded;
  // état de l'écran de démarrage (lu par update.html)
  private volatile String phase = "check", msg = "", choice = "";
  private volatile int pct = -1, apkCode = 0; private volatile String apkName = "", apkSha = null; private volatile long apkSize = 0;
  private volatile boolean cancel = false, booting = false;
  private String target;

  TestKit(Activity a, WebView w) { act = a; web = w; root = new File(a.getFilesDir(), "game"); embedded = Updater.Manifest.parse(asset(a, "embedded.txt")); }

  private static String asset(Context c, String name) {
    try (InputStream in = c.getAssets().open(name)) { byte[] b = new byte[65536]; int n = in.read(b); return n > 0 ? new String(b, 0, n, StandardCharsets.UTF_8) : ""; } catch (Exception e) { return ""; }
  }
  private static String mime(String n) {
    if (n.endsWith(".html")) return "text/html"; if (n.endsWith(".js")) return "text/javascript"; if (n.endsWith(".json")) return "application/json";
    if (n.endsWith(".glb")) return "model/gltf-binary"; if (n.endsWith(".txt")) return "text/plain"; if (n.endsWith(".png")) return "image/png"; return "application/octet-stream";
  }
  // Sert le jeu : la version téléchargée si elle existe, sinon celle livrée dans l'appli.
  static WebViewAssetLoader.PathHandler handler(final Context c) {
    final WebViewAssetLoader.AssetsPathHandler base = new WebViewAssetLoader.AssetsPathHandler(c);
    final File root = new File(c.getFilesDir(), "game");
    final Updater.Manifest emb = Updater.Manifest.parse(asset(c, "embedded.txt"));
    return path -> {
      try {
        File f = Updater.resolve(root, emb, path);
        if (f != null) {
          Map<String, String> h = new HashMap<>(); h.put("Cache-Control", "no-store"); h.put("Access-Control-Allow-Origin", "*");
          return new WebResourceResponse(mime(path), mime(path).startsWith("text/") ? "utf-8" : null, 200, "OK", h, new FileInputStream(f));
        }
      } catch (Exception e) { /* on retombe sur la version de l'appli */ }
      return base.handle(path);
    };
  }

  private int myCode() { try { return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode; } catch (Exception e) { return 0; } }
  private String myName() { try { return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; } }
  private void set(String ph, String m, int p) { phase = ph; msg = m; pct = p; }

  // Démarrage : écran « mise à jour » puis le jeu. Quoi qu'il arrive (pas de réseau, erreur), le jeu finit par s'ouvrir.
  void boot(String targetUrl) {
    target = targetUrl; cancel = false; choice = ""; booting = true; set("check", "Recherche de mise à jour…", -1);
    web.loadUrl("https://" + HOST + "/assets/update.html");
    new Thread(() -> {
      try { checkApk(); } catch (Throwable e) { /* pas de réseau : on continue */ }
      if (!"stay".equals(choice)) {
        try { syncContent(); } catch (Throwable e) { /* échec : le jeu démarre avec la version déjà présente */ }
        go();
      }
    }, "egoshot-update").start();
  }
  private void go() { if (!booting) return; booting = false; set("go", "", 100); act.runOnUiThread(() -> web.loadUrl(target)); }

  private void syncContent() throws Exception {
    set("dl", "Recherche de mise à jour du jeu…", -1);
    Updater.sync(SITE, root, embedded, new Updater.Progress() {
      public void on(long done, long total) { set("dl", "Téléchargement de la mise à jour du jeu…", total > 0 ? (int) Math.min(99, done * 100 / total) : -1); }
      public boolean cancelled() { return cancel; }
    });
  }
  // Nouvelle appli disponible ? (version.txt publié à côté de l'APK : code=…, name=…, size=…, sha256=…)
  private void checkApk() throws Exception {
    Updater.Manifest v = Updater.Manifest.parse(Updater.fetchText(APK_BASE + "version.txt?t=" + System.currentTimeMillis(), 5000));
    int code = 0; try { code = Integer.parseInt(v.value("code")); } catch (Exception e) { return; }
    if (code <= myCode()) return;
    apkCode = code; apkName = v.value("name") == null ? "" : v.value("name"); apkSha = v.value("sha256"); try { apkSize = Long.parseLong(v.value("size")); } catch (Exception e) { apkSize = 0; }
    set("apk", "", -1);
    // on attend le choix du testeur : « Mettre à jour » ou « Plus tard »
    for (int i = 0; i < 6000 && choice.isEmpty(); i++) Thread.sleep(100);
    if (!"install".equals(choice)) { choice = ""; return; }
    File dir = new File(act.getCacheDir(), "update"); dir.mkdirs();
    File apk = new File(dir, "EgoShot-Test.apk");
    try {
      set("apkdl", "Téléchargement de la nouvelle application…", 0);
      Updater.download(APK_BASE + "EgoShot-Test.apk?v=" + code, apk, apkSize, apkSha != null && apkSha.matches("[0-9a-f]{64}") ? apkSha : null, new long[] { 0 }, apkSize, new Updater.Progress() {
        public void on(long done, long total) { set("apkdl", "Téléchargement de la nouvelle application…", total > 0 ? (int) Math.min(99, done * 100 / total) : -1); }
        public boolean cancelled() { return cancel; }
      });
      choice = "stay"; set("apkready", "", 100);
      act.runOnUiThread(this::install);
    } catch (Exception e) { choice = ""; set("check", "Téléchargement impossible, on continue…", -1); Thread.sleep(1200); }
  }
  // Ouvre l'installation d'Android. La première fois, Android demande d'autoriser « EgoShot Test » à installer des applications.
  private String install() {
    try {
      if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
        act.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.getPackageName())));
        set("apkperm", "", 100); return "perm";
      }
      File apk = new File(new File(act.getCacheDir(), "update"), "EgoShot-Test.apk");
      Uri u = FileProvider.getUriForFile(act, act.getPackageName() + ".files", apk);
      Intent it = new Intent(Intent.ACTION_VIEW); it.setDataAndType(u, "application/vnd.android.package-archive");
      it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
      act.startActivity(it); set("apkready", "", 100); return "ok";
    } catch (Exception e) { set("apkfail", String.valueOf(e.getMessage()), -1); return "fail"; }
  }

  Object bridge() { return new Bridge(); }
  final class Bridge {
    // ----- écran de démarrage -----
    @JavascriptInterface public String boot() {
      try { JSONObject o = new JSONObject(); o.put("phase", phase); o.put("msg", msg); o.put("pct", pct); o.put("apk", apkName); o.put("mine", myName()); return o.toString(); } catch (Exception e) { return "{}"; }
    }
    @JavascriptInterface public void act(String what) {
      if ("install".equals(what) && "apk".equals(phase)) choice = "install";
      else if ("later".equals(what) && "apk".equals(phase)) choice = "later";
      else if ("retry".equals(what)) act.runOnUiThread(TestKit.this::install);
      else if ("reboot".equals(what) && !booting && target != null) act.runOnUiThread(() -> TestKit.this.boot(target)); // nouvelle version du jeu : on repasse par l'écran de mise à jour
      else if ("skip".equals(what)) { cancel = true; if ("apk".equals(phase)) choice = "later"; if ("stay".equals(choice) || !"dl".equals(phase)) { new Thread(() -> { try { Thread.sleep(300); } catch (Exception e) { } go(); }).start(); } }
    }
    // ----- notifications Android (début / fin du banc d'essai) : avec son et vibration -----
    @JavascriptInterface public void askNotif() {
      act.runOnUiThread(() -> { try { if (Build.VERSION.SDK_INT >= 33 && act.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) act.requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 77); } catch (Exception e) { /* ignoré */ } });
    }
    @JavascriptInterface public void notify(final String title, final String text) {
      act.runOnUiThread(() -> {
        try {
          android.app.NotificationManager nm = (android.app.NotificationManager) act.getSystemService(Context.NOTIFICATION_SERVICE);
          if (Build.VERSION.SDK_INT >= 26) { android.app.NotificationChannel ch = new android.app.NotificationChannel("tests", "Tests EgoShot", android.app.NotificationManager.IMPORTANCE_HIGH); ch.enableVibration(true); nm.createNotificationChannel(ch); }
          Intent open = new Intent(act, act.getClass()); open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
          android.app.PendingIntent pi = android.app.PendingIntent.getActivity(act, 0, open, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
          android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(act, "tests") : new android.app.Notification.Builder(act);
          b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setStyle(new android.app.Notification.BigTextStyle().bigText(text)).setAutoCancel(true).setContentIntent(pi).setDefaults(android.app.Notification.DEFAULT_ALL);
          nm.notify(7701, b.build());
        } catch (Exception e) { /* notifications refusées : tant pis */ }
        try { android.os.Vibrator v = (android.os.Vibrator) act.getSystemService(Context.VIBRATOR_SERVICE); if (v != null) v.vibrate(new long[] { 0, 400, 200, 400 }, -1); } catch (Exception e) { /* ignoré */ }
      });
    }
    // ----- suivi des tests -----
    @JavascriptInterface public String info() {
      try {
        JSONObject o = new JSONObject();
        o.put("platform", "android"); o.put("marque", Build.MANUFACTURER); o.put("modele", Build.MODEL); o.put("appareil", Build.DEVICE);
        o.put("android", Build.VERSION.RELEASE); o.put("sdk", Build.VERSION.SDK_INT);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo(); ((ActivityManager) act.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        o.put("ram", Math.round(mi.totalMem / 1048576.0));
        o.put("app", myName()); o.put("code", myCode()); o.put("launch", String.valueOf(LAUNCH)); o.put("contenu", Updater.activeBuild(root, embedded));
        return o.toString();
      } catch (Exception e) { return "{}"; }
    }
    @JavascriptInterface public String battery() {
      try {
        JSONObject o = new JSONObject();
        Intent b = act.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
          int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100), t = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
          if (level >= 0 && scale > 0) o.put("level", Math.round(level * 100f / scale));
          if (t != Integer.MIN_VALUE) o.put("temp", t / 10.0);
          o.put("charging", b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
        }
        int th = 0; if (Build.VERSION.SDK_INT >= 29) { PowerManager pm = (PowerManager) act.getSystemService(Context.POWER_SERVICE); if (pm != null) th = pm.getCurrentThermalStatus(); }
        o.put("thermal", th);
        PowerManager pm2 = (PowerManager) act.getSystemService(Context.POWER_SERVICE); if (pm2 != null) o.put("saver", pm2.isPowerSaveMode());
        return o.toString();
      } catch (Exception e) { return "{}"; }
    }
  }
}
