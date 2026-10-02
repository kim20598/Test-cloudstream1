package app.template.patches.busuu

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.COMPATIBILITY_BUSUU
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Build-time AMOLED theme for Busuu.
 *
 * Busuu ships its dark theme as `res/values-night/colors.xml` — a full override
 * set that swaps the day palette (whites, pale blues) for dark surfaces
 * (#151617 canvas, #252b2f greys, #3e3e3e cards). This patch drives every one
 * of those dark surfaces down to true black (#FF000000) or near-black
 * (#FF0A0A0A / #FF0F0F0F / #FF1A1A1A) so an OLED screen saves power and the
 * theme reads as genuine AMOLED instead of dark grey.
 *
 * Only the night colours are written to. The day file is left alone so light
 * mode is untouched — patching the day values with black would break every
 * screen in light mode.
 *
 * Text colours, icon tints, accents, and the Material timepicker widget's
 * resources are deliberately not touched: those need to stay light-on-dark for
 * legibility, and the timepicker draws itself via theme attributes, not hex.
 *
 * This is a `resourcePatch`, so it has no relationship to the bytecode side of
 * the Busuu patches (EnablePremium.kt) and does not need `addInstructions` or
 * `returnEarly` — those live in the bytecode API surface.
 *
 * Two candidate paths are attempted because Morphe's patcher exposes the
 * qualifier-specific resource file at different virtual paths depending on how
 * it decoded the APK: usually `res/values-night/colors.xml`, sometimes
 * `resources/res/values-night/colors.xml`. Whichever resolves first is used;
 * if neither does, the patch fails loudly instead of silently no-op'ing.
 */
@Suppress("unused")
val busuuAmoledPatch = resourcePatch(
    name = "AMOLED (baked in)",
    description = "Bakes a true-black AMOLED theme into Busuu at patch time by darkening " +
        "its night-mode surface colours. Works on every Android version. Changing it later " +
        "requires re-patching.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_BUSUU)

    execute {
        val candidates = listOf(
            "res/values-night/colors.xml",
            "resources/res/values-night/colors.xml",
        )

        var wrote = false

        for (path in candidates) {
            val exists = try {
                get(path).isFile
            } catch (t: Throwable) {
                false
            }
            if (!exists) continue

            document(path).use { document ->
                val resources = document.documentElement
                    ?: throw PatchException("$path has no root element")

                BUSUU_AMOLED_NIGHT.forEach { (name, hex) ->
                    writeColorIfPresent(document, resources, name, hex)
                }

                // Marker resource so a future in-app Mod settings screen can detect that
                // a baked theme is active. Mirrors the Letterboxd patch's `morphe_baked_theme`.
                if (!hasColor(document, "morphe_baked_theme")) {
                    resources.appendChild(
                        document.createElement("color").apply {
                            setAttribute("name", "morphe_baked_theme")
                            textContent = "#FF000000"
                        }
                    )
                }
            }

            wrote = true
        }

        if (!wrote) {
            throw PatchException(
                "No Busuu night colours file found at any known path " +
                    "(tried: ${candidates.joinToString()})"
            )
        }
    }
}

/**
 * Writes [hex] to the existing `<color name="...">` entry named [name]. Does nothing if
 * the resource is not present in the given file — Busuu's resource set varies between
 * versions, so a strict "must exist" check would break on some APKs. Missing entries
 * are simply ignored; the palette is best-effort by design.
 */
private fun writeColorIfPresent(
    document: Document,
    resources: Element,
    name: String,
    hex: String,
) {
    val nodes = document.getElementsByTagName("color")
    for (i in 0 until nodes.length) {
        val el = nodes.item(i) as Element
        if (el.getAttribute("name") == name) {
            el.textContent = hex
        }
    }
}

/** True if a `<color name="[name]">` already exists in [document]. */
private fun hasColor(document: Document, name: String): Boolean {
    val nodes = document.getElementsByTagName("color")
    for (i in 0 until nodes.length) {
        val el = nodes.item(i) as Element
        if (el.getAttribute("name") == name) return true
    }
    return false
}

/**
 * Busuu AMOLED palette. Keys are the exact resource names from Busuu's
 * `res/values-night/colors.xml`. Values are ARGB hex.
 *
 * Rule of thumb used when picking these:
 *  - The main canvas (whatever fills the screen behind content) → #FF000000
 *  - Cards, sheets, modals, snackbars → slightly off-black so edges are visible
 *  - Shimmer placeholders → darker but not pure black, or the shimmer animation
 *    becomes invisible
 *  - Dividers → kept just above black so hairlines are still readable
 */
private val BUSUU_AMOLED_NIGHT = mapOf(

    // ── Primary canvas ──
    "busuu_main_background"        to "#FF000000",
    "neutral_background"           to "#FF000000",

    // ── Aliases that resolve to a dark grey by default ──
    "busuu_app_background"         to "#FF000000",
    "busuu_grey_dark"              to "#FF000000",

    // ── Elevated surfaces ──
    "busuu_bottom_modal"           to "#FF0A0A0A",
    "busuu_white_background_alt"   to "#FF0A0A0A",
    "white_background"             to "#FF0A0A0A",
    "light_gray_background"        to "#FF0A0A0A",
    "busuu_grey_lesson_background" to "#FF0A0A0A",

    // ── Deepest background ──
    "white_secondary_background"   to "#FF000000",

    // ── Snackbars / tooltips ──
    "busuu_snackbar_dark"          to "#FF1A1A1A",
    "busuu_tooltip_background"     to "#FF1A1A1A",

    // ── Shimmer placeholders ──
    "shimmer_first_color"          to "#FF0F0F0F",
    "shimmer_second_color"         to "#FF181818",

    // ── Dividers ──
    "neutral_ui_divider"           to "#FF1A1A1A",

    // ── Secondary backgrounds ──
    "blue_secondary_background"    to "#FF0A0A0A",
    "busuu_darker_blue_5"          to "#FF0A0A0A",
    "busuu_grey_xlite3"            to "#FF0A0A0A",
    "busuu_grey_xlite_background"  to "#FF0A0A0A",

    // ── Purchasely paywall surfaces ──
    "ply_subscriptions_container"  to "#FF0A0A0A",
    "ply_subscriptions_surface"    to "#FF0F0F0F",

    // ── Material Design dark baseline ──
    "design_dark_default_color_background" to "#FF000000",
    "design_dark_default_color_surface"    to "#FF000000",

    // ── Material 3 dark palette ──
    "m3_sys_color_dark_background"                to "#FF000000",
    "m3_sys_color_dark_surface"                   to "#FF000000",
    "m3_sys_color_dark_surface_dim"               to "#FF000000",
    "m3_sys_color_dark_surface_container_lowest"  to "#FF000000",
    "m3_sys_color_dark_surface_container_low"     to "#FF0A0A0A",
    "m3_sys_color_dark_surface_container"         to "#FF0F0F0F",
    "m3_sys_color_dark_surface_container_high"    to "#FF151515",
    "m3_sys_color_dark_surface_container_highest" to "#FF1A1A1A",
    "m3_sys_color_dark_surface_bright"            to "#FF1A1A1A",
)
