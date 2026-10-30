package com.weatherlet.app.alerts

/** "What to do about it": short tickable tasks per alert, profile-aware. */
object Checklist {
    private val generic: Map<AlertType, List<String>> = mapOf(
        AlertType.SNOW to listOf("Charge phones and power banks", "Stock up on food and essentials", "Keep a shovel and grit or salt handy", "Plan for travel or work changes"),
        AlertType.COLD to listOf("Lag exposed pipes and outdoor taps", "Check the heating works", "Check on elderly neighbours or relatives", "Get layers, hats and gloves ready"),
        AlertType.ICE to listOf("Grit or salt paths and steps", "Allow extra time for any journey", "Wear shoes with good grip"),
        AlertType.RAIN to listOf("Clear gutters and drains", "Waterproofs and umbrella by the door", "Lift valuables off the floor", "Check windows and roof for leaks"),
        AlertType.FLOOD to listOf("Charge phones; torch and batteries ready", "Move valuables and documents upstairs", "Know your route to higher ground", "Save your insurer's contact number"),
        AlertType.WIND to listOf("Secure garden furniture, bins and trampolines", "Park away from trees", "Charge devices in case of power cuts", "Check loose fence panels and roof tiles"),
        AlertType.HEAT to listOf("Fill water bottles and plan extra fluids", "Close curtains on sun-facing windows", "Sunscreen and a hat", "Check on elderly or vulnerable people"),
        AlertType.SWING to listOf("Pack layers for both ends of the day", "Keep any medication handy"),
        AlertType.STORM to listOf("Charge phones and power banks", "Unplug sensitive electronics", "Bring in loose outdoor items", "Plan to avoid open areas"),
        AlertType.FOG to listOf("Check lights and wipers", "Allow extra travel time", "Hi-vis for walking or cycling"),
        AlertType.UV to listOf("Sunscreen SPF 30+", "Hat and sunglasses", "Plan shade breaks around midday"),
        AlertType.AIR to listOf("Check air quality before exercising", "Keep windows shut at peak times", "Avoid busy roads when out"),
        AlertType.POLLEN to listOf("Take antihistamines early", "Keep windows closed", "Dry washing indoors", "Wash hair and clothes after being outside"),
        AlertType.DUST to listOf("Close windows and doors", "Mask ready for going outside", "Cover food and water", "Eye drops and any reliever handy"),
        AlertType.PRESSURE to listOf("Bring in loose outdoor items", "Charge phones in case the weather turns", "Drink plenty of water", "Check the forecast for the next 24 hours"),
    )

    private val byProfile: Map<Pair<AlertType, Profile>, List<String>> = mapOf(
        (AlertType.SNOW to Profile.DRIVER) to listOf("Fill the tank and screenwash; check tyres", "Pack a blanket and torch in the car"),
        (AlertType.SNOW to Profile.PET_OWNER) to listOf("Towel for paws; plan short toilet breaks"),
        (AlertType.COLD to Profile.PET_OWNER) to listOf("Warm bedding; keep pets indoors"),
        (AlertType.COLD to Profile.GARDENER) to listOf("Fleece tender plants and move pots to the wall"),
        (AlertType.COLD to Profile.ALLERGY) to listOf("Scarf for your nose and mouth outdoors; reliever with you"),
        (AlertType.ICE to Profile.DRIVER) to listOf("De-icer and scraper in the car", "Leave 15 minutes earlier"),
        (AlertType.ICE to Profile.GARDENER) to listOf("Cover frost-tender plants tonight"),
        (AlertType.ICE to Profile.CYCLIST) to listOf("Take another route or switch to transit"),
        (AlertType.RAIN to Profile.CYCLIST) to listOf("Mudguards, lights and waterproofs packed"),
        (AlertType.RAIN to Profile.DRIVER) to listOf("Check wipers and tyre tread"),
        (AlertType.RAIN to Profile.GARDENER) to listOf("Skip watering; check drainage for seedlings"),
        (AlertType.WIND to Profile.CYCLIST) to listOf("Choose a sheltered route or another day"),
        (AlertType.WIND to Profile.GARDENER) to listOf("Stake tall plants; secure greenhouse panels"),
        (AlertType.WIND to Profile.OUTDOOR_WORKER) to listOf("Secure loose materials; no work at height"),
        (AlertType.HEAT to Profile.PET_OWNER) to listOf("Fresh water and shade for pets; walk early or late"),
        (AlertType.HEAT to Profile.OUTDOOR_WORKER) to listOf("Start earlier and schedule shade breaks"),
        (AlertType.HEAT to Profile.GARDENER) to listOf("Water at dawn or dusk; mulch beds"),
        (AlertType.STORM to Profile.PET_OWNER) to listOf("Safe, quiet space and ID tags for pets"),
        (AlertType.FOG to Profile.DRIVER) to listOf("Fog lights and dipped headlights ready"),
        (AlertType.AIR to Profile.ALLERGY) to listOf("Reliever inhaler and medication in your bag"),
        (AlertType.POLLEN to Profile.ALLERGY) to listOf("Pollen mask for the worst hours"),
        (AlertType.PRESSURE to Profile.MIGRAINE) to listOf("Migraine medication in your bag", "Water bottle filled; don't skip meals", "Rest and screen breaks planned"),
        (AlertType.PRESSURE to Profile.PET_OWNER) to listOf("Quiet, safe space for pets"),
        (AlertType.STORM to Profile.MIGRAINE) to listOf("Migraine medication handy"),
        (AlertType.DUST to Profile.ALLERGY) to listOf("Reliever inhaler and dust mask in your bag"),
    )

    /** Up to 5 items: the user's profile tasks first, then the general ones. */
    fun items(type: AlertType, profiles: Set<Profile>): List<String> {
        val personal = profiles.sortedBy { it.ordinal }.flatMap { byProfile[type to it].orEmpty() }
        return (personal + generic[type].orEmpty()).distinct().take(5)
    }

    /** Stable identity for one tickable item of one alert in one place. */
    fun key(placeKey: String, alert: WeatherAlert, index: Int) =
        "$placeKey|${alert.type.name}|${alert.start}|$index"
}
