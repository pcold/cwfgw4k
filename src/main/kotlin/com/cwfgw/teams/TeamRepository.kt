package com.cwfgw.teams

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.db.required
import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.keys.TEAMS_SEASON_ID_TEAM_NAME_KEY
import com.cwfgw.jooq.tables.records.TeamRostersRecord
import com.cwfgw.jooq.tables.records.TeamsRecord
import com.cwfgw.jooq.tables.references.GOLFERS
import com.cwfgw.jooq.tables.references.TEAMS
import com.cwfgw.jooq.tables.references.TEAM_ROSTERS
import com.cwfgw.seasons.SeasonId
import org.jooq.impl.DSL
import java.math.BigDecimal

interface TeamRepository {
    context(ctx: TransactionContext)
    fun findBySeason(seasonId: SeasonId): List<Team>

    context(ctx: WriteTransactionContext)
    fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
    ): Team

    /** Ordered by `(team_id, draft_round, acquired_at)` so callers can group by team deterministically. */
    context(ctx: TransactionContext)
    fun findRostersBySeason(seasonId: SeasonId): List<RosterEntry>

    context(ctx: WriteTransactionContext)
    fun addToRoster(
        teamId: TeamId,
        request: AddToRosterRequest,
    ): RosterEntry

    context(ctx: TransactionContext)
    fun getRosterView(seasonId: SeasonId): List<RosterViewTeam>
}

fun TeamRepository(): TeamRepository = JooqTeamRepository()

internal val UNIQUE_TEAM_NAME_PER_SEASON: String = TEAMS_SEASON_ID_TEAM_NAME_KEY.name

private const val DEFAULT_ACQUIRED_VIA = "free_agent"
private val DEFAULT_OWNERSHIP_PCT: BigDecimal = BigDecimal("100.00")

private data class RosterViewRow(
    val teamId: TeamId,
    val teamNumber: Int?,
    val teamName: String,
    val round: Int,
    val firstName: String,
    val lastName: String,
    val ownershipPct: BigDecimal,
    val golferId: GolferId,
)

private class JooqTeamRepository : TeamRepository {
    context(ctx: TransactionContext)
    override fun findBySeason(seasonId: SeasonId): List<Team> =
        ctx.dsl.selectFrom(TEAMS)
            .where(TEAMS.SEASON_ID.eq(seasonId.value))
            .orderBy(TEAMS.TEAM_NUMBER.asc().nullsLast(), TEAMS.TEAM_NAME.asc())
            .fetch(::toTeam)

