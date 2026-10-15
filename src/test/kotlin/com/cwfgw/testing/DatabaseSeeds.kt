package com.cwfgw.testing

import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.jooq.tables.references.SEASON_RULE_PAYOUTS
import com.cwfgw.jooq.tables.references.SEASON_RULE_SIDE_BET_ROUNDS
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A context parameter, not an extension on [PostgresHarness]: a seed has to share the caller's open
 * transaction, or it can't see a row the caller created earlier in it. [payouts] are (position, amount).
 */
context(ctx: WriteTransactionContext)
fun seedSeasonRules(
    seasonId: SeasonId,
    payouts: List<Pair<Int, BigDecimal>>,
    sideBetRounds: List<Int>,
) {
    payouts.forEach { (pos, amt) ->
        ctx.dsl.insertInto(SEASON_RULE_PAYOUTS)
            .set(SEASON_RULE_PAYOUTS.SEASON_ID, seasonId.value)
            .set(SEASON_RULE_PAYOUTS.POSITION, pos)
            .set(SEASON_RULE_PAYOUTS.AMOUNT, amt)
            .execute()
    }
    sideBetRounds.forEach { round ->
        ctx.dsl.insertInto(SEASON_RULE_SIDE_BET_ROUNDS)
            .set(SEASON_RULE_SIDE_BET_ROUNDS.SEASON_ID, seasonId.value)
            .set(SEASON_RULE_SIDE_BET_ROUNDS.ROUND, round)
            .execute()
    }
}

/** Seeds a fresh league and season on every call, so two seeded tournaments never share a season. */
context(ctx: WriteTransactionContext)
fun seedTournament(pgaTournamentId: String? = null): TournamentId {
    val league = LeagueRepository().create(CreateLeagueRequest(name = "Castlewood Fantasy Golf"))
    val season =
        SeasonRepository().create(CreateSeasonRequest(leagueId = league.id, name = "2026 Season", seasonYear = 2026))
    return TournamentRepository().create(
        CreateTournamentRequest(
            name = "Zurich Classic",
            seasonId = season.id,
            startDate = LocalDate.parse("2026-04-23"),
            endDate = LocalDate.parse("2026-04-26"),
            pgaTournamentId = pgaTournamentId,
        ),
    ).id
}

/** @see seedSeasonRules */
context(ctx: WriteTransactionContext)
fun seedTeams(seasonId: SeasonId): List<TeamId> =
    listOf("Alpha", "Bravo", "Charlie").map { name ->
        TeamRepository().create(seasonId, CreateTeamRequest(ownerName = "Owner $name", teamName = name)).id
    }
