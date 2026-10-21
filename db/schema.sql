--
-- Generated from src/main/resources/db/migration by ./gradlew updateSchemaDump.
-- `check` fails when the two disagree.
--

\restrict cwfgw4kschemadump


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: cwfgw4k; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA cwfgw4k;


--
-- Name: refuse_override_to_tombstoned_golfer(); Type: FUNCTION; Schema: cwfgw4k; Owner: -
--

CREATE FUNCTION cwfgw4k.refuse_override_to_tombstoned_golfer() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'cwfgw4k', '$user', 'public'
    AS $$
DECLARE
    tombstoned TIMESTAMPTZ;
BEGIN
    -- Not filtered on tombstoned_at: the snapshot reads an uncommitted tombstone as NULL, so the row would never be locked.
    SELECT tombstoned_at INTO tombstoned FROM golfers WHERE id = NEW.golfer_id FOR SHARE;
    IF tombstoned IS NOT NULL THEN
        RAISE EXCEPTION 'golfer % is tombstoned and cannot be linked', NEW.golfer_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: cache; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE UNLOGGED TABLE cwfgw4k.cache (
    key text NOT NULL,
    value jsonb NOT NULL,
    expires_at timestamp with time zone NOT NULL
);


--
-- Name: cache_generation; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.cache_generation (
    singleton boolean DEFAULT true NOT NULL,
    generation bigint NOT NULL,
    CONSTRAINT cache_generation_singleton_check CHECK (singleton)
);


--
-- Name: fantasy_scores; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.fantasy_scores (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    season_id uuid NOT NULL,
    team_id uuid NOT NULL,
    tournament_id uuid NOT NULL,
    golfer_id uuid NOT NULL,
    points numeric(12,4) DEFAULT 0 NOT NULL,
    "position" integer DEFAULT 0 NOT NULL,
    num_tied integer DEFAULT 1 NOT NULL,
    base_payout numeric(10,4) DEFAULT 0 NOT NULL,
    ownership_pct numeric(10,4) DEFAULT 100 NOT NULL,
    payout numeric(10,4) DEFAULT 0 NOT NULL,
    multiplier numeric(4,2) DEFAULT 1 NOT NULL,
    calculated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: golfers; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.golfers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pga_player_id text,
    first_name text NOT NULL,
    last_name text NOT NULL,
    country text,
    world_ranking integer,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tombstoned_at timestamp with time zone,
    merged_into uuid,
    CONSTRAINT golfers_merged_into_is_tombstoned CHECK (((merged_into IS NULL) OR (tombstoned_at IS NOT NULL))),
    CONSTRAINT golfers_merged_into_other_row CHECK ((merged_into <> id))
);


--
-- Name: leagues; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.leagues (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: season_rule_payouts; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.season_rule_payouts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    season_id uuid NOT NULL,
    "position" integer NOT NULL,
    amount numeric(10,4) NOT NULL,
    CONSTRAINT season_rule_payouts_amount_non_negative CHECK ((amount >= (0)::numeric))
);


--
-- Name: season_rule_side_bet_rounds; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.season_rule_side_bet_rounds (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    season_id uuid NOT NULL,
    round integer NOT NULL,
    CONSTRAINT season_rule_side_bet_rounds_round_in_range CHECK (((round >= 1) AND (round <= 8)))
);


--
-- Name: seasons; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.seasons (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    league_id uuid NOT NULL,
    name text NOT NULL,
    season_year integer NOT NULL,
    season_number integer NOT NULL,
    tie_floor numeric(10,4) DEFAULT 1 NOT NULL,
    side_bet_amount numeric(10,4) DEFAULT 15 NOT NULL,
    max_teams integer DEFAULT 10 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT seasons_max_teams_positive CHECK ((max_teams >= 1)),
    CONSTRAINT seasons_name_not_blank CHECK ((name ~ '[^ \t\n\r\v\f]'::text)),
    CONSTRAINT seasons_side_bet_amount_non_negative CHECK ((side_bet_amount >= (0)::numeric)),
    CONSTRAINT seasons_tie_floor_non_negative CHECK ((tie_floor >= (0)::numeric))
);