    context(ctx: WriteTransactionContext)
    override fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
    ): Team {
        val inserted =
            ctx.dsl.insertInto(TEAMS)
                .set(TEAMS.SEASON_ID, seasonId.value)
                .set(TEAMS.OWNER_NAME, request.ownerName)
                .set(TEAMS.TEAM_NAME, request.teamName)
                .set(TEAMS.TEAM_NUMBER, request.teamNumber)
                .returning()
                .fetchSingle()
        return toTeam(inserted)
    }

    context(ctx: TransactionContext)
    override fun findRostersBySeason(seasonId: SeasonId): List<RosterEntry> =
        ctx.dsl.selectFrom(TEAM_ROSTERS)
            .where(
                TEAM_ROSTERS.TEAM_ID.`in`(
                    DSL.select(TEAMS.ID).from(TEAMS).where(TEAMS.SEASON_ID.eq(seasonId.value)),
                ),
            )
            .orderBy(
                TEAM_ROSTERS.TEAM_ID.asc(),
                TEAM_ROSTERS.DRAFT_ROUND.asc().nullsLast(),
                TEAM_ROSTERS.ACQUIRED_AT.asc(),
            )
            .fetch(::toRosterEntry)

    context(ctx: WriteTransactionContext)
    override fun addToRoster(
        teamId: TeamId,
        request: AddToRosterRequest,
    ): RosterEntry {
        val inserted =
            ctx.dsl.insertInto(TEAM_ROSTERS)
                .set(TEAM_ROSTERS.TEAM_ID, teamId.value)
                .set(TEAM_ROSTERS.GOLFER_ID, request.golferId.value)
                .set(TEAM_ROSTERS.ACQUIRED_VIA, request.acquiredVia ?: DEFAULT_ACQUIRED_VIA)
                .set(TEAM_ROSTERS.DRAFT_ROUND, request.draftRound)
                .set(TEAM_ROSTERS.OWNERSHIP_PCT, request.ownershipPct ?: DEFAULT_OWNERSHIP_PCT)
                .returning()
                .fetchSingle()
        return toRosterEntry(inserted)
    }

    context(ctx: TransactionContext)
    override fun getRosterView(seasonId: SeasonId): List<RosterViewTeam> {
        val rows = fetchRosterViewRows(seasonId)
        return rows.groupBy { Triple(it.teamId, it.teamNumber, it.teamName) }.map { (team, picks) ->
            val (teamId, teamNumber, teamName) = team
            RosterViewTeam(
                teamId = teamId,
                teamNumber = teamNumber,
                teamName = teamName,
                picks = picks.map(::toRosterViewPick),
            )
        }
    }

    context(ctx: TransactionContext)
    private fun fetchRosterViewRows(seasonId: SeasonId): List<RosterViewRow> =
        ctx.dsl.select(
            TEAMS.ID,
            TEAMS.TEAM_NUMBER,
            TEAMS.TEAM_NAME,
            TEAM_ROSTERS.DRAFT_ROUND,
            GOLFERS.FIRST_NAME,
            GOLFERS.LAST_NAME,
            TEAM_ROSTERS.OWNERSHIP_PCT,
            GOLFERS.ID,
        )
            .from(TEAM_ROSTERS)
            .join(TEAMS).on(TEAM_ROSTERS.TEAM_ID.eq(TEAMS.ID))
            .join(GOLFERS).on(TEAM_ROSTERS.GOLFER_ID.eq(GOLFERS.ID))
            .where(TEAMS.SEASON_ID.eq(seasonId.value))
            .orderBy(
                TEAMS.TEAM_NUMBER.asc().nullsLast(),
                TEAMS.TEAM_NAME.asc(),
                TEAM_ROSTERS.DRAFT_ROUND.asc().nullsLast(),
            )
            .fetch { record ->
                RosterViewRow(
                    teamId = TeamId(checkNotNull(record[TEAMS.ID])),
                    teamNumber = record[TEAMS.TEAM_NUMBER],
                    teamName = checkNotNull(record[TEAMS.TEAM_NAME]),
                    round = record[TEAM_ROSTERS.DRAFT_ROUND] ?: 0,
                    firstName = checkNotNull(record[GOLFERS.FIRST_NAME]),
                    lastName = checkNotNull(record[GOLFERS.LAST_NAME]),
                    ownershipPct = checkNotNull(record[TEAM_ROSTERS.OWNERSHIP_PCT]),
                    golferId = GolferId(checkNotNull(record[GOLFERS.ID])),
                )
            }

    private fun toRosterViewPick(row: RosterViewRow): RosterViewPick =
        RosterViewPick(
            round = row.round,
            golferName = if (row.firstName.isNotEmpty()) "${row.firstName} ${row.lastName}" else row.lastName,
            ownershipPct = row.ownershipPct,
            golferId = row.golferId,
        )

    private fun toTeam(record: TeamsRecord): Team =
        Team(
            id = TeamId(record.required(TEAMS.ID)),
            seasonId = SeasonId(record.seasonId),
            ownerName = record.ownerName,
            teamName = record.teamName,
            teamNumber = record.teamNumber,
            createdAt = record.required(TEAMS.CREATED_AT).toInstant(),
            updatedAt = record.required(TEAMS.UPDATED_AT).toInstant(),
        )

    private fun toRosterEntry(record: TeamRostersRecord): RosterEntry =
        RosterEntry(
            id = RosterEntryId(record.required(TEAM_ROSTERS.ID)),
            teamId = TeamId(record.teamId),
            golferId = GolferId(record.golferId),
            acquiredVia = record.required(TEAM_ROSTERS.ACQUIRED_VIA),
            draftRound = record.draftRound,
            ownershipPct = record.required(TEAM_ROSTERS.OWNERSHIP_PCT),
            acquiredAt = record.required(TEAM_ROSTERS.ACQUIRED_AT).toInstant(),
            isActive = record.required(TEAM_ROSTERS.IS_ACTIVE),
        )
}
