package com.cwfgw.drafts

import com.cwfgw.db.TransactionContext
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FakeDraftRepository(
    initialDrafts: List<Draft> = emptyList(),
    initialPicks: List<DraftPick> = emptyList(),
    private val availableGolfers: List<Golfer> = emptyList(),
    private val draftIdFactory: () -> DraftId = { DraftId(UUID.randomUUID()) },
    private val pickIdFactory: () -> DraftPickId = { DraftPickId(UUID.randomUUID()) },
    private val clock: () -> Instant = Instant::now,
) : DraftRepository {
    private val drafts = ConcurrentHashMap<DraftId, Draft>()
    private val picks = ConcurrentHashMap<DraftPickId, DraftPick>()

    init {
        initialDrafts.forEach { drafts[it.id] = it }
        initialPicks.forEach { picks[it.id] = it }
    }

    context(ctx: TransactionContext)
    override suspend fun findBySeason(seasonId: SeasonId): Draft? =
        drafts.values.firstOrNull { it.seasonId == seasonId }

    context(ctx: TransactionContext)
    override suspend fun create(
        seasonId: SeasonId,
        request: CreateDraftRequest,
    ): Draft {
        val draft =
            Draft(
                id = draftIdFactory(),
                seasonId = seasonId,
                status = "pending",
                draftType = request.draftType ?: "snake",
                startedAt = null,
                completedAt = null,
                createdAt = clock(),
            )
        drafts[draft.id] = draft
        return draft
    }

    context(ctx: TransactionContext)
    override suspend fun updateStatus(
        id: DraftId,
        status: String,
    ): Draft? {
        val current = drafts[id] ?: return null
        val now = clock()
        val updated =
            current.copy(
                status = status,
                startedAt = if (status == "in_progress") now else current.startedAt,
                completedAt = if (status == "completed") now else current.completedAt,
            )
        drafts[id] = updated
        return updated
    }

    context(ctx: TransactionContext)
    override suspend fun getPicks(draftId: DraftId): List<DraftPick> =
        picks.values
            .filter { it.draftId == draftId }
            .sortedBy { it.pickNum }

    context(ctx: TransactionContext)
    override suspend fun createPicks(
        draftId: DraftId,
        slots: List<PickSlot>,
    ): List<DraftPick> =
        slots.map { slot ->
            val pick =
                DraftPick(
                    id = pickIdFactory(),
                    draftId = draftId,
                    teamId = slot.teamId,
                    golferId = null,
                    roundNum = slot.roundNum,
                    pickNum = slot.pickNum,
                    pickedAt = null,
                )
            picks[pick.id] = pick
            pick
        }

    context(ctx: TransactionContext)
    override suspend fun makePick(
        draftId: DraftId,
        pickNum: Int,
        golferId: GolferId,
    ): DraftPick? {
        val target =
            picks.values.firstOrNull { entry ->
                entry.draftId == draftId && entry.pickNum == pickNum && entry.golferId == null
            } ?: return null
        val updated = target.copy(golferId = golferId, pickedAt = clock())
        picks[target.id] = updated
        return updated
    }

    context(ctx: TransactionContext)
    override suspend fun getAvailableGolfers(draftId: DraftId): List<Golfer> {
        val pickedIds =
            picks.values
                .filter { it.draftId == draftId }
                .mapNotNull { it.golferId }
                .toSet()
        return availableGolfers.filterNot { it.id in pickedIds }
    }
}
