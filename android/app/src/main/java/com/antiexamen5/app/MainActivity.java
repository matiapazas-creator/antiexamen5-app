package com.antiexamen5.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ANTI.EXAMEN5 para Android.
 *
 * Dos vistas web en una sola pantalla:
 * 1. Principal: la pagina del alumno de antiexamen5.com (codigo, reglas,
 *    resultado). Ella habla con el servidor.
 * 2. Examen: Google Forms, Moodle u otra pagina, con una barra fija arriba
 *    (tiempo, advertencias y "Termine").
 *
 * La app solo detecta y avisa a la pagina principal; el servidor decide.
 */
public class MainActivity extends Activity {

    private static final String SITIO = "https://antiexamen5.com";
    private static final int ELEGIR_ARCHIVO = 41;

    private static final int URDIMBRE = 0xFF11121B, URDIMBRE3 = 0xFF1E2130, TEXTO = 0xFFF1EFE8,
            TEXTO3 = 0xFF9D9DB3, MAIZ = 0xFFF2C94C, FUCSIA = 0xFFE1307A, NARANJA = 0xFFF07A2B,
            VERDE = 0xFF2FB86A, ANIL = 0xFF4352C9, LANA = 0xFFEFE9DA, GRAVE = 0xFFFF5B93,
            LEVE = 0xFFFFA05A, OSCURO = 0xFF1B1505;

    private WebView principal, examen;
    private LinearLayout capaExamen, cajaAviso;
    private View hiloAviso;
    private TextView tReloj, tFaltas, tEstado, tAvisoTitulo, tAvisoTexto;
    private Permitidos permitidos;
    private String origenExamen;
    private boolean enExamen, fuera, eligiendoArchivo;
    private ValueCallback<Uri[]> archivoCallback;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable salidaPendiente;
    private final Runnable ocultarAviso = () -> cajaAviso.setVisibility(View.GONE);

    /* =========================================================== inicio */
    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        // Capturas, grabaciones y transmisiones de pantalla ven la app en negro.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(URDIMBRE);
        principal = new WebView(this);
        raiz.addView(principal, new FrameLayout.LayoutParams(-1, -1));
        capaExamen = construirCapaExamen();
        capaExamen.setVisibility(View.GONE);
        raiz.addView(capaExamen, new FrameLayout.LayoutParams(-1, -1));
        setContentView(raiz);

        configurarPrincipal();
        configurarExamen();
        principal.loadUrl(urlInicial(getIntent()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!enExamen && intent.getData() != null) principal.loadUrl(urlInicial(intent));
    }

