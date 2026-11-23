package com.minebeatstudios.egoshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

// Mise à jour « à chaud » du jeu dans l'appli de test : le contenu (index.html, etc.) est téléchargé depuis le site,
// vérifié (taille + empreinte), puis utilisé à la place de celui livré dans l'appli. Rien n'est réinstallé.
// Si quoi que ce soit échoue, l'ancienne version reste en place, intacte.
// Java pur (aucune dépendance Android) : testable sur un ordinateur.
//
// Liste publiée par le site (update.txt) :
//   build=1795464764860
//   file=index.html 6668039 <sha256>
// Sur le téléphone : <racine>/current.txt = nom du dossier actif ; <racine>/b<build>/ = fichiers qui diffèrent de ceux de l'appli.
public final class Updater {
  public interface Progress { void on(long done, long total); boolean cancelled(); }

  public static final class Manifest {
    public long build; public String text = "";
    public final Map<String, String[]> files = new LinkedHashMap<>(); // nom -> [taille, sha256]
    public static Manifest parse(String txt) {
      Manifest m = new Manifest(); if (txt == null) return m; m.text = txt;
      for (String line : txt.split("\n")) {
        line = line.trim();
        if (line.startsWith("build=")) { try { m.build = Long.parseLong(line.substring(6).trim()); } catch (Exception e) { m.build = 0; } }
        else if (line.startsWith("file=")) {
          String[] p = line.substring(5).trim().split("\\s+");
          if (p.length == 3 && p[0].matches("[A-Za-z0-9._-]{1,80}") && !p[0].startsWith(".") && p[1].matches("\\d{1,10}") && p[2].matches("[0-9a-f]{64}")) m.files.put(p[0], new String[] { p[1], p[2] });
        }
      }
      return m;
    }
    public String value(String key) { for (String line : text.split("\n")) { line = line.trim(); if (line.startsWith(key + "=")) return line.substring(key.length() + 1).trim(); } return null; }
  }

