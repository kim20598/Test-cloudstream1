package app.template.extension.settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Local store of `filmSlug -> posterUrl` overrides for the "Custom poster" patch.
 *
 * <p>Values live in {@link Prefs} under a single key ({@link Prefs#KEY_CUSTOM_POSTERS}) as a
 * JSON object: {@code {"memento-2000":"https://...","parasite-2019":"https://..."}}. Reads
 * happen from the hot path in {@code PosterView.setImage} (via the bytecode hook) so the lookup
 * must be cheap and null-safe — a miss returns null and the original Glide path runs.
 *
 * <p>Writes are user-driven (picking a poster, resetting, importing a config), so no caching is
 * needed: each write goes straight to SharedPreferences and each read parses the small JSON
 * blob. Configs of a few hundred entries parse in well under a millisecond.
 */
public final class CustomPosterStore {

    private CustomPosterStore() {}

    /** The override URL for [filmSlug], or null if none is set. */
    public static String getOverride(String filmSlug) {
        if (filmSlug == null || filmSlug.isEmpty()) return null;
        try {
            JSONObject map = readMap();
            if (map == null) return null;
            String url = map.optString(filmSlug, null);
            return (url == null || url.isEmpty()) ? null : url;
        } catch (Throwable t) {
            return null;
        }
    }

    /** True if the user has explicitly set a custom poster for [filmSlug]. */
    public static boolean hasOverride(String filmSlug) {
        return getOverride(filmSlug) != null;
    }

    /** Stores [url] for [filmSlug]. Passing null or empty removes the override. */
    public static void setOverride(String filmSlug, String url) {
        if (filmSlug == null || filmSlug.isEmpty()) return;
        try {
            JSONObject map = readMap();
            if (map == null) map = new JSONObject();
            if (url == null || url.isEmpty()) {
                map.remove(filmSlug);
            } else {
                map.put(filmSlug, url);
            }
            Prefs.putString(Prefs.KEY_CUSTOM_POSTERS, map.toString());
        } catch (Throwable ignored) {
        }
    }

    /** Removes the override for [filmSlug], if any. */
    public static void clearOverride(String filmSlug) {
        setOverride(filmSlug, null);
    }

    /** Returns every slug with an active override. */
    public static List<String> listSlugs() {
        try {
            JSONObject map = readMap();
            if (map == null) return Collections.emptyList();
            List<String> out = new ArrayList<>();
            Iterator<String> it = map.keys();
            while (it.hasNext()) out.add(it.next());
            return out;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /** Snapshot of the whole map as a JSONObject. Never null — empty on failure. */
    public static JSONObject snapshot() {
        try {
            JSONObject map = readMap();
            return map != null ? map : new JSONObject();
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    /** Replaces the entire map. Used by config import. Null or empty clears everything. */
    public static void replaceAll(JSONObject map) {
        try {
            if (map == null) {
                Prefs.putString(Prefs.KEY_CUSTOM_POSTERS, "{}");
            } else {
                Prefs.putString(Prefs.KEY_CUSTOM_POSTERS, map.toString());
            }
        } catch (Throwable ignored) {
        }
    }

    /** How many overrides are currently stored. */
    public static int size() {
        try {
            JSONObject map = readMap();
            return map == null ? 0 : map.length();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static JSONObject readMap() {
        try {
            String raw = Prefs.getString(Prefs.KEY_CUSTOM_POSTERS, "{}");
            if (raw == null || raw.isEmpty()) return new JSONObject();
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        } catch (Throwable t) {
            return null;
        }
    }
}
