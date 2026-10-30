package com.weatherlet.app.alerts

/** "What describes you?" — each profile makes alerts more sensitive where it matters to that person. */
enum class Profile(val label: String, val emoji: String) {
    DRIVER("I drive", "🚗"),
    CYCLIST("I cycle", "🚴"),
    GARDENER("I garden", "🌱"),
    PET_OWNER("I have pets", "🐕"),
    OUTDOOR_WORKER("I work outdoors", "👷"),
    ALLERGY("Allergies / asthma", "🤧"),
    MIGRAINE("Migraines / joint pain", "🤕"),
}

/** How readily alerts fire. Scales every threshold; profiles still tighten on top of it. */
enum class Sensitivity(val label: String, val blurb: String) {
    RELAXED("Relaxed", "Only clearly bad weather"),
    NORMAL("Normal", "Balanced (recommended)"),
    SENSITIVE("Sensitive", "Earlier, more cautious"),
}
