package app.template.extension.settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Minimal TMDB client — used by {@link CustomPosterDialog} to fetch a grid of posters for a
 * film, keyed on its IMDb id.
 *
 * <p>Requires a free TMDB API key, set by the user in Mod settings
 * ({@link Prefs#KEY_TMDB_API_KEY}). If the key is empty, methods return an empty list and the
 * picker falls back to just the paste-URL field.
 *
 * <p>Runs synchronously on a background thread; callers should invoke on a worker.
 */
public final class TmdbClient {

    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/w500";
    private static final String API_BASE   = "https://api.themoviedb.org/3";

    private TmdbClient() {}

    /** True if a TMDB API key is currently configured. */
    public static boolean isConfigured() {
        String key = Prefs.getString(Prefs.KEY_TMDB_API_KEY, "");
        return key != null && !key.trim().isEmpty();
    }

    /**
     * Fetches every poster URL TMDB has for the film matching [imdbId] (e.g. {@code tt0209144}).
     *
     * <p>Returns an empty list on any failure (no key, no match, network error, parse error).
     * The caller treats empty as "show the paste-URL field only".
     */
    public static List<String> fetchPosters(String imdbId) {
        List<String> out = new ArrayList<>();
        if (imdbId == null || imdbId.isEmpty()) return out;
        if (!isConfigured()) return out;

        try {
            String key = Prefs.getString(Prefs.KEY_TMDB_API_KEY, "").trim();

            // Step 1: /find/{imdbId}?external_source=imdb_id -> tmdb movie id
            String findUrl = API_BASE + "/find/" + urlEncode(imdbId)
                    + "?api_key=" + urlEncode(key)
                    + "&external_source=imdb_id";
            JSONObject find = httpGetJson(findUrl);
            if (find == null) return out;

            JSONArray movieResults = find.optJSONArray("movie_results");
            if (movieResults == null || movieResults.length() == 0) return out;
            JSONObject firstMovie = movieResults.optJSONObject(0);
            if (firstMovie == null) return out;
            int tmdbId = firstMovie.optInt("id", -1);
            if (tmdbId <= 0) return out;

            // Step 2: /movie/{id}/images -> posters array
            String imagesUrl = API_BASE + "/movie/" + tmdbId + "/images"
                    + "?api_key=" + urlEncode(key)
                    + "&include_image_language=en,null";
            JSONObject images = httpGetJson(imagesUrl);
            if (images == null) return out;

            JSONArray posters = images.optJSONArray("posters");
            if (posters == null) return out;
            for (int i = 0; i < posters.length(); i++) {
                JSONObject p = posters.optJSONObject(i);
                if (p == null) continue;
                String path = p.optString("file_path", "");
                if (path == null || path.isEmpty()) continue;
                out.add(IMAGE_BASE + path);
            }
        } catch (Throwable ignored) {
            // Return whatever we got before the failure — often partial, still useful.
        }
        return out;
    }

    /**
     * Convenience wrapper: fetches posters on a single-use background thread and blocks the
     * calling thread for up to [timeoutSeconds]. Do not call from the UI thread.
     */
    public static List<String> fetchPostersBlocking(String imdbId, int timeoutSeconds) {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            Future<List<String>> f = exec.submit(new Callable<List<String>>() {
                @Override public List<String> call() {
                    return fetchPosters(imdbId);
                }
            });
            return f.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Throwable t) {
            return new ArrayList<>();
        } finally {
            exec.shutdownNow();
        }
    }

    // --- internals --------------------------------------------------------

    private static JSONObject httpGetJson(String urlStr) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "MorpheLetterboxdPatch/1.0");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            in = conn.getInputStream();
            String body = readAll(in);
            if (body == null || body.isEmpty()) return null;
            return new JSONObject(body);
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (conn != null) conn.disconnect(); } catch (Throwable ignored) {}
        }
    }

    private static String readAll(InputStream in) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
                if (sb.length() > 500_000) break; // sanity cap
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }
}
