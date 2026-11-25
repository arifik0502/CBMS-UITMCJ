package com.crispyburger.admin.data

import java.util.Locale
import kotlin.math.pow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON used both for the database and the live preview. Defaults are written so the server sees every field. */
val SiteJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
data class SiteTheme(
    val bg: String = "#FFC93C",
    val ink: String = "#1B1410",
    val accent: String = "#D7263D",
    val good: String = "#2B7A3D",
    val paper: String = "#FFFDF7",
    val font: String = "default"
)

@Serializable
data class SiteBanner(
    val enabled: Boolean = false,
    val text: String = "",
    val tone: String = "info",
    @SerialName("link_label") val linkLabel: String = "",
    @SerialName("link_to") val linkTo: String = ""
)

@Serializable
data class SiteSection(
    val id: String,
    val type: String,
    val visible: Boolean = true,
    val title: String = "",
    val body: String = "",
    val headline: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    val caption: String = "",
    val label: String = "",
    val to: String = "",
    val items: List<String> = emptyList()
)

@Serializable
data class SiteConfig(
    val v: Int = 1,
    @SerialName("brand_name") val brandName: String = "",
    val theme: SiteTheme = SiteTheme(),
    val banner: SiteBanner = SiteBanner(),
    val sections: List<SiteSection> = emptyList(),
    @SerialName("footer_note") val footerNote: String = ""
)

@Serializable
data class SiteState(
    val version: Int = 0,
    val published: SiteConfig? = null,
    val draft: SiteConfig? = null,
    @SerialName("has_unpublished") val hasUnpublished: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null
)

@Serializable
data class SiteVersion(
    val id: String,
    val version: Int,
    @SerialName("published_at") val publishedAt: String
)

object SectionType {
    const val HERO = "hero"
    const val MENU = "menu"
    const val STEPS = "steps"
    const val TRIPS = "trips"
    const val BUILDINGS = "buildings"
    const val SUPPORT = "support"
    const val TEXT = "text"
    const val IMAGE = "image"
    const val BUTTON = "button"

    val custom = setOf(TEXT, IMAGE, BUTTON)

    /** Plain-English name shown to the owner. */
    fun label(type: String): String = when (type) {
        HERO -> "Top banner picture & headline"
        MENU -> "Menu & ordering"
        STEPS -> "How to order"
        TRIPS -> "Delivery times"
        BUILDINGS -> "Where we deliver"
        SUPPORT -> "Problem-report link"
        TEXT -> "Text box"
        IMAGE -> "Picture"
        BUTTON -> "Button"
        else -> type
    }

    fun glyph(type: String): String = when (type) {
        HERO -> "🖼️"
        MENU -> "🍔"
        STEPS -> "📋"
        TRIPS -> "🕒"
        BUILDINGS -> "🏢"
        SUPPORT -> "🎫"
        TEXT -> "📝"
        IMAGE -> "📷"
        BUTTON -> "🔗"
        else -> "▫️"
    }
}

data class SitePreset(val name: String, val theme: SiteTheme)

data class LinkTarget(val label: String, val path: String)

object SiteRules {
    const val MAX_CUSTOM = 10
    const val MAX_STEPS = 8

    val DEFAULT_CONFIG = SiteConfig(
        sections = listOf(
            SiteSection(id = "hero", type = SectionType.HERO, headline = "Hot crispy burgers at your dorm door."),
            SiteSection(id = "menu", type = SectionType.MENU, title = "Menu"),
            SiteSection(
                id = "steps", type = SectionType.STEPS,
                items = listOf(
                    "Add burgers to your cart.",
                    "Pick your delivery trip at checkout.",
                    "Choose your college. KKB and KKC deliveries go to the college lobby.",
                    "Pay cash on delivery or by DuitNow QR.",
                    "Follow your delivery under My orders."
                )
            ),
            SiteSection(
                id = "trips", type = SectionType.TRIPS, title = "Delivery trips",
                body = "Choose the delivery window that suits you. Orders stay open until that window ends."
            ),
            SiteSection(id = "buildings", type = SectionType.BUILDINGS, body = "We deliver to"),
            SiteSection(id = "support", type = SectionType.SUPPORT, body = "Problem with an order?", label = "Report it")
        )
    )

    /** Every preset keeps dark text on a light background (the checkout form fields are always white). */
    val PRESETS: List<SitePreset> = listOf(
        SitePreset("Classic", SiteTheme()),
        SitePreset("Fresh green", SiteTheme(bg = "#CDEBC0", ink = "#12301B", accent = "#C2410C", good = "#1E6B34", paper = "#FBFFF8")),
        SitePreset("Ocean", SiteTheme(bg = "#BFE3F5", ink = "#0B2A3D", accent = "#0E6BA8", good = "#0F766E", paper = "#F7FCFF")),
        SitePreset("Berry", SiteTheme(bg = "#F6C6D8", ink = "#3A0F24", accent = "#A3195B", good = "#2F6B3A", paper = "#FFF8FB")),
        SitePreset("Sunset", SiteTheme(bg = "#FFB38A", ink = "#2B1308", accent = "#B91C1C", good = "#2B7A3D", paper = "#FFF7F0")),
        SitePreset("Grey", SiteTheme(bg = "#E5E7EB", ink = "#111827", accent = "#1F2937", good = "#166534", paper = "#FFFFFF"))
    )

    val SWATCHES: List<String> = listOf(
        "#FFC93C", "#CDEBC0", "#BFE3F5", "#F6C6D8", "#FFB38A", "#E5E7EB", "#FFFDF7", "#FFFFFF",
        "#1B1410", "#12301B", "#0B2A3D", "#3A0F24", "#111827",
        "#D7263D", "#C2410C", "#B91C1C", "#A3195B", "#0E6BA8", "#1F2937", "#2B7A3D", "#1E6B34", "#0F766E"
    )

