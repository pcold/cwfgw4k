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
 * Days before [Tournament.startDate] that the tournament becomes eligible
 * for an ESPN live-overlay pull. PGA Tour events typically start
 * Thursday and ESPN publishes tee times / field commitments somewhere
 * between Monday morning and Wednesday afternoon. With a 3-day window
 * a Thursday start is eligible from the prior Monday, which makes the
 * pre-tournament data (who's playing, tee-time pairings) visible
 * before the round opens — the operator's whole reason for asking.
 *
 * The May 10 wedge was the original argument *against* a look-ahead: a
 * full season carries 20+ `Upcoming` tournaments and the per-event
 * gather was expensive enough that fanning out across all of them was
 * unaffordable. CWF-17 + CWF-18 dropped the gather to O(1) per overlay,
 * so a small look-ahead is back in budget. Keep this small enough that
 * a peak-week (current event + next week's preview) doesn't more than
 * double the candidate count.
 */
private const val LIVE_OVERLAY_LOOKAHEAD_DAYS: Long = 3

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
        !startDate.isAfter(today.plusDays(LIVE_OVERLAY_LOOKAHEAD_DAYS))