--
-- Name: team_rosters; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.team_rosters (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    team_id uuid NOT NULL,
    golfer_id uuid NOT NULL,
    acquired_via text DEFAULT 'draft'::text NOT NULL,
    draft_round integer,
    ownership_pct numeric(5,2) DEFAULT 100.00 NOT NULL,
    acquired_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT team_rosters_draft_round_in_range CHECK (((draft_round >= 1) AND (draft_round <= 8))),
    CONSTRAINT team_rosters_ownership_pct_in_range CHECK (((ownership_pct > (0)::numeric) AND (ownership_pct <= (100)::numeric)))
);


--
-- Name: teams; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.teams (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    season_id uuid NOT NULL,
    owner_name text NOT NULL,
    team_name text NOT NULL,
    team_number integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tournament_player_overrides; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.tournament_player_overrides (
    tournament_id uuid NOT NULL,
    espn_competitor_id text NOT NULL,
    golfer_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tournament_results; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.tournament_results (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tournament_id uuid NOT NULL,
    golfer_id uuid NOT NULL,
    "position" integer,
    score_to_par integer,
    total_strokes integer,
    earnings bigint,
    round1 integer,
    round2 integer,
    round3 integer,
    round4 integer,
    made_cut boolean DEFAULT true NOT NULL,
    pair_key text
);


--
-- Name: tournaments; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.tournaments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pga_tournament_id text,
    name text NOT NULL,
    season_id uuid NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    course_name text,
    status text DEFAULT 'upcoming'::text NOT NULL,
    purse_amount bigint,
    payout_multiplier numeric(6,4) DEFAULT 1.00 NOT NULL,
    week text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    is_team_event boolean DEFAULT false NOT NULL,
    CONSTRAINT tournaments_status_known CHECK ((status = ANY (ARRAY['upcoming'::text, 'in_progress'::text, 'completed'::text])))
);


--
-- Name: users; Type: TABLE; Schema: cwfgw4k; Owner: -
--

CREATE TABLE cwfgw4k.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    username text NOT NULL,
    password_hash text NOT NULL,
    role text DEFAULT 'user'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT users_role_known CHECK ((role = ANY (ARRAY['admin'::text, 'user'::text])))
);


--
-- Name: cache_generation cache_generation_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.cache_generation
    ADD CONSTRAINT cache_generation_pkey PRIMARY KEY (singleton);


--
-- Name: cache cache_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.cache
    ADD CONSTRAINT cache_pkey PRIMARY KEY (key);


--
-- Name: fantasy_scores fantasy_scores_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.fantasy_scores
    ADD CONSTRAINT fantasy_scores_pkey PRIMARY KEY (id);


