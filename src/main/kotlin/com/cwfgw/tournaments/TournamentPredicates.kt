package com.cwfgw.tournaments

import java.time.LocalDate
import java.time.ZoneId

/**
 * The PGA Tour calendar and ESPN's scoreboard endpoint are organized
 * around US Eastern time. "Has this tournament started?" flips at
 * midnight Eastern, not at midnight UTC (which would be 7–8 PM Eastern
 * the previous evening). Use this zone any time we compare a tournament's
 * [LocalDate] start date against "today."
 */
val ESPN_SCHEDULE_ZONE: ZoneId = ZoneId.of("America/New_York")

/**
 * Opens a Thursday start from the Monday, the earliest ESPN publishes its field and tee times. Keep it small
 * enough that a peak week (current event plus next week's preview) doesn't more than double the candidate count.
 */
private const val LIVE_OVERLAY_LOOKAHEAD_DAYS: Long = 3

/**
 * Exclusive, as [TournamentFilter.startsBefore] is, so a query takes it as is. [isLiveOverlayCandidate] judges
 * from it too: a query and the per-tournament check that each did their own arithmetic could drift apart.
 */
fun liveOverlayWindowEnd(today: LocalDate): LocalDate = today.plusDays(LIVE_OVERLAY_LOOKAHEAD_DAYS + 1)

/**
 * True when [this] tournament is eligible for an ESPN live-overlay pull
 * as of [today]: not already finalized, and its start date is within
 * [LIVE_OVERLAY_LOOKAHEAD_DAYS] of today (or already in the past).
 *
 * Completed tournaments are excluded — their results are in our DB and
 * a live pull just costs latency. Tournaments past the look-ahead
 * window are excluded — they typically have no scoreboard payload yet
 * and we'd burn the cold-start cost for nothing.
 */
fun Tournament.isLiveOverlayCandidate(today: LocalDate): Boolean =
    status != TournamentStatus.Completed &&
        startDate.isBefore(liveOverlayWindowEnd(today))

/**
 * Only an admin's PUT writes [TournamentStatus.InProgress], so a week that has started and is not finalized reads as
 * one from its date. For display only: a gate or a write reads the stored status, which stays as it was.
 */
fun Tournament.statusAsOf(today: LocalDate): TournamentStatus =
    if (status == TournamentStatus.Upcoming && !startDate.isAfter(today)) TournamentStatus.InProgress else status
