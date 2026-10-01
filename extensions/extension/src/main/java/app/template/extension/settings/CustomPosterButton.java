package app.template.extension.settings;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime for the "Custom poster" feature. Five entry points:
 *
 * <ul>
 *   <li>{@link #maybeOverridePoster} — from {@code PosterView.setImage}.</li>
 *   <li>{@link #injectRow} — from {@code FilmActionsFragment.onViewCreated}; adds the
 *       "Custom poster" row under "Change poster / backdrop".</li>
 *   <li>{@link #injectBackdropRow} — same hook; adds a "Custom backdrop" row right after
 *       the poster row.</li>
 *   <li>{@link #maybeOverrideFilmBackdrop} — from {@code FilmHeaderFragment.configureBackdrop}.</li>
 *   <li>{@link #refreshVisiblePoster} — from the picker after saving.</li>
 * </ul>
 */
public final class CustomPosterButton {

    private static final String TAG_ROW_POSTER = "morphe_custom_poster_row";
    private static final String TAG_ROW_BACKDROP = "morphe_custom_backdrop_row";
    private static final Pattern IMDB = Pattern.compile("(tt\\d+)");

    private CustomPosterButton() {}

    // --- entry point 1: poster override ---------------------------------

    public static void maybeOverridePoster(Object posterView) {
        try {
            if (posterView == null) return;

            Method getFilmSummary = posterView.getClass().getMethod("getFilmSummary");
            Object filmSummary = getFilmSummary.invoke(posterView);
            if (filmSummary == null) return;

            Method getId = filmSummary.getClass().getMethod("getId");
            Object id = getId.invoke(filmSummary);
            if (id == null) return;
            String slug = id.toString();
            if (slug.isEmpty()) return;

            String customUrl = CustomPosterStore.getOverride(slug);
            if (customUrl == null || customUrl.isEmpty()) return;

            URL url = new URL(customUrl);
            Method setImageURL = posterView.getClass().getMethod("setImageURL", URL.class);
            setImageURL.invoke(posterView, url);
        } catch (Throwable ignored) {
        }
    }

    // --- entry point 2: row injection (poster + backdrop) ---------------

    /** Called from the injected hook at the top of {@code FilmActionsFragment.onViewCreated}. */
    public static void injectRow(Object fragment) {
        try {
            if (fragment == null) return;

            Object binding = readField(fragment, "binding");
            if (binding == null) return;

            Object userButtonsViewObj = readField(binding, "userButtonsView");
            if (!(userButtonsViewObj instanceof ViewGroup)) return;
            ViewGroup container = (ViewGroup) userButtonsViewObj;

            Object changePosterObj = readField(binding, "buttonChangePoster");
            if (!(changePosterObj instanceof View)) return;
            View reference = (View) changePosterObj;

            // Poster row (existing)
            if (container.findViewWithTag(TAG_ROW_POSTER) == null) {
                View posterRow = buildRow(container, reference, "Custom poster", TAG_ROW_POSTER,
                        new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                openPicker(fragment, "poster");
                            }
                        });
                if (posterRow != null) {
                    int idx = container.indexOfChild(reference);
                    if (idx < 0) idx = container.getChildCount() - 1;
                    container.addView(posterRow, idx + 1, reference.getLayoutParams());
                }
            }

            // Backdrop row — placed right after the poster row so they group together.
            if (container.findViewWithTag(TAG_ROW_BACKDROP) == null) {
                View posterRow = container.findViewWithTag(TAG_ROW_POSTER);
                View anchor = posterRow != null ? posterRow : reference;
                View backdropRow = buildRow(container, anchor, "Custom backdrop", TAG_ROW_BACKDROP,
                        new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                openPicker(fragment, "backdrop");
                            }
                        });
                if (backdropRow != null) {
                    int idx = container.indexOfChild(anchor);
                    if (idx < 0) idx = container.getChildCount() - 1;
                    container.addView(backdropRow, idx + 1, anchor.getLayoutParams());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static View buildRow(ViewGroup parent, View styledLike, String label, String tag,
                                 View.OnClickListener click) {
        try {
            Context ctx = parent.getContext();
            if (ctx == null) return null;

            Button row = new Button(ctx);
            row.setTag(tag);
            row.setText(label);
            row.setAllCaps(false);
            row.setBackground(null);
            row.setGravity(Gravity.CENTER_VERTICAL);

            if (styledLike instanceof TextView) {
                TextView ref = (TextView) styledLike;
                row.setTextSize(TypedValue.COMPLEX_UNIT_PX, ref.getTextSize());
                row.setTextColor(ref.getCurrentTextColor());
                row.setTypeface(ref.getTypeface());
                row.setPadding(ref.getPaddingLeft(), ref.getPaddingTop(),
                        ref.getPaddingRight(), ref.getPaddingBottom());
                if (ref.getMinHeight() > 0) row.setMinHeight(ref.getMinHeight());
            }

            row.setOnClickListener(click);
            return row;
        } catch (Throwable t) {
            return null;
        }
    }

    // --- entry point 3: film backdrop override --------------------------

    public static void maybeOverrideFilmBackdrop(Object binding, Object film) {
        try {
            if (binding == null || film == null) return;

            String slug = extractFilmSlug(film);
            if (slug == null || slug.isEmpty()) return;

            String customUrl = CustomPosterStore.getBackdropOverride(slug);
            if (customUrl == null || customUrl.isEmpty()) return;

            Object headerImageView = readField(binding, "headerImageView");
            if (!(headerImageView instanceof ImageView)) return;
            ImageView iv = (ImageView) headerImageView;

            CoilLoader.load(iv.getContext(), customUrl, iv);
        } catch (Throwable ignored) {
        }
    }

    private static String extractFilmSlug(Object film) {
        try {
            Method getSummary = film.getClass().getMethod("getSummary");
            Object summary = getSummary.invoke(film);
            if (summary != null) {
                Method getId = summary.getClass().getMethod("getId");
                Object id = getId.invoke(summary);
                if (id != null) return id.toString();
            }
        } catch (Throwable ignored) { }

        try {
            Method getId = film.getClass().getMethod("getId");
            Object id = getId.invoke(film);
            if (id != null) return id.toString();
        } catch (Throwable ignored) { }

        return null;
    }

    // --- entry point 4: instant refresh ---------------------------------

    public static void refreshVisiblePoster(Context ctx, String filmSlug, String newUrl) {
        try {
            if (ctx == null || filmSlug == null) return;

            Activity activity = findActivity(ctx);
            if (activity == null || activity.getWindow() == null) return;

            View root = activity.getWindow().getDecorView();
            refreshInViewTree(root, filmSlug, newUrl);
        } catch (Throwable ignored) {
        }
    }

    public static void refreshVisibleBackdrop(Context ctx, String filmSlug, String newUrl) {
        try {
            if (ctx == null || filmSlug == null) return;

            Activity activity = findActivity(ctx);
            if (activity == null || activity.getWindow() == null) return;

            // The film header's backdrop is a plain ImageView on the current screen.
            // Walk the view tree looking for it via the fragment's binding — but since we're
            // detached from the fragment here, we instead walk for an ImageView whose
            // id-name contains "headerImageView". Simpler: locate any View with a matching
            // resource id name, and load into it if it's an ImageView.
            View root = activity.getWindow().getDecorView();
            refreshBackdropInTree(root, newUrl);
        } catch (Throwable ignored) {
        }
    }

    private static void refreshBackdropInTree(View view, String newUrl) {
        try {
            if (view == null) return;

            if (view instanceof ImageView) {
                int id = view.getId();
                if (id != 0) {
                    String name = view.getResources().getResourceEntryName(id);
                    if ("headerImageView".equals(name)) {
                        if (newUrl == null || newUrl.isEmpty()) {
                            ((ImageView) view).setImageDrawable(null);
                        } else {
                            CoilLoader.load(view.getContext(), newUrl, (ImageView) view);
                        }
                        return;
                    }
                }
            }

            if (view instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) view;
                for (int i = 0; i < g.getChildCount(); i++) {
                    refreshBackdropInTree(g.getChildAt(i), newUrl);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void refreshInViewTree(View view, String filmSlug, String newUrl) {
        try {
            if (view == null) return;

            if (view.getClass().getName()
                    .equals("com.letterboxd.letterboxd.ui.views.PosterView")) {
                Method getFilmSummary = view.getClass().getMethod("getFilmSummary");
                Object filmSummary = getFilmSummary.invoke(view);
                if (filmSummary != null) {
                    Method getId = filmSummary.getClass().getMethod("getId");
                    Object id = getId.invoke(filmSummary);
                    if (id != null && filmSlug.equals(id.toString())) {
                        Method setImageURL = view.getClass()
                                .getMethod("setImageURL", URL.class);
                        if (newUrl == null || newUrl.isEmpty()) {
                            setImageURL.invoke(view, new Object[]{null});
                        } else {
                            setImageURL.invoke(view, new URL(newUrl));
                        }
                    }
                }
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    refreshInViewTree(group.getChildAt(i), filmSlug, newUrl);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Activity findActivity(Context ctx) {
        try {
            if (ctx instanceof Activity) return (Activity) ctx;
            if (ctx instanceof ContextWrapper) {
                Context c = ctx;
                while (c instanceof ContextWrapper) {
                    if (c instanceof Activity) return (Activity) c;
                    c = ((ContextWrapper) c).getBaseContext();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // --- internal: open picker ------------------------------------------

    private static void openPicker(Object fragment, String mode) {
        try {
            Method getFilmSummary = fragment.getClass().getMethod("getFilmSummary");
            Object filmSummary = getFilmSummary.invoke(fragment);
            if (filmSummary == null) return;

            Method requireContext = fragment.getClass().getMethod("requireContext");
            Context ctx = (Context) requireContext.invoke(fragment);
            if (ctx == null) return;

            Prefs.load(ctx);

            String slug = reflectString(filmSummary, "getId");
            if (slug == null || slug.isEmpty()) return;
            String imdbId = reflectImdbId(filmSummary);

            CustomPosterDialog dialog = new CustomPosterDialog(ctx, slug, imdbId, mode,
                    new CustomPosterDialog.OnChange() {
                        @Override public void onChange(String newUrl) { }
                    });
            dialog.show();
        } catch (Throwable ignored) {
        }
    }

    // --- reflection helpers ---------------------------------------------

    private static Object readField(Object target, String name) {
        try {
            Field f = target.getClass().getField(name);
            return f.get(target);
        } catch (Throwable t) {
            try {
                Field f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static String reflectString(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object result = m.invoke(target);
            return result == null ? null : result.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String reflectImdbId(Object filmSummary) {
        try {
            Method getLinks = filmSummary.getClass().getMethod("getLinks");
            Object linksObj = getLinks.invoke(filmSummary);
            if (!(linksObj instanceof List)) return null;
            for (Object link : (List<?>) linksObj) {
                Method getType = link.getClass().getMethod("getType");
                Object type = getType.invoke(link);
                if (type == null) continue;
                if (!"Imdb".equals(type.getClass().getSimpleName())) continue;
                Method getUrl = link.getClass().getMethod("getUrl");
                Object url = getUrl.invoke(link);
                if (url == null) continue;
                Matcher m = IMDB.matcher(url.toString());
                if (m.find()) return m.group(1);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // --- cache buster ---------------------------------------------------

    public static void __cacheBustV6() {
        // intentionally empty
    }
}
