package com.antiexamen5.app;

import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Que paginas puede abrir la ventana del examen. Misma regla que la app de
 * computadora (app/permitidos.js):
 * - Google Forms y Microsoft Forms: solo sus paginas de formularios y de
 *   inicio de sesion, no el buscador.
 * - Cualquier otra plataforma (Moodle, etc.): todo el sitio del examen,
 *   por ejemplo *.upea.edu.bo, mas el inicio de sesion de Google o Microsoft.
 */
final class Permitidos {

    private static final Set<String> SEGUNDO_NIVEL = new HashSet<>(Arrays.asList(
            "com", "edu", "gob", "gov", "org", "net", "ac", "co", "mil", "int", "nom", "sch"));

    private static final List<String> INICIO_SESION = Arrays.asList(
            "accounts.google.com", "accounts.youtube.com", "login.microsoftonline.com",
            "login.live.com", "login.microsoft.com");

    private static final List<List<String>> FAMILIAS = Arrays.asList(
            Arrays.asList("docs.google.com", "forms.gle", "forms.google.com"),
            Arrays.asList("forms.office.com", "forms.cloud.microsoft", "forms.microsoft.com"));

    private final Set<String> exactos = new HashSet<>(INICIO_SESION);
    private final String sitio;

    Permitidos(String urlExamen) {
        String host = hostDe(urlExamen);
        List<String> familia = null;
        for (List<String> f : FAMILIAS) if (f.contains(host)) familia = f;
        if (familia != null) {
            exactos.addAll(familia);
            sitio = null;
        } else {
            sitio = sitioDe(host);
        }
    }

    static String hostDe(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    static String sitioDe(String host) {
        String limpio = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (limpio.endsWith(".")) limpio = limpio.substring(0, limpio.length() - 1);
        String[] p = limpio.split("\\.");
        if (p.length <= 2) return limpio;
        String ultimo = p[p.length - 1], penultimo = p[p.length - 2];
        int n = (ultimo.length() == 2 && SEGUNDO_NIVEL.contains(penultimo)) ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = p.length - n; i < p.length; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(p[i]);
        }
        return sb.toString();
    }

    boolean permitido(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        if (u.startsWith("about:") || u.startsWith("blob:") || u.startsWith("data:")) return true;
        if (!u.startsWith("https://")) return false;
        String h = hostDe(url);
        if (exactos.contains(h)) return true;
        return sitio != null && sitio.equals(sitioDe(h));
    }
}