    // antiexamen5://e/abc-def  ->  rendir.html?c=abc-def
    private static String urlInicial(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d != null && "antiexamen5".equals(d.getScheme())) {
            Matcher m = Pattern.compile("([a-z]{3})-?([a-z]{3})").matcher(String.valueOf(d.getPath()).toLowerCase(Locale.ROOT));
            if (m.find()) return SITIO + "/rendir.html?c=" + m.group(1) + "-" + m.group(2);
        }
        return SITIO + "/rendir.html";
    }

    /* ======================================================= principal */
    private void configurarPrincipal() {
        WebSettings s = principal.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        principal.setBackgroundColor(URDIMBRE);
        principal.addJavascriptInterface(new Puente(), "AE5Nativo");
        principal.setWebChromeClient(new WebChromeClient());
        principal.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith(SITIO + "/") || u.equals(SITIO)) return false;
                if (!enExamen && (u.startsWith("https://") || u.startsWith("antiexamen5://"))) abrirFuera(u);
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) mostrarSinConexion(r.getUrl().toString());
            }
        });
    }

    private void abrirFuera(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { /* sin navegador */ }
    }

    private void mostrarSinConexion(String volver) {
        String destino = volver != null && volver.startsWith(SITIO) ? volver : SITIO + "/rendir.html";
        String html = "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<body style='margin:0;min-height:100vh;display:grid;place-items:center;background:#11121b;color:#f1efe8;font-family:sans-serif;padding:24px'>"
                + "<div style='max-width:420px;background:#171925;padding:28px;border-top:6px solid #e1307a'>"
                + "<p style='font-weight:800;letter-spacing:.02em'>ANTI<span style='color:#f2c94c'>.</span>EXAMEN5</p>"
                + "<h1 style='font-size:24px;line-height:1.15'>No hay conexión con el servidor.</h1>"
                + "<p style='color:#cbc9d8;line-height:1.5'>Revisa tus datos móviles o el WiFi. Si estabas rindiendo, tus respuestas guardadas siguen a salvo y el tiempo lo lleva el servidor.</p>"
                + "<a href='" + destino.replace("'", "%27") + "' style='display:inline-block;margin-top:8px;padding:14px 22px;background:#f2c94c;color:#1b1505;font-weight:700;text-decoration:none'>Reintentar</a>"
                + "</div></body>";
        principal.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    /* ========================================================== examen */
    private static final String VIGIA_JS = "(function(){if(window.__ae5Vigia)return;window.__ae5Vigia=1;var propio=null;"
            + "function editable(n){for(n=n&&n.nodeType===3?n.parentElement:n;n;n=n.parentElement){if(n.isContentEditable)return true;var t=n.tagName;"
            + "if(t==='TEXTAREA')return true;if(t==='INPUT'&&/^(text|search|email|number|url|tel|)$/i.test(n.getAttribute('type')||''))return true;}return false;}"
            + "function sel(){var a=document.activeElement;if(a&&(a.tagName==='TEXTAREA'||a.tagName==='INPUT')&&typeof a.selectionStart==='number')return a.value.slice(a.selectionStart,a.selectionEnd);return String(window.getSelection()||'');}"
            + "function copiar(e){if(editable(e.target)||editable(document.activeElement)){propio=sel();return;}e.preventDefault();e.stopImmediatePropagation();if(e.type==='copy')AE5Examen.falta('COPIAR','Copió texto del examen');}"
            + "function pegar(e){var t=(e.clipboardData&&e.clipboardData.getData('text'))||'';if(!t||(propio!==null&&t===propio))return;e.preventDefault();e.stopImmediatePropagation();AE5Examen.falta('PEGAR','Texto de afuera ('+t.length+' caracteres)');}"
            + "window.addEventListener('copy',copiar,true);window.addEventListener('cut',copiar,true);window.addEventListener('paste',pegar,true);})();";

    private void configurarExamen() {
        WebSettings s = examen.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(false); // los enlaces "en otra pestana" se abren aqui mismo, con las mismas reglas
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        examen.addJavascriptInterface(new Vigia(), "AE5Examen");
        examen.setDownloadListener((url, ua, cd, mime, largo) -> { /* durante el examen no se descarga nada */ });
        examen.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (permitidos != null && permitidos.permitido(u)) return false;
                String host = Permitidos.hostDe(u);
                falta("NAVEGACION_BLOQUEADA", host.isEmpty() ? String.valueOf(r.getUrl().getScheme()) : host);
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(VIGIA_JS, null);
            }
        });
        examen.setWebChromeClient(new WebChromeClient() {
            // Subir un archivo (pregunta de Forms con adjunto) abre el selector
            // del sistema: eso no es salir del examen.
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (archivoCallback != null) archivoCallback.onReceiveValue(null);
                archivoCallback = cb;
                eligiendoArchivo = true;
                try {
                    startActivityForResult(p.createIntent(), ELEGIR_ARCHIVO);
                    return true;
                } catch (Exception e) {
                    archivoCallback = null;
                    eligiendoArchivo = false;
                    return false;
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        super.onActivityResult(pedido, resultado, datos);
        if (pedido != ELEGIR_ARCHIVO) return;
        if (archivoCallback != null) archivoCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultado, datos));
        archivoCallback = null;
        h.postDelayed(() -> eligiendoArchivo = false, 800);
    }

    /* ===================================================== barra y capa */
    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private View orillo() {
        int[][] franjas = {{FUCSIA, 14}, {LANA, 4}, {VERDE, 14}, {LANA, 4}, {NARANJA, 14}, {LANA, 4},
                {MAIZ, 14}, {LANA, 4}, {ANIL, 14}, {LANA, 4}, {FUCSIA, 8}};
        int ancho = dp(98), alto = dp(4);
        Bitmap b = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        float x = 0;
        for (int[] f : franjas) {
            p.setColor(f[0]);
            float w = ancho * f[1] / 98f;
            c.drawRect(x, 0, x + w, alto, p);
            x += w;
        }
        BitmapDrawable d = new BitmapDrawable(getResources(), b);
        d.setTileModeX(Shader.TileMode.REPEAT);
        View v = new View(this);
        v.setBackground(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, alto));
        return v;
    }

    private TextView texto(float sp, int color, boolean mono, boolean negrita) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(mono ? Typeface.MONOSPACE : Typeface.SANS_SERIF, negrita ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private LinearLayout construirCapaExamen() {
        LinearLayout capa = new LinearLayout(this);
        capa.setOrientation(LinearLayout.VERTICAL);
        capa.setBackgroundColor(URDIMBRE);
        capa.addView(orillo());

        LinearLayout barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setPadding(dp(12), dp(6), dp(8), dp(6));
        barra.setBackgroundColor(URDIMBRE);

        LinearLayout columnaReloj = new LinearLayout(this);
        columnaReloj.setOrientation(LinearLayout.VERTICAL);
        tEstado = texto(10, TEXTO3, false, false);
        tEstado.setText("● SUPERVISIÓN ACTIVA");
        tEstado.setLetterSpacing(0.06f);
        tReloj = texto(22, TEXTO, true, true);
        tReloj.setText("--:--");
        columnaReloj.addView(tEstado);
        columnaReloj.addView(tReloj);
        barra.addView(columnaReloj, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout columnaFaltas = new LinearLayout(this);
        columnaFaltas.setOrientation(LinearLayout.VERTICAL);
        columnaFaltas.setGravity(Gravity.END);
        TextView etiqueta = texto(10, TEXTO3, false, false);
        etiqueta.setText("ADVERTENCIAS");
        etiqueta.setLetterSpacing(0.06f);
        tFaltas = texto(18, TEXTO, true, true);
        tFaltas.setText("0/3");
        tFaltas.setGravity(Gravity.END);
        columnaFaltas.addView(etiqueta);
        columnaFaltas.addView(tFaltas);
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(-2, -2);
        lpF.setMarginEnd(dp(12));
        barra.addView(columnaFaltas, lpF);

        Button terminar = new Button(this);
        terminar.setText("Terminé");
        terminar.setAllCaps(false);
        terminar.setTextColor(OSCURO);
        terminar.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        terminar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setColor(MAIZ);
        fondo.setCornerRadius(0);
        terminar.setBackground(fondo);
        terminar.setPadding(dp(16), 0, dp(16), 0);
        terminar.setMinHeight(dp(44));
        terminar.setOnClickListener(v -> evento("entregar", null, null));
        barra.addView(terminar, new LinearLayout.LayoutParams(-2, dp(44)));
        capa.addView(barra);

        // Aviso: aparece bajo la barra y se oculta solo.
        cajaAviso = new LinearLayout(this);
        cajaAviso.setOrientation(LinearLayout.HORIZONTAL);
        cajaAviso.setBackgroundColor(URDIMBRE3);
        cajaAviso.setVisibility(View.GONE);
        hiloAviso = new View(this);
        cajaAviso.addView(hiloAviso, new LinearLayout.LayoutParams(dp(4), -1));
        LinearLayout textosAviso = new LinearLayout(this);
        textosAviso.setOrientation(LinearLayout.VERTICAL);
        textosAviso.setPadding(dp(12), dp(10), dp(12), dp(10));
        tAvisoTitulo = texto(14, TEXTO, false, true);
        tAvisoTexto = texto(13, 0xFFCBC9D8, false, false);
        textosAviso.addView(tAvisoTitulo);
        textosAviso.addView(tAvisoTexto);
        cajaAviso.addView(textosAviso, new LinearLayout.LayoutParams(-1, -2));
        capa.addView(cajaAviso, new LinearLayout.LayoutParams(-1, -2));

        examen = new WebView(this);
        examen.setBackgroundColor(0xFFFFFFFF);
        capa.addView(examen, new LinearLayout.LayoutParams(-1, 0, 1));
        return capa;
    }

    private static String mmss(int s) {
        int hh = s / 3600, m = (s % 3600) / 60, x = s % 60;
        return hh > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hh, m, x) : String.format(Locale.ROOT, "%02d:%02d", m, x);
    }

    private void pintarEstado(JSONObject e) {
        if (e.has("resta") && !e.isNull("resta")) {
            int r = e.optInt("resta");
            tReloj.setText(mmss(r));
            tReloj.setTextColor(r <= 60 ? GRAVE : r <= 300 ? LEVE : TEXTO);
        }
        if (e.has("faltas")) {
            int f = e.optInt("faltas"), l = e.optInt("limite", 3);
            tFaltas.setText(f + "/" + l);
            tFaltas.setTextColor(f >= l ? GRAVE : f > 0 ? LEVE : TEXTO);
        }
        boolean red = e.optBoolean("red", true);
        tEstado.setText(red ? "● SUPERVISIÓN ACTIVA" : "● SIN CONEXIÓN: SE REENVÍA");
        tEstado.setTextColor(red ? TEXTO3 : LEVE);
    }

    private void pintarAviso(JSONObject a) {
        String tipo = a.optString("tipo", "info");
        hiloAviso.setBackgroundColor("grave".equals(tipo) ? FUCSIA : "leve".equals(tipo) ? NARANJA
                : ("perdon".equals(tipo) || "ok".equals(tipo)) ? VERDE : ANIL);
        tAvisoTitulo.setText(a.optString("titulo", ""));
        String t = a.optString("texto", "");
        tAvisoTexto.setText(t);
        tAvisoTexto.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
        cajaAviso.setVisibility(View.VISIBLE);
        h.removeCallbacks(ocultarAviso);
        h.postDelayed(ocultarAviso, "grave".equals(tipo) ? 9000 : 6000);
    }

    private void mostrarExamenUi() {
        if (enExamen) capaExamen.setVisibility(View.VISIBLE);
    }

    private void mostrarPrincipalUi() {
        capaExamen.setVisibility(View.GONE);
    }

    /* ================================================ avisar a la pagina */
    private void evento(String tipo, String falta, String detalle) {
        try {
            JSONObject o = new JSONObject();
            o.put("tipo", tipo);
            if (falta != null) o.put("falta", falta);
            if (detalle != null) o.put("detalle", detalle.length() > 120 ? detalle.substring(0, 120) : detalle);
            principal.evaluateJavascript("window.__ae5Evento && window.__ae5Evento(" + o + ")", null);
        } catch (JSONException e) { /* no pasa */ }
    }

    private void falta(String tipo, String detalle) {
        if (enExamen) evento("falta", tipo, detalle);
    }

    private boolean desdeSitio() {
        String u = principal.getUrl();
        return u != null && (u.startsWith(SITIO + "/") || u.equals(SITIO));
    }

    /* Lo que la pagina principal puede pedir (ver rendir.js, puenteAndroid). */
    private class Puente {
        @JavascriptInterface
        public void iniciar(String json) {
            h.post(() -> {
                if (!desdeSitio()) return;
                try {
                    JSONObject o = new JSONObject(json);
                    String url = o.optString("url", "");
                    if (!url.startsWith("https://")) return;
                    boolean nueva = !enExamen;
                    permitidos = new Permitidos(url);
                    enExamen = true;
                    if (nueva) {
                        fuera = false;
                        origenExamen = Uri.parse(url).getScheme() + "://" + Uri.parse(url).getHost();
                        limpiarPortapapeles();
                        examen.loadUrl(url);
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        h.postDelayed(MainActivity.this::revisarPantallas, 1500);
                    }
                    mostrarExamenUi();
                } catch (JSONException e) { /* datos malos: no se abre nada */ }
            });
        }

        @JavascriptInterface
        public void estado(String json) {
            h.post(() -> {
                if (!desdeSitio()) return;
                try { pintarEstado(new JSONObject(json)); } catch (JSONException e) { /* nada */ }
            });
        }

        @JavascriptInterface
        public void aviso(String json) {
            h.post(() -> {
                if (!desdeSitio()) return;
                try { pintarAviso(new JSONObject(json)); } catch (JSONException e) { /* nada */ }
            });
        }

        @JavascriptInterface
        public void mostrarExamen() { h.post(() -> { if (desdeSitio()) mostrarExamenUi(); }); }

        @JavascriptInterface
        public void mostrarPrincipal() { h.post(() -> { if (desdeSitio()) mostrarPrincipalUi(); }); }

        @JavascriptInterface
        public void terminar() { h.post(() -> { if (desdeSitio()) terminarExamen(); }); }
    }

    /* Lo unico que la pagina del examen puede reportar: copiar o pegar. */
    private class Vigia {
        @JavascriptInterface
        public void falta(String tipo, String detalle) {
            if (!"COPIAR".equals(tipo) && !"PEGAR".equals(tipo)) return;
            h.post(() -> MainActivity.this.falta(tipo, detalle));
        }
    }

    private void terminarExamen() {
        enExamen = false;
        fuera = false;
        if (salidaPendiente != null) h.removeCallbacks(salidaPendiente);
        mostrarPrincipalUi();
        examen.stopLoading();
        examen.loadUrl("about:blank");
        examen.clearHistory();
        CookieManager.getInstance().removeAllCookies(null);
        if (origenExamen != null) WebStorage.getInstance().deleteOrigin(origenExamen);
        permitidos = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void limpiarPortapapeles() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip();
            else cm.setPrimaryClip(ClipData.newPlainText("", ""));
        } catch (Exception e) { /* algunos fabricantes no lo permiten */ }
    }

    /* =========================================== salir y volver a la app */
    @Override
    public void onWindowFocusChanged(boolean foco) {
        super.onWindowFocusChanged(foco);
        if (!enExamen || eligiendoArchivo) return;
        if (foco) volver();
        else programarSalida();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (enExamen && !eligiendoArchivo) salirAhora();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (enExamen && !eligiendoArchivo) volver();
    }

    private void programarSalida() {
        if (salidaPendiente != null) h.removeCallbacks(salidaPendiente);
        salidaPendiente = this::salirAhora;
        h.postDelayed(salidaPendiente, 250);
    }

    private void salirAhora() {
        if (salidaPendiente != null) h.removeCallbacks(salidaPendiente);
        if (fuera || !enExamen) return;
        fuera = true;
        evento("salida", "CAMBIO_APLICACION", "Salió de la app del examen");
    }

    private void volver() {
        if (salidaPendiente != null) h.removeCallbacks(salidaPendiente);
        if (!fuera) return;
        fuera = false;
        evento("regreso", null, null);
    }

    @Override
    public void onMultiWindowModeChanged(boolean activo, Configuration nueva) {
        super.onMultiWindowModeChanged(activo, nueva);
        if (activo) falta("PANTALLA_EXTRA", "Pantalla dividida o ventana flotante");
    }

    private void revisarPantallas() {
        if (!enExamen) return;
        if (isInMultiWindowMode()) falta("PANTALLA_EXTRA", "Pantalla dividida o ventana flotante");
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return;
        Display[] d = dm.getDisplays();
        if (d.length > 1) falta("PANTALLA_EXTRA", d.length + " pantallas");
    }

    /* ======================================================== atras */
    @Override
    public void onBackPressed() {
        if (capaExamen.getVisibility() == View.VISIBLE) {
            if (examen.canGoBack()) examen.goBack();
            else {
                try {
                    pintarAviso(new JSONObject().put("tipo", "info").put("titulo", "Para salir del examen, pulsa Terminé.")
                            .put("texto", "Antes, envía tus respuestas en la plataforma del examen."));
                } catch (JSONException e) { /* nada */ }
            }
            return;
        }
        if (principal.canGoBack()) { principal.goBack(); return; }
        if (enExamen) { mostrarExamenUi(); return; }
        super.onBackPressed();
    }
}
