package com.example.domain

/**
 * Represents all supported hyper-local business categories in நம்ம ஊரு ஆப்.
 *
 * Using a sealed class instead of a raw String ensures:
 *  - Compile-time exhaustive `when` branches (no missed categories).
 *  - A single source of truth for the Tamil display label used in both the
 *    UI category grid and the [Shop.category] field.
 *  - Easy addition of new Phase-2 categories without breaking existing call sites.
 */
sealed class ShopCategory(
    /** The Tamil string stored in [Shop.category] and shown in the UI. */
    val labelTamil: String,
    /** A short English identifier used for analytics / logging. */
    val labelEnglish: String
) {
    /** உணவகங்கள் — local hotels / eateries. */
    object Hotel : ShopCategory("ஹோட்டல்", "hotel")

    /** மருந்தகம் — pharmacies and medical shops. */
    object Medical : ShopCategory("மெடிக்கல்", "medical")

    /** மளிகை — grocery / provisions stores. */
    object Grocery : ShopCategory("மளிகை", "grocery")

    /** கோழி / ஆட்டிறைச்சி / மீன் — meat and fish stalls. */
    object Meat : ShopCategory("இறைச்சி", "meat")

    /**
     * Convenience factory: convert a raw [labelTamil] string (e.g. stored in
     * a data source) back to the corresponding sealed class instance.
     *
     * Returns `null` when the string does not match a known category, so callers
     * can decide whether to silently skip or surface an error.
     */
    companion object {
        fun fromLabel(label: String): ShopCategory? = when (label) {
            Hotel.labelTamil   -> Hotel
            Medical.labelTamil -> Medical
            Grocery.labelTamil -> Grocery
            Meat.labelTamil    -> Meat
            else               -> null
        }

        /** All supported categories, useful for building the Home-Screen grid. */
        val all: List<ShopCategory>
            get() = listOf(Hotel, Medical, Grocery, Meat)
    }
}

/**
 * Core domain entity representing a local shop or business registered on
 * நம்ம ஊரு ஆப்.
 *
 * This is a **pure domain model** — it carries no Android framework dependencies
 * and is safe to use in ViewModels, repositories, and unit tests alike.
 *
 * @property id            Unique, stable identifier for this shop entry (UUID or
 *                         server-assigned string).  Used as the navigation argument
 *                         when opening [OrderScreen].
 * @property nameTamil     The shop's display name written in Tamil script, e.g.
 *                         "சக்தி உணவகம்".  Always present; never empty.
 * @property category      Tamil category label that matches one of the values
 *                         declared in [ShopCategory].  Use [ShopCategory.fromLabel]
 *                         to convert to the typed sealed class.
 * @property whatsAppNumber Merchant's WhatsApp-enabled mobile number in E.164
 *                         format (e.g. "+919876543210").  Used by [OrderScreen]
 *                         to construct the deep-link Intent and by
 *                         [ShopRepository.triggerIvrVoiceAlert] for the telephony
 *                         gateway call.
 * @property isSubscribed  Whether the merchant has an active நம்ம ஊரு ஆப்
 *                         subscription.  Un-subscribed shops are displayed with a
 *                         "Coming Soon" badge and their order flow is disabled.
 * @property imageUrl      Optional CDN URL for the shop's banner / profile image.
 *                         Empty string signals "no image"; the UI should render a
 *                         category-specific placeholder in that case.
 * @property address       Human-readable address shown on the shop detail card.
 *                         Defaults to empty for backward compatibility with older
 *                         data that may not include location info.
 * @property openingHours  Localised operating hours shown as a plain string (e.g.
 *                         "காலை 7 - இரவு 10").  Defaults to empty string.
 * @property rating        Average customer rating on a 0–5 scale.  Null until at
 *                         least one rating has been submitted.
 */
data class Shop(
    val id: String,
    val nameTamil: String,
    val category: String,
    val whatsAppNumber: String,
    val isSubscribed: Boolean,
    val imageUrl: String,
    val address: String = "",
    val openingHours: String = "",
    val rating: Float? = null
) {
    /**
     * Convenience accessor that returns the typed [ShopCategory] for this shop,
     * or `null` if [category] holds an unrecognised value.
     */
    val typedCategory: ShopCategory?
        get() = ShopCategory.fromLabel(category)
}
