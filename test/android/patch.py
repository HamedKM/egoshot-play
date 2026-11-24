# Transforme une COPIE du projet Android en appli « EgoShot Test » : autre identifiant (elle s'installe à côté de l'appli normale),
# autre nom, autres liens d'ouverture, et un bouton de partage pour « Envoyer mes résultats ». Le dossier android/ d'origine n'est pas modifié.
import sys, re
root = sys.argv[1]
def edit(path, fn):
    p = f'{root}/{path}'; s = open(p, encoding='utf8').read(); t = fn(s)
    assert t != s, 'rien à changer dans ' + path
    open(p, 'w', encoding='utf8').write(t)
def need(s, a, b, n=None):
    c = s.count(a); assert c >= 1 and (n is None or c == n), (a, c); return s.replace(a, b)

def gradle(s):
    s = need(s, "applicationId 'com.minebeatstudios.egoshot'", "applicationId 'com.minebeatstudios.egoshot.test'", 1)
    return need(s, 'versionName "0.2.${buildNumber}"', 'versionName "0.2.${buildNumber}-test"', 1)
edit('app/build.gradle', gradle)

def manifest(s):
    s = need(s, 'android:label="EgoShot"', 'android:label="EgoShot Test"', 1)
    s = need(s, 'android:scheme="egoshot"', 'android:scheme="egoshottest"', 2)
    return need(s, 'android:pathPrefix="/egoshot-play"', 'android:pathPrefix="/egoshot-play/test"', 1)
edit('app/src/main/AndroidManifest.xml', manifest)

def java(s):
    s = need(s, '"egoshot".equals(d.getScheme())', '"egoshottest".equals(d.getScheme())', 3)
    share = ('    // version de test : « Envoyer mes résultats » ouvre le partage du téléphone (WhatsApp, etc.)\n'
             '    @JavascriptInterface public void share(final String text) { runOnUiThread(() -> { try { Intent s = new Intent(Intent.ACTION_SEND); s.setType("text/plain"); s.putExtra(Intent.EXTRA_TEXT, text); startActivity(Intent.createChooser(s, "Envoyer mes résultats")); } catch (Exception e) { /* ignoré */ } }); }\n')
    a = '    @JavascriptInterface public void open(final String url)'
    return need(s, a, share + a, 1)
edit('app/src/main/java/com/minebeatstudios/egoshot/MainActivity.java', java)

# ---- mises à jour sans réinstaller + mesures du suivi des tests (TestKit.java, Updater.java, update.html) ----
import shutil, os
here = os.path.dirname(os.path.abspath(__file__))
for f in ('TestKit.java', 'Updater.java'): shutil.copy(f'{here}/java/{f}', f'{root}/app/src/main/java/com/minebeatstudios/egoshot/{f}')
os.makedirs(f'{root}/app/src/main/res/xml', exist_ok=True); shutil.copy(f'{here}/res/xml/test_paths.xml', f'{root}/app/src/main/res/xml/test_paths.xml')
os.makedirs(f'{root}/app/src/main/assets', exist_ok=True); shutil.copy(f'{here}/assets/update.html', f'{root}/app/src/main/assets/update.html')

def java2(s):
    s = need(s, 'new WebViewAssetLoader.AssetsPathHandler(this)', 'TestKit.handler(this)', 1)
    a = '    web.addJavascriptInterface(new Bridge(), "EgoShotApp");'
    s = need(s, a, a + '\n    final TestKit kit = new TestKit(this, web);\n    web.addJavascriptInterface(kit.bridge(), "EgoShotTest"); // appli de test : mises à jour et mesures', 1)
    return need(s, 'if (state == null) web.loadUrl(startUrl(getIntent()));', 'if (state == null) kit.boot(startUrl(getIntent())); // écran de mise à jour, puis le jeu', 1)
edit('app/src/main/java/com/minebeatstudios/egoshot/MainActivity.java', java2)

def manifest2(s):
    s = need(s, '  <uses-permission android:name="android.permission.INTERNET" />', '  <uses-permission android:name="android.permission.INTERNET" />\n  <!-- appli de test : elle installe elle-même ses mises à jour -->\n  <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n  <uses-permission android:name="android.permission.VIBRATE" />', 1)
    return need(s, '  </application>', '    <provider android:name="androidx.core.content.FileProvider" android:authorities="com.minebeatstudios.egoshot.test.files" android:exported="false" android:grantUriPermissions="true">\n      <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/test_paths" />\n    </provider>\n  </application>', 1)
edit('app/src/main/AndroidManifest.xml', manifest2)

def gradle2(s):
    # signature : clé de test privée (recréée depuis la phrase secrète) si elle est fournie, sinon l'ancienne clé publique
    s = need(s, "test { storeFile file('test.keystore'); storePassword 'android'; keyAlias 'androiddebugkey'; keyPassword 'android' }",
             "test { storeFile file(System.getenv('TEST_KS') ?: 'test.keystore'); storePassword(System.getenv('TEST_KS') ? System.getenv('TEST_KS_PASS') : 'android'); keyAlias(System.getenv('TEST_KS') ? 'egoshottest' : 'androiddebugkey'); keyPassword(System.getenv('TEST_KS') ? System.getenv('TEST_KS_PASS') : 'android') }", 1)
    return need(s, "  implementation 'androidx.webkit:webkit:1.11.0'", "  implementation 'androidx.webkit:webkit:1.11.0'\n  implementation 'androidx.core:core:1.13.1' // installation des mises à jour (FileProvider)", 1)
edit('app/build.gradle', gradle2)
print('projet Android de test prêt')
