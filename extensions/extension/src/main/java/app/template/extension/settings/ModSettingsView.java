package app.template.extension.settings;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The mod settings screen, built as plain views so the toggles and section headers can be styled
 * and tinted with the chosen accent. Writes to the same {@link Prefs} store the patches read.
 */
final class ModSettingsView extends ScrollView {

    private static final String[] NAV_LABELS = {
            "Stock", "No pill", "No pill, white icon", "No pill, accent icon", "Accent pill",
    };
    private static final String[] NAV_VALUES = { "stock", "nopill", "white", "accent", "accentPill" };

    private static final String[] REVEAL_LABELS = { "Frosted panel", "Tap-to-show link", "Shimmer", "Tap to burst" };
    private static final String[] REVEAL_VALUES = { "panel", "link", "shimmer", "burst" };

    private static final String[] ANIMATION_LABELS = { "Pop", "Crumble", "Confetti" };
    private static final String[] ANIMATION_VALUES = { "default", "crumble", "confetti" };

    private static final String[] CONFETTI_COLOR_LABELS = { "Accent", "Letterboxd colors", "Classic red" };
    private static final String[] CONFETTI_COLOR_VALUES = { "accent", "letterboxd", "red" };

    private static final String[] STREAMING_APP_LABELS = { "Stremio", "Nuvio", "CloudStream" };
    private static final String[] STREAMING_APP_VALUES = { "stremio", "nuvio", "cloudstream" };

    private static final String[] THEME_LABELS = { "Stock", "Pure Black (OLED)", "Purple", "Midnight Blue" };
    private static final String[] THEME_VALUES = { "stock", "oled", "purple", "midnight" };

    private final Context ctx;
    private final float density;
    private final int accent;
    private final LinearLayout column;

    private View revealRow;
    private TextView revealValue;
    private View animationRow;
    private TextView animationValue;
    private View confettiColorRow;
    private TextView confettiColorValue;
    private View streamingAppRow;
    private TextView streamingAppValue;
    private TextView navItemsValue;
    private TextView launchTabValue;
    private TextView homeTabsValue;

    ModSettingsView(Context context) {
        super(context);
        this.ctx = context;
        this.density = context.getResources().getDisplayMetrics().density;
        Prefs.load(context);
        this.accent = 0xFF000000 | AccentPresets.previewColor(context,
                Prefs.getString(Prefs.KEY_THEME_ACCENT, AccentPresets.defaultAccent(context)),
                Prefs.getString(Prefs.KEY_THEME_ACCENT_HEX, ""));

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(4), dp(20), dp(28));
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean themeAvailable = ModTheme.isSupported();

        boolean materialYouActive = ctx.getResources().getIdentifier(
                "morphe_my_surface", "color", ctx.getPackageName()) != 0;

        header("Theme");
        if (!themeAvailable) {
            column.addView(disabledRow("Theme", "Needs Android 12 or newer", null));
        } else if (materialYouActive) {
            column.addView(disabledRow("Theme", "Disabled — tap to find out why",
                    materialYouConflictExplainer()));
        } else {
            column.addView(choiceRow("Theme", "App colour scheme",
                    labelFor(THEME_LABELS, THEME_VALUES, Prefs.surface()),
                    new Runnable() {
                        @Override public void run() {
                            new ThemePickerDialog(ctx, Prefs.surface(), accent,
                                    new ThemePickerDialog.OnPick() {
                                        @Override public void onPick(String value) {
                                            Prefs.putString(Prefs.KEY_THEME_SURFACE, value);
                                            Prefs.putBoolean(Prefs.KEY_THEME_OLED, "oled".equals(value));
                                            RestartHelper.promptRestart(ctx);
                                        }
                                    }).show();
                        }
                    }));
        }

        if (materialYouActive) {
            column.addView(disabledRow("Match bottom nav to top bar", "Disabled — tap to find out why",
                    materialYouConflictExplainer()));
        } else {
            column.addView(toggleRow("Match bottom nav to top bar",
                    "Paint the bottom navigation bar black to match the top bar",
                    Prefs.KEY_MATCH_BOTTOM_NAV, true, true));
        }

        if (themeAvailable) {
            column.addView(accentRow());
            column.addView(choiceRow("Bottom nav selected style", null,
                    labelFor(NAV_LABELS, NAV_VALUES, Prefs.getString(Prefs.KEY_NAV_INDICATOR, "stock")),
                    new Runnable() {
                        @Override public void run() {
                            new NavStyleDialog(ctx, Prefs.getString(Prefs.KEY_NAV_INDICATOR, "stock"),
                                    accent, new NavStyleDialog.OnPick() {
                                        @Override public void onPick(String value) {
                                            Prefs.putString(Prefs.KEY_NAV_INDICATOR, value);
                                            rebuildAndRestart();
                                        }
                                    }).show();
                        }
                    }));
        }

        header("Bottom navigation");
        column.addView(choiceRow("Shown items", "Which destinations the bottom bar shows",
                navSummary(),
                new Runnable() {
                    @Override public void run() {
                        new NavItemsDialog(ctx, accent, new NavItemsDialog.OnDone() {
                            @Override public void onDone() {
                                if (navItemsValue != null) navItemsValue.setText(navSummary());
                                if (launchTabValue != null) launchTabValue.setText(launchTabSummary());
                                RestartHelper.promptRestart(ctx);
                            }
                        }).show();
                    }
                }));
        column.addView(choiceRow("Launch tab", "Which tab the app opens on",
                launchTabSummary(),
                new Runnable() {
                    @Override public void run() {
                        new LaunchTabDialog(ctx, Prefs.getString(Prefs.KEY_LAUNCH_TAB, "last"), accent,
                                new LaunchTabDialog.OnPick() {
                                    @Override public void onPick(String value) {
                                        Prefs.putString(Prefs.KEY_LAUNCH_TAB, value);
                                        if (launchTabValue != null) launchTabValue.setText(launchTabSummary());
                                        RestartHelper.promptRestart(ctx);
                                    }
                                }).show();
                    }
                }));

        header("Home");
        column.addView(choiceRow("Home tabs", "Which section tabs the home screen shows, and their order",
                homeTabsSummary(),
                new Runnable() {
                    @Override public void run() {
                        new HomeTabsDialog(ctx, accent, new HomeTabsDialog.OnDone() {
                            @Override public void onDone() {
                                if (homeTabsValue != null) homeTabsValue.setText(homeTabsSummary());
                                RestartHelper.promptRestart(ctx);
                            }
                        }).show();
                    }
                }));
        column.addView(toggleRow("Hide Video Store",
                "Remove the Video Store promo row from the Films tab",
                Prefs.KEY_HIDE_VIDEO_STORE, false, true));
        column.addView(toggleRow("Hide Where to Watch",
                "Remove the \"Where to watch\" section from a film's page",
                Prefs.KEY_HIDE_WHERE_TO_WATCH, false, false));
        column.addView(toggleRow("Runtime as 1h 47m",
                "Show a film's runtime in hours and minutes instead of \"107 mins\"",
                Prefs.KEY_RUNTIME_HHMM, true, false));

        header("Streaming");
        final PillToggle openInPlayer = new PillToggle(ctx);
        column.addView(toggleRow(openInPlayer, "Open in player",
                "Opens the film in streaming