    val FONTS: List<Pair<String, String>> = listOf(
        "default" to "Original", "friendly" to "Friendly", "classic" to "Classic serif", "modern" to "Clean modern"
    )

    val LINKS: List<LinkTarget> = listOf(
        LinkTarget("Menu (home page)", "/"),
        LinkTarget("My orders", "/orders"),
        LinkTarget("Report a problem", "/support")
    )

    fun isHex(value: String): Boolean = Regex("^#[0-9a-fA-F]{6}$").matches(value)

    fun normalizeHex(input: String): String? {
        val t = input.trim().let { if (it.startsWith("#")) it else "#$it" }
        return if (isHex(t)) t.uppercase(Locale.ROOT) else null
    }

    fun isValidLink(value: String): Boolean =
        value.length <= 200 && !value.any { it.isWhitespace() } &&
            (Regex("^/([^/].*)?$").matches(value) || Regex("^https://[^/]+.*$").matches(value))

    fun luminance(hex: String): Double {
        val n = hex.removePrefix("#").toInt(16)
        fun channel(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel((n shr 16) and 255) + 0.7152 * channel((n shr 8) and 255) + 0.0722 * channel(n and 255)
    }

    fun contrast(a: String, b: String): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Plain-English reasons the draft must not go live yet. Empty means it is safe to publish. */
    fun problems(c: SiteConfig): List<String> {
        val out = mutableListOf<String>()
        val t = c.theme
        val colors = listOf(t.bg, t.ink, t.accent, t.good, t.paper)
        if (colors.any { !isHex(it) }) {
            out += "One of the colours is not valid."
            return out
        }
        if (contrast(t.ink, t.bg) < 4.5) out += "Text is hard to read on the page colour. Pick a darker text colour or a lighter page colour."
        if (contrast(t.ink, t.paper) < 4.5) out += "Text is hard to read on the card colour."
        if (contrast(t.ink, "#FFFFFF") < 4.5) out += "Text colour is too light: checkout form fields are always white."
        if (contrast("#FFFFFF", t.accent) < 4.5) out += "The accent colour is too light for white text on it (prices, badges)."
        if (contrast("#FFFFFF", t.good) < 4.5) out += "The “good/in stock” colour is too light for white text on it."

        val menu = c.sections.firstOrNull { it.type == SectionType.MENU }
        if (menu == null || !menu.visible) out += "The menu must stay on the page so customers can order."
        if (c.banner.enabled && c.banner.text.isBlank()) out += "The announcement is switched on but has no text."
        if (c.banner.linkLabel.isNotBlank() && !isValidLink(c.banner.linkTo)) out += "The announcement link needs a place to go."
        if (c.banner.linkTo.isNotBlank() && !isValidLink(c.banner.linkTo)) out += "The announcement link address is not valid."
        c.sections.forEach { s ->
            val name = SectionType.label(s.type)
            when (s.type) {
                SectionType.IMAGE -> if (s.visible && s.imageUrl.isNullOrBlank()) out += "“$name” has no picture yet. Add one or hide it."
                SectionType.BUTTON -> if (s.visible && (s.label.isBlank() || !isValidLink(s.to))) out += "“$name” needs a label and a place to go."
                SectionType.TEXT -> if (s.visible && s.title.isBlank() && s.body.isBlank()) out += "“$name” is empty. Write something or hide it."
                SectionType.HERO -> if (s.visible && s.headline.isBlank()) out += "The top headline is empty."
            }
        }
        if (c.sections.count { it.type in SectionType.custom } > MAX_CUSTOM) out += "Too many extra boxes (max $MAX_CUSTOM)."
        return out
    }

    // ---- layout edits (all pure: return a new config) ----

    fun move(c: SiteConfig, id: String, delta: Int): SiteConfig {
        val list = c.sections.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return c
        val item = list.removeAt(i)
        list.add(j, item)
        return c.copy(sections = list)
    }

    fun setVisible(c: SiteConfig, id: String, visible: Boolean): SiteConfig {
        val s = c.sections.firstOrNull { it.id == id } ?: return c
        if (s.type == SectionType.MENU && !visible) return c
        return replace(c, s.copy(visible = visible))
    }

    fun replace(c: SiteConfig, section: SiteSection): SiteConfig =
        c.copy(sections = c.sections.map { if (it.id == section.id) section else it })

    fun canAddCustom(c: SiteConfig): Boolean = c.sections.count { it.type in SectionType.custom } < MAX_CUSTOM

    fun addCustom(c: SiteConfig, type: String, idSuffix: String): SiteConfig {
        require(type in SectionType.custom) { "Not an extra box type." }
        if (!canAddCustom(c)) return c
        val id = "c_" + idSuffix.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(10)
        if (c.sections.any { it.id == id }) return c
        return c.copy(sections = c.sections + SiteSection(id = id, type = type, label = if (type == SectionType.BUTTON) "Open" else ""))
    }

    fun removeCustom(c: SiteConfig, id: String): SiteConfig {
        val s = c.sections.firstOrNull { it.id == id } ?: return c
        if (s.type !in SectionType.custom) return c
        return c.copy(sections = c.sections.filterNot { it.id == id })
    }

    /** A section the live config is missing (older drafts) is added back so the editor always shows all of them. */
    fun withAllBuiltIns(c: SiteConfig): SiteConfig {
        val missing = DEFAULT_CONFIG.sections.filter { d -> c.sections.none { it.type == d.type } }
        return if (missing.isEmpty()) c else c.copy(sections = c.sections + missing.map { it.copy(visible = false) })
    }
}
