package de.thonktank.autosecretary.presentation.mobile

internal fun remainingDurationText(
    readyAtEpochMillis: Long?,
    nowEpochMillis: Long,
): String {
    if (readyAtEpochMillis == null) return ""
    val minutes = ((readyAtEpochMillis - nowEpochMillis).coerceAtLeast(0L) + 59_999L) / 60_000L
    if (minutes < 60L) return "$minutes min"
    val hours = minutes / 60L
    val restMinutes = minutes % 60L
    if (hours < 24L) return if (restMinutes == 0L) "$hours h" else "$hours h $restMinutes min"
    val days = hours / 24L
    val restHours = hours % 24L
    return if (restHours == 0L) "$days d" else "$days d $restHours h"
}
