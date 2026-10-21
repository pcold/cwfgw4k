package com.cwfgw.http

/**
 * Where `ui/src/shared/api/types.ts` names a wire type other than by its Kotlin simple name: the client
 * names the concept a screen holds, the server the endpoint that returns it. Any other asserted type
 * is looked up by its simple name, and an import that does not resolve is a `tsc` error.
 */
internal val TYPESCRIPT_WIRE_TYPES: Map<String, String> =
    mapOf(
        "com.cwfgw.admin.ConfirmedPick" to "RosterConfirmPick",
        "com.cwfgw.admin.ConfirmedTeam" to "RosterConfirmTeamInput",
        "com.cwfgw.admin.PreviewPick" to "RosterPreviewPick",
        "com.cwfgw.admin.PreviewTeam" to "RosterPreviewTeam",
        "com.cwfgw.admin.RosterPreviewResult" to "RosterPreview",
        "com.cwfgw.admin.RosterUploadResult" to "RosterConfirmResult",
        "com.cwfgw.admin.SkippedEntry" to "SkippedSeasonImportEntry",
        "com.cwfgw.teams.RosterViewPick" to "RosterPick",
        "com.cwfgw.teams.RosterViewTeam" to "RosterTeam",
    )

/**
 * Wire types `types.ts` does not declare, each with its reason, so no assertion is emitted for them.
 * A value class needs no entry: it is its wrapped value on the wire, and `types.ts` reads it as one.
 */
internal val UNMODELLED_WIRE_TYPES: Set<String> =
    setOf(
        // Request bodies sent from exactly one place, each typed inline at its `client/` slice call.
        "com.cwfgw.admin.ConfirmRosterRequest",
        "com.cwfgw.admin.ConfirmSeasonScheduleRequest",
        "com.cwfgw.admin.UploadSeasonRequest",
        "com.cwfgw.leagues.CreateLeagueRequest",
        "com.cwfgw.seasons.CreateSeasonRequest",
        "com.cwfgw.tournaments.UpdateTournamentRequest",
        "com.cwfgw.users.LoginRequest",
        // Read inline, at the one place `client/core.ts` reads a non-2xx body.
        "com.cwfgw.http.ErrorBody",
        "com.cwfgw.http.RefusalBody",
        // The UI never calls `/health`; it is the load balancer's.
        "com.cwfgw.health.HealthResponse",
    )

/**
 * A response can be read partially and stay correct, so an omitted key is a decision, recorded here.
 * The spec fails once the wire stops carrying the field, so the excuse cannot outlive it.
 */
internal val UNMODELLED_WIRE_FIELDS: Map<String, Set<String>> = mapOf()
