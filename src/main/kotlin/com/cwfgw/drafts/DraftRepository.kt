package com.cwfgw.drafts

import com.cwfgw.db.TransactionContext
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.records.DraftPicksRecord
import com.cwfgw.jooq.tables.records.DraftsRecord
import com.cwfgw.jooq.tables.references.DRAFTS
import com.cwfgw.jooq.tables.references.DRAFT_PICKS
import com.cwfgw.jooq.tables.references.GOLFERS
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.TeamId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.impl.DSL

interface DraftRepository {
    context(ctx: TransactionContext)
    suspend fun findBySeason(seasonId: SeasonId): Draft?

    context(ctx: TransactionContext)
    suspend fun create(
        seasonId: SeasonId,
        request: CreateDraftRequest,
    ): Draft

    context(ctx: TransactionContext)
    suspend fun updateStatus(
        id: DraftId,
        status: String,
    ): Draft?

    context(ctx: TransactionContext)
    suspend fun getPicks(draftId: DraftId): List<DraftPick>

    context(ctx: TransactionContext)
    suspend fun createPicks(
        draftId: DraftId,
        slots: List<PickSlot>,
    ): List<DraftPick>

    context(ctx: TransactionContext)
    suspend fun makePick(
        draftId: DraftId,
        pickNum: Int,
        golferId: GolferId,
    ): DraftPick?

    context(ctx: TransactionContext)
    suspend fun getAvailableGolfers(draftId: DraftId): List<Golfer>
}

data class PickSlot(
    val teamId: TeamId,
    val roundNum: Int,
    val pickNum: Int,
)

fun DraftRepository(): DraftRepository = JooqDraftRepository()

private class JooqDraftRepository : DraftRepository {
    context(ctx: TransactionContext)
    override suspend fun findBySeason(seasonId: SeasonId): Draft? =
        withContext(Dispatchers.IO) {
            ctx.dsl.selectFrom(DRAFTS)
                .where(DRAFTS.SEASON_ID.eq(seasonId.value))
                .fetchOne()
                ?.let(::toDraft)
        }

    context(ctx: TransactionContext)
    override suspend fun create(
        seasonId: SeasonId,
        request: CreateDraftRequest,
    ): Draft =
        withContext(Dispatchers.IO) {
            val inserted =
                ctx.dsl.insertInto(DRAFTS)
                    .set(DRAFTS.SEASON_ID, seasonId.value)
                    .set(DRAFTS.DRAFT_TYPE, request.draftType ?: DEFAULT_DRAFT_TYPE)
                    .returning()
                    .fetchOne() ?: error("INSERT RETURNING produced no row for drafts")
            toDraft(inserted)
        }

    context(ctx: TransactionContext)
    override suspend fun updateStatus(
        id: DraftId,
        status: String,
    ): Draft? =
        withContext(Dispatchers.IO) {
            val assignments =
                buildMap<org.jooq.Field<*>, Any?> {
                    put(DRAFTS.STATUS, status)
                    when (status) {
                        "in_progress" -> put(DRAFTS.STARTED_AT, DSL.currentOffsetDateTime())
                        "completed" -> put(DRAFTS.COMPLETED_AT, DSL.currentOffsetDateTime())
                    }
                }
            ctx.dsl.update(DRAFTS)
                .set(assignments)
                .where(DRAFTS.ID.eq(id.value))
                .returning()
                .fetchOne()
                ?.let(::toDraft)
        }

    context(ctx: TransactionContext)
    override suspend fun getPicks(draftId: DraftId): List<DraftPick> =
        withContext(Dispatchers.IO) {
            ctx.dsl.selectFrom(DRAFT_PICKS)
                .where(DRAFT_PICKS.DRAFT_ID.eq(draftId.value))
                .orderBy(DRAFT_PICKS.PICK_NUM.asc())
                .fetch(::toPick)
        }

