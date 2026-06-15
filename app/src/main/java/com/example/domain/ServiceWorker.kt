package com.example.domain

/**
 * Represents a gig / freelance service worker for Phase 2 of நம்ம ஊரு ஆப்.
 *
 * Phase 2 extends the platform beyond retail shops to include skilled tradespeople
 * (electricians, plumbers, carpenters, etc.) who can be booked directly from the
 * app via voice command.
 *
 * @property id            Unique, stable identifier (UUID or server-assigned).
 * @property name          Worker's legal / commonly-used name (not necessarily in
 *                         Tamil script — some workers prefer English names).
 * @property roleTamil     Tamil description of the worker's primary trade, e.g.
 *                         "மின்சார வல்லுனர் (Electrician)".  Shown on the worker
 *                         card in the UI.
 * @property rating        Aggregate rating on a 0.0–5.0 scale derived from verified
 *                         past-job reviews.  Null if the worker has no reviews yet.
 * @property pastWorkImages List of CDN image URLs showing examples of past completed
 *                         jobs.  Used to populate the portfolio gallery on the worker
 *                         detail screen.  May be empty for newly onboarded workers.
 * @property phoneNumber   Mobile number for the worker in E.164 format.  Used for
 *                         both WhatsApp dispatch and IVR booking confirmation.
 * @property isAvailable   Whether the worker is currently accepting new bookings.
 *                         `true` by default; set to `false` when the worker marks
 *                         themselves as busy or on leave through the partner app.
 * @property serviceArea   Comma-separated list of localities / panchayats the worker
 *                         covers (e.g. "பொட்டல்புதூர், நாங்குநேரி, திருவேங்கடம்").
 * @property yearsExp      Number of years of professional experience.  Shown as a
 *                         trust signal next to the rating badge.
 */
data class ServiceWorker(
    val id: String,
    val name: String,
    val roleTamil: String,
    val rating: Float,
    val pastWorkImages: List<String>,
    val phoneNumber: String = "",
    val isAvailable: Boolean = true,
    val serviceArea: String = "பொட்டல்புதூர்",
    val yearsExp: Int = 0
)
