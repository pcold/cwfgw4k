package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.db.updateOrOutraced
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueId
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.NewTeam
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournaments.TournamentId
import io.kotest.core.spec.style.FunSpec
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * A `*RepositoryContractSpec` runs each case once per implementation, against a subject built fresh for that
 * case, so the fake and Postgres answer the same calls and neither sees another case's rows.
 */
internal class ContractSubject<R>(
    val tx: Transactor,
    val repository: R,
    val seeds: ContractSeeds,
)

/** @see ContractSubject */
internal class ContractCase<R>(
    val name: String,
    val verify: suspend ContractSubject<R>.() -> Unit,
)

/** Prefixes each case with [side], so a red run names which implementation drifted. */
internal fun <R> FunSpec.runContract(
    side: String,
    cases: List<ContractCase<R>>,
    subject: () -> ContractSubject<R>,
) {
    cases.forEach { case -> test("$side: ${case.name}") { case.verify(subject()) } }
}

/** Parent rows a case needs only to satisfy a foreign key: real rows in Postgres, bare ids for a fake. */
internal interface ContractSeeds {
    suspend fun league(): LeagueId

    suspend fun season(): SeasonId

    suspend fun golfer(): GolferId

    suspend fun team(): TeamId

    suspend fun tournament(): TournamentId
}

/** @see ContractSeeds */
internal object FakeSeeds : ContractSeeds {
    /** Sign-straddling, so a fake that orders rows by tournament id must order them as Postgres does. */
    private val tournamentIds = signStraddlingUuids()

    override suspend fun league(): LeagueId = LeagueId(UUID.randomUUID())

    override suspend fun season(): SeasonId = SeasonId(UUID.randomUUID())

    override suspend fun golfer(): GolferId = GolferId(UUID.randomUUID())

    override suspend fun team(): TeamId = TeamId(UUID.randomUUID())

    override suspend fun tournament(): TournamentId = TournamentId(tournamentIds())
}

/** @see ContractSeeds */
internal class PostgresSeeds(private val tx: Transactor) : ContractSeeds {
    override suspend fun league(): LeagueId = tx.update { LeagueRepository().create(CreateLeagueRequest("Seeded")).id }

    override suspend fun season(): SeasonId {
        val league = league()
        return tx.update {
            SeasonRepository().create(CreateSeasonRequest(leagueId = league, name = "Seeded", seasonYear = 2026)).id
        }
    }

    override suspend fun golfer(): GolferId =
        tx.update { GolferRepository().create(CreateGolferRequest(firstName = "Seeded", lastName = "Golfer")).id }

    override suspend fun team(): TeamId {
        val season = season()
        return tx.update {
            TeamRepository().create(season, NewTeam(ownerName = "Seeded", teamName = "Seeded")).id
        }
    }

    override suspend fun tournament(): TournamentId = tx.update { seedTournament() }
}

internal data object Outraced

/** How a service sees a write that broke [constraint]: the block rolls back and the call answers Err. */
internal suspend fun <T> Transactor.updateUnlessOutraced(
    constraint: String,
    block: context(WriteTransactionContext) () -> T,
): Result<T, Outraced> = updateOrOutraced(constraint, outraced = { Outraced }) { Result.Ok(block()) }

/** Postgres orders `uuid` bytewise, which the lowercase hex spelling sorts alike; [UUID.compareTo] is signed. */
internal val POSTGRES_UUID_ORDER: Comparator<UUID> = compareBy { it.toString() }

/** Alternates the top bit, so a fake that orders ids by [UUID.compareTo] disagrees with Postgres every time. */
internal fun signStraddlingUuids(): () -> UUID {
    val issued = AtomicLong()
    return {
        val next = issued.incrementAndGet()
        UUID(if (next % 2 == 0L) next else Long.MIN_VALUE + next, next)
    }
}
