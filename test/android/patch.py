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
print('projet Android de test prêt')
