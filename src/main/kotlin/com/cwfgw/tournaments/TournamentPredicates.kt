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
 * True when [this] tournament is eligible for an ESPN live-overlay pull
 * as of [today]: not already finalized, and its start date has arrived.
 *
 * Both halves matter — a season schedule loaded for the year typically
 * carries 20+ `Upcoming` tournaments at any given moment, and the
 * live-overlay path walks the candidate list one tournament at a time,
 * rebuilding a full season context and round-tripping ESPN for each.
 * Letting future tournaments through that filter was the dominant cost
 * in the May 10 wedge; letting completed tournaments through is just
 * wasted latency since their results are already in our DB.
 */
fun Tournament.isLiveOverlayCandidate(today: LocalDate): Boolean =
    status != TournamentStatus.Completed && !startDate.isAfter(today)
