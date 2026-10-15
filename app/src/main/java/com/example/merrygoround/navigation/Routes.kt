package com.example.merrygoround.navigation

/** Route table — a single navigation graph, no extra Activities (brief §7). */
object Routes {
    // Unauthenticated
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"

    // Authenticated shell (bottom nav)
    const val HOME = "home"
    const val GROUPS = "groups"
    const val ACTIVITY = "activity"
    const val PROFILE = "profile"

    // Detail destinations
    const val CREATE_GROUP = "groups/create"
    const val JOIN_GROUP = "groups/join"
    const val GROUP_DETAIL = "groups/detail/{groupId}"
    const val CONTRIBUTE = "groups/contribute/{groupId}"
    const val PAYOUT_SCHEDULE = "groups/schedule/{groupId}"
    const val NOTIFICATIONS = "notifications"
    const val EDIT_PROFILE = "profile/edit"
    const val GROUP_SETTINGS = "groups/settings/{groupId}"

    fun groupDetail(groupId: String) = "groups/detail/$groupId"
    fun contribute(groupId: String) = "groups/contribute/$groupId"
    fun payoutSchedule(groupId: String) = "groups/schedule/$groupId"
    fun groupSettings(groupId: String) = "groups/settings/$groupId"

    /** Destinations that show the bottom navigation bar. */
    val bottomBarDestinations = setOf(HOME, GROUPS, ACTIVITY, PROFILE)
}