    context(ctx: TransactionContext)
    override suspend fun createPicks(
        draftId: DraftId,
        slots: List<PickSlot>,
    ): List<DraftPick> =
        withContext(Dispatchers.IO) {
            slots.map { slot ->
                val inserted =
                    ctx.dsl.insertInto(DRAFT_PICKS)
                        .set(DRAFT_PICKS.DRAFT_ID, draftId.value)
                        .set(DRAFT_PICKS.TEAM_ID, slot.teamId.value)
                        .set(DRAFT_PICKS.ROUND_NUM, slot.roundNum)
                        .set(DRAFT_PICKS.PICK_NUM, slot.pickNum)
                        .returning()
                        .fetchOne() ?: error("INSERT RETURNING produced no row for draft_picks")
                toPick(inserted)
            }
        }

    context(ctx: TransactionContext)
    override suspend fun makePick(
        draftId: DraftId,
        pickNum: Int,
        golferId: GolferId,
    ): DraftPick? =
        withContext(Dispatchers.IO) {
            ctx.dsl.update(DRAFT_PICKS)
                .set(DRAFT_PICKS.GOLFER_ID, golferId.value)
                .set(DRAFT_PICKS.PICKED_AT, DSL.currentOffsetDateTime())
                .where(DRAFT_PICKS.DRAFT_ID.eq(draftId.value))
                .and(DRAFT_PICKS.PICK_NUM.eq(pickNum))
                .and(DRAFT_PICKS.GOLFER_ID.isNull)
                .returning()
                .fetchOne()
                ?.let(::toPick)
        }

    context(ctx: TransactionContext)
    override suspend fun getAvailableGolfers(draftId: DraftId): List<Golfer> =
        withContext(Dispatchers.IO) {
            val pickedGolferIds =
                DSL.select(DRAFT_PICKS.GOLFER_ID)
                    .from(DRAFT_PICKS)
                    .where(DRAFT_PICKS.DRAFT_ID.eq(draftId.value))
                    .and(DRAFT_PICKS.GOLFER_ID.isNotNull)
            ctx.dsl.selectFrom(GOLFERS)
                .where(GOLFERS.ACTIVE.eq(true))
                .and(GOLFERS.ID.notIn(pickedGolferIds))
                .orderBy(GOLFERS.WORLD_RANKING.asc().nullsLast(), GOLFERS.LAST_NAME.asc())
                .fetch { record ->
                    Golfer(
                        id = GolferId(checkNotNull(record.id)),
                        pgaPlayerId = record.pgaPlayerId,
                        firstName = record.firstName,
                        lastName = record.lastName,
                        country = record.country,
                        worldRanking = record.worldRanking,
                        active = checkNotNull(record.active),
                        updatedAt = checkNotNull(record.updatedAt).toInstant(),
                    )
                }
        }

    private fun toDraft(record: DraftsRecord): Draft =
        Draft(
            id = DraftId(checkNotNull(record.id) { "drafts.id is NOT NULL but returned null" }),
            seasonId = SeasonId(record.seasonId),
            status = checkNotNull(record.status) { "drafts.status is NOT NULL but returned null" },
            draftType = checkNotNull(record.draftType) { "drafts.draft_type is NOT NULL but returned null" },
            startedAt = record.startedAt?.toInstant(),
            completedAt = record.completedAt?.toInstant(),
            createdAt =
                checkNotNull(record.createdAt) {
                    "drafts.created_at is NOT NULL but returned null"
                }.toInstant(),
        )

    private fun toPick(record: DraftPicksRecord): DraftPick =
        DraftPick(
            id = DraftPickId(checkNotNull(record.id) { "draft_picks.id is NOT NULL but returned null" }),
            draftId = DraftId(record.draftId),
            teamId = TeamId(record.teamId),
            golferId = record.golferId?.let(::GolferId),
            roundNum = record.roundNum,
            pickNum = record.pickNum,
            pickedAt = record.pickedAt?.toInstant(),
        )

    companion object {
        private const val DEFAULT_DRAFT_TYPE = "snake"
    }
}
