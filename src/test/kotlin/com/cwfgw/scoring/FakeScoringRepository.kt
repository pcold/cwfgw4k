package com.cwfgw.scoring

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.TournamentId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FakeScoringRepository(
    initialScores: List<FantasyScore> = emptyList(),
    initialStandings: List<SeasonStanding> = emptyList(),
    private val pointTotals: Map<Triple<SeasonId, TeamId, GolferId>, BigDecimal> = emptyMap(),
    private val teamTotals: Map<Pair<SeasonId, TeamId>, TeamSeasonTotals> = emptyMap(),
    private val scoreIdFactory: () -> FantasyScoreId = { FantasyScoreId(UUID.randomUUID()) },
    private val standingIdFactory: () -> SeasonStandingId = { SeasonStandingId(UUID.randomUUID()) },
) : ScoringRepository {
    private val scores = ConcurrentHashMap<FantasyScoreId, FantasyScore>()
    private val standings = ConcurrentHashMap<SeasonStandingId, SeasonStanding>()
    val scoreUpserts = mutableListOf<FantasyScore>()
    val standingUpserts = mutableListOf<SeasonStanding>()

    init {
        initialScores.forEach { scores[it.id] = it }
        initialStandings.forEach { standings[it.id] = it }
    }

    override suspend fun getScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): List<FantasyScore> =
        scores.values
            .filter { it.seasonId == seasonId && it.tournamentId == tournamentId }
            .sortedByDescending { it.points }

    override suspend fun getStandings(seasonId: SeasonId): List<SeasonStanding> =
        standings.values
            .filter { it.seasonId == seasonId }
            .sortedByDescending { it.totalPoints }

    override suspend fun upsertScore(record: UpsertScore): FantasyScore {
        val existing =
            scores.values.firstOrNull { existing ->
                existing.seasonId == record.seasonId &&
                    existing.teamId == record.teamId &&
                    existing.tournamentId == record.tournamentId &&
                    existing.golferId == record.golferId
            }
        val score =
            FantasyScore(
                id = existing?.id ?: scoreIdFactory(),
                seasonId = record.seasonId,
                teamId = record.teamId,
                tournamentId = record.tournamentId,
                golferId = record.golferId,
                points = record.breakdown.payout,
                position = record.breakdown.position,
                numTied = record.breakdown.numTied,
                basePayout = record.breakdown.basePayout,
                ownershipPct = record.breakdown.ownershipPct,
                payout = record.breakdown.payout,
                multiplier = record.breakdown.multiplier,
                calculatedAt = Instant.now(),
            )
        scores[score.id] = score
        scoreUpserts += score
        return score
    }

    override suspend fun golferPointTotal(
        seasonId: SeasonId,
        teamId: TeamId,
        golferId: GolferId,
    ): BigDecimal = pointTotals[Triple(seasonId, teamId, golferId)] ?: BigDecimal.ZERO

    override suspend fun teamSeasonTotals(
        seasonId: SeasonId,
        teamId: TeamId,
    ): TeamSeasonTotals = teamTotals[seasonId to teamId] ?: TeamSeasonTotals(BigDecimal.ZERO, 0)

    override suspend fun upsertStanding(
        seasonId: SeasonId,
        teamId: TeamId,
        totalPoints: BigDecimal,
        tournamentsPlayed: Int,
    ): SeasonStanding {
        val existing =
            standings.values.firstOrNull { existing ->
                existing.seasonId == seasonId && existing.teamId == teamId
            }
        val standing =
            SeasonStanding(
                id = existing?.id ?: standingIdFactory(),
                seasonId = seasonId,
                teamId = teamId,
                totalPoints = totalPoints,
                tournamentsPlayed = tournamentsPlayed,
                lastUpdated = Instant.now(),
            )
        standings[standing.id] = standing
        standingUpserts += standing
        return standing
    }

    override suspend fun deleteByTournament(tournamentId: TournamentId): Int {
        val matching = scores.values.filter { it.tournamentId == tournamentId }
        matching.forEach { scores.remove(it.id) }
        return matching.size
    }

    override suspend fun deleteBySeason(seasonId: SeasonId): Int {
        val matching = scores.values.filter { it.seasonId == seasonId }
        matching.forEach { scores.remove(it.id) }
        return matching.size
    }

    override suspend fun deleteStandingsBySeason(seasonId: SeasonId): Int {
        val matching = standings.values.filter { it.seasonId == seasonId }
        matching.forEach { standings.remove(it.id) }
        return matching.size
    }
}