  private static HttpURLConnection open(String url, int timeoutMs) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(timeoutMs); c.setReadTimeout(timeoutMs); c.setInstanceFollowRedirects(true); c.setUseCaches(false);
    c.setRequestProperty("Cache-Control", "no-cache");
    int code = c.getResponseCode();
    if (code != 200) { c.disconnect(); throw new IOException("HTTP " + code); }
    return c;
  }
  public static String fetchText(String url, int timeoutMs) throws IOException {
    HttpURLConnection c = open(url, timeoutMs);
    try (InputStream in = c.getInputStream()) {
      java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
      while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); if (bo.size() > 65536) throw new IOException("trop gros"); }
      return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    } finally { c.disconnect(); }
  }
  public static String sha256(File f) throws IOException {
    try (InputStream in = new FileInputStream(f)) {
      MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] buf = new byte[65536]; int n;
      while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
      StringBuilder sb = new StringBuilder(); for (byte b : md.digest()) sb.append(String.format("%02x", b)); return sb.toString();
    } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
  }
  // Télécharge vers « out » (en passant par out.part), vérifie taille et empreinte. Lecture bloquée 20 s = abandon.
  public static void download(String url, File out, long size, String sha, long[] done, long total, Progress p) throws IOException {
    File part = new File(out.getPath() + ".part");
    HttpURLConnection c = open(url, 20000);
    try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(part)) {
      byte[] buf = new byte[65536]; int n; long got = 0;
      while ((n = in.read(buf)) > 0) {
        os.write(buf, 0, n); got += n; done[0] += n;
        if (size > 0 && got > size) throw new IOException("fichier plus gros que prévu");
        if (p != null) { if (p.cancelled()) throw new IOException("annulé"); p.on(done[0], total); }
      }
    } finally { c.disconnect(); }
    if (size > 0 && part.length() != size) { part.delete(); throw new IOException("taille incorrecte"); }
    if (sha != null && !sha.equals(sha256(part))) { part.delete(); throw new IOException("empreinte incorrecte"); }
    if (out.exists()) out.delete();
    if (!part.renameTo(out)) throw new IOException("écriture impossible");
  }
  private static void copy(File a, File b) throws IOException {
    try (InputStream in = new FileInputStream(a); OutputStream os = new FileOutputStream(b)) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) os.write(buf, 0, n); }
  }
  private static void wipe(File f) { if (f == null || !f.exists()) return; File[] l = f.listFiles(); if (l != null) for (File x : l) wipe(x); f.delete(); }
  private static String read(File f) { try (InputStream in = new FileInputStream(f)) { byte[] b = new byte[(int) Math.min(f.length(), 65536)]; int n = in.read(b); return n > 0 ? new String(b, 0, n, StandardCharsets.UTF_8) : ""; } catch (IOException e) { return ""; } }
  private static void write(File f, String s) throws IOException { File t = new File(f.getPath() + ".tmp"); try (OutputStream os = new FileOutputStream(t)) { os.write(s.getBytes(StandardCharsets.UTF_8)); } if (f.exists()) f.delete(); if (!t.renameTo(f)) throw new IOException("écriture impossible"); }

  // dossier actif (ou null : on utilise le contenu livré dans l'appli)
  public static File active(File root, Manifest embedded) {
    String name = read(new File(root, "current.txt")).trim();
    if (!name.matches("b\\d{1,16}")) return null;
    File d = new File(root, name);
    Manifest m = Manifest.parse(read(new File(d, "update.txt")));
    if (!d.isDirectory() || m.build <= 0) return null;
    if (embedded != null && embedded.build >= m.build) return null; // l'appli installée est plus récente que le téléchargement
    return d;
  }
  public static long activeBuild(File root, Manifest embedded) {
    File d = active(root, embedded);
    return d != null ? Manifest.parse(read(new File(d, "update.txt"))).build : (embedded != null ? embedded.build : 0);
  }
  // Vérifie et applique une mise à jour du contenu. Renvoie true si une nouvelle version a été installée.
  public static boolean sync(String baseUrl, File root, Manifest embedded, Progress p) throws IOException {
    root.mkdirs();
    Manifest remote = Manifest.parse(fetchText(baseUrl + "update.txt?t=" + System.currentTimeMillis(), 6000));
    if (remote.build <= 0 || remote.files.isEmpty() || !remote.files.containsKey("index.html")) throw new IOException("liste illisible");
    File cur = active(root, embedded);
    if (cur == null) { File c = new File(root, "current.txt"); if (c.exists()) c.delete(); }
    Manifest local = cur != null ? Manifest.parse(read(new File(cur, "update.txt"))) : embedded;
    if (local != null && remote.build <= local.build) { clean(root, cur); return false; }
    File dir = new File(root, "b" + remote.build); wipe(dir);
    if (!dir.mkdirs()) throw new IOException("dossier impossible");
    try {
      long total = 0; Map<String, Integer> plan = new LinkedHashMap<>(); // 0 = livré dans l'appli, 1 = copie de la version actuelle, 2 = à télécharger
      for (Map.Entry<String, String[]> e : remote.files.entrySet()) {
        String name = e.getKey(), sha = e.getValue()[1];
        String[] emb = embedded != null ? embedded.files.get(name) : null, loc = cur != null && local != null ? local.files.get(name) : null;
        if (emb != null && emb[1].equals(sha)) plan.put(name, 0);
        else if (loc != null && loc[1].equals(sha) && new File(cur, name).isFile()) plan.put(name, 1);
        else { plan.put(name, 2); total += Long.parseLong(e.getValue()[0]); }
      }
      long[] done = { 0 };
      for (Map.Entry<String, Integer> e : plan.entrySet()) {
        String name = e.getKey(); String[] meta = remote.files.get(name);
        if (e.getValue() == 1) { copy(new File(cur, name), new File(dir, name)); if (!meta[1].equals(sha256(new File(dir, name)))) throw new IOException("copie incorrecte"); }
        else if (e.getValue() == 2) download(baseUrl + name + "?b=" + remote.build, new File(dir, name), Long.parseLong(meta[0]), meta[1], done, total, p);
      }
      write(new File(dir, "update.txt"), remote.text);
      write(new File(root, "current.txt"), dir.getName());
    } catch (IOException | RuntimeException e) { wipe(dir); throw e; }
    clean(root, dir);
    return true;
  }
  // efface les anciens dossiers
  private static void clean(File root, File keep) { File[] l = root.listFiles(); if (l == null) return; for (File f : l) if (f.isDirectory() && (keep == null || !f.getName().equals(keep.getName()))) wipe(f); }
  // fichier à servir pour ce nom : celui téléchargé s'il existe, sinon null (= celui de l'appli)
  public static File resolve(File root, Manifest embedded, String name) {
    if (name == null || !name.matches("[A-Za-z0-9._-]{1,80}") || name.startsWith(".")) return null;
    File d = active(root, embedded); if (d == null) return null;
    File f = new File(d, name); return f.isFile() ? f : null;
  }
  private Updater() {}
}