--
-- Name: fantasy_scores fantasy_scores_season_id_team_id_tournament_id_golfer_id_key; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.fantasy_scores
    ADD CONSTRAINT fantasy_scores_season_id_team_id_tournament_id_golfer_id_key UNIQUE (season_id, team_id, tournament_id, golfer_id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: golfers golfers_pga_player_id_key; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.golfers
    ADD CONSTRAINT golfers_pga_player_id_key UNIQUE (pga_player_id);


--
-- Name: golfers golfers_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.golfers
    ADD CONSTRAINT golfers_pkey PRIMARY KEY (id);


--
-- Name: leagues leagues_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.leagues
    ADD CONSTRAINT leagues_pkey PRIMARY KEY (id);


--
-- Name: season_rule_payouts season_rule_payouts_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.season_rule_payouts
    ADD CONSTRAINT season_rule_payouts_pkey PRIMARY KEY (id);


--
-- Name: season_rule_payouts season_rule_payouts_season_id_position_key; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.season_rule_payouts
    ADD CONSTRAINT season_rule_payouts_season_id_position_key UNIQUE (season_id, "position");


--
-- Name: season_rule_side_bet_rounds season_rule_side_bet_rounds_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.season_rule_side_bet_rounds
    ADD CONSTRAINT season_rule_side_bet_rounds_pkey PRIMARY KEY (id);


--
-- Name: season_rule_side_bet_rounds season_rule_side_bet_rounds_season_id_round_key; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.season_rule_side_bet_rounds
    ADD CONSTRAINT season_rule_side_bet_rounds_season_id_round_key UNIQUE (season_id, round);


--
-- Name: seasons seasons_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.seasons
    ADD CONSTRAINT seasons_pkey PRIMARY KEY (id);


--
-- Name: team_rosters team_rosters_one_pick_per_round; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.team_rosters
    ADD CONSTRAINT team_rosters_one_pick_per_round UNIQUE (team_id, draft_round);


--
-- Name: team_rosters team_rosters_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.team_rosters
    ADD CONSTRAINT team_rosters_pkey PRIMARY KEY (id);


--
-- Name: team_rosters team_rosters_team_id_golfer_id_acquired_at_key; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.team_rosters
    ADD CONSTRAINT team_rosters_team_id_golfer_id_acquired_at_key UNIQUE (team_id, golfer_id, acquired_at);


--
-- Name: teams teams_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.teams
    ADD CONSTRAINT teams_pkey PRIMARY KEY (id);


--
-- Name: teams teams_season_id_team_name_key; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.teams
    ADD CONSTRAINT teams_season_id_team_name_key UNIQUE (season_id, team_name);


--
-- Name: tournament_player_overrides tournament_player_overrides_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournament_player_overrides
    ADD CONSTRAINT tournament_player_overrides_pkey PRIMARY KEY (tournament_id, espn_competitor_id);


--
-- Name: tournament_results tournament_results_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournament_results
    ADD CONSTRAINT tournament_results_pkey PRIMARY KEY (id);


--
-- Name: tournament_results tournament_results_tournament_id_golfer_id_key; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournament_results
    ADD CONSTRAINT tournament_results_tournament_id_golfer_id_key UNIQUE (tournament_id, golfer_id);


--
-- Name: tournaments tournaments_pga_tournament_id_key; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournaments
    ADD CONSTRAINT tournaments_pga_tournament_id_key UNIQUE (pga_tournament_id);


--
-- Name: tournaments tournaments_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournaments
    ADD CONSTRAINT tournaments_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON cwfgw4k.flyway_schema_history USING btree (success);


--
-- Name: idx_cache_expires; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_cache_expires ON cwfgw4k.cache USING btree (expires_at);


--
-- Name: idx_golfers_name; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_golfers_name ON cwfgw4k.golfers USING btree (last_name, first_name);


--
-- Name: idx_golfers_ranking; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_golfers_ranking ON cwfgw4k.golfers USING btree (world_ranking);


--
-- Name: idx_results_golfer; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_results_golfer ON cwfgw4k.tournament_results USING btree (golfer_id);


--
-- Name: idx_results_tournament; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_results_tournament ON cwfgw4k.tournament_results USING btree (tournament_id);


--
-- Name: idx_roster_golfer; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_roster_golfer ON cwfgw4k.team_rosters USING btree (golfer_id);


--
-- Name: idx_scores_season_tournament; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_scores_season_tournament ON cwfgw4k.fantasy_scores USING btree (season_id, tournament_id);


--
-- Name: idx_scores_team; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_scores_team ON cwfgw4k.fantasy_scores USING btree (team_id);


--
-- Name: idx_seasons_year; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_seasons_year ON cwfgw4k.seasons USING btree (season_year);


--
-- Name: idx_teams_season; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_teams_season ON cwfgw4k.teams USING btree (season_id);


--
-- Name: idx_tournament_player_overrides_golfer; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_tournament_player_overrides_golfer ON cwfgw4k.tournament_player_overrides USING btree (golfer_id);


--
-- Name: idx_tournaments_dates; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_tournaments_dates ON cwfgw4k.tournaments USING btree (start_date, end_date);


--
-- Name: idx_tournaments_season; Type: INDEX; Schema: cwfgw4k; Owner: -
--

CREATE INDEX idx_tournaments_season ON cwfgw4k.tournaments USING btree (season_id);


--
-- Name: tournament_player_overrides tournament_player_overrides_golfer_not_tombstoned; Type: TRIGGER; Schema: cwfgw4k; Owner: -
--

CREATE TRIGGER tournament_player_overrides_golfer_not_tombstoned BEFORE INSERT OR UPDATE OF golfer_id ON cwfgw4k.tournament_player_overrides FOR EACH ROW EXECUTE FUNCTION cwfgw4k.refuse_override_to_tombstoned_golfer();


--
-- Name: fantasy_scores fantasy_scores_golfer_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.fantasy_scores
    ADD CONSTRAINT fantasy_scores_golfer_id_fkey FOREIGN KEY (golfer_id) REFERENCES cwfgw4k.golfers(id);


--
-- Name: fantasy_scores fantasy_scores_season_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.fantasy_scores
    ADD CONSTRAINT fantasy_scores_season_id_fkey FOREIGN KEY (season_id) REFERENCES cwfgw4k.seasons(id) ON DELETE CASCADE;


--
-- Name: fantasy_scores fantasy_scores_team_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.fantasy_scores
    ADD CONSTRAINT fantasy_scores_team_id_fkey FOREIGN KEY (team_id) REFERENCES cwfgw4k.teams(id) ON DELETE CASCADE;


--
-- Name: fantasy_scores fantasy_scores_tournament_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.fantasy_scores
    ADD CONSTRAINT fantasy_scores_tournament_id_fkey FOREIGN KEY (tournament_id) REFERENCES cwfgw4k.tournaments(id) ON DELETE CASCADE;


--
-- Name: golfers golfers_merged_into_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.golfers
    ADD CONSTRAINT golfers_merged_into_fkey FOREIGN KEY (merged_into) REFERENCES cwfgw4k.golfers(id);


--
-- Name: season_rule_payouts season_rule_payouts_season_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.season_rule_payouts
    ADD CONSTRAINT season_rule_payouts_season_id_fkey FOREIGN KEY (season_id) REFERENCES cwfgw4k.seasons(id) ON DELETE CASCADE;


--
-- Name: season_rule_side_bet_rounds season_rule_side_bet_rounds_season_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.season_rule_side_bet_rounds
    ADD CONSTRAINT season_rule_side_bet_rounds_season_id_fkey FOREIGN KEY (season_id) REFERENCES cwfgw4k.seasons(id) ON DELETE CASCADE;


--
-- Name: seasons seasons_league_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.seasons
    ADD CONSTRAINT seasons_league_id_fkey FOREIGN KEY (league_id) REFERENCES cwfgw4k.leagues(id);


--
-- Name: team_rosters team_rosters_golfer_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.team_rosters
    ADD CONSTRAINT team_rosters_golfer_id_fkey FOREIGN KEY (golfer_id) REFERENCES cwfgw4k.golfers(id);


--
-- Name: team_rosters team_rosters_team_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.team_rosters
    ADD CONSTRAINT team_rosters_team_id_fkey FOREIGN KEY (team_id) REFERENCES cwfgw4k.teams(id) ON DELETE CASCADE;


--
-- Name: teams teams_season_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.teams
    ADD CONSTRAINT teams_season_id_fkey FOREIGN KEY (season_id) REFERENCES cwfgw4k.seasons(id) ON DELETE CASCADE;


--
-- Name: tournament_player_overrides tournament_player_overrides_golfer_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournament_player_overrides
    ADD CONSTRAINT tournament_player_overrides_golfer_id_fkey FOREIGN KEY (golfer_id) REFERENCES cwfgw4k.golfers(id);


--
-- Name: tournament_player_overrides tournament_player_overrides_tournament_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournament_player_overrides
    ADD CONSTRAINT tournament_player_overrides_tournament_id_fkey FOREIGN KEY (tournament_id) REFERENCES cwfgw4k.tournaments(id) ON DELETE CASCADE;


--
-- Name: tournament_results tournament_results_golfer_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournament_results
    ADD CONSTRAINT tournament_results_golfer_id_fkey FOREIGN KEY (golfer_id) REFERENCES cwfgw4k.golfers(id);


--
-- Name: tournament_results tournament_results_tournament_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournament_results
    ADD CONSTRAINT tournament_results_tournament_id_fkey FOREIGN KEY (tournament_id) REFERENCES cwfgw4k.tournaments(id) ON DELETE CASCADE;


--
-- Name: tournaments tournaments_season_id_fkey; Type: FK CONSTRAINT; Schema: cwfgw4k; Owner: -
--

ALTER TABLE ONLY cwfgw4k.tournaments
    ADD CONSTRAINT tournaments_season_id_fkey FOREIGN KEY (season_id) REFERENCES cwfgw4k.seasons(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict cwfgw4kschemadump

