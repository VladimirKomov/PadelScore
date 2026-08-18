package com.vekom.padelprobe;

import static com.vekom.padelprobe.MatchModel.Mode;
import static com.vekom.padelprobe.MatchModel.Settings;
import static com.vekom.padelprobe.MatchModel.State;
import static com.vekom.padelprobe.MatchModel.Team;

interface ScoringStrategy {
    void addPoint(State state, Team team);

    String scoreLabel(State state, Team team);

    String modeLabel(State state);

    final class Factory {
        static ScoringStrategy forMode(Mode mode) {
            if (mode == Mode.CLASSIC || mode == Mode.SINGLE_SET) {
                return new Classic();
            }
            if (mode == Mode.AMERICANO) {
                return new Americano();
            }
            return new Numeric();
        }

        private Factory() {
        }
    }

    final class Classic implements ScoringStrategy {
        @Override
        public void addPoint(State state, Team team) {
            Settings settings = state.settings;
            if (state.inTieBreak) {
                if (team == Team.A) {
                    state.tieBreakPointsA++;
                } else {
                    state.tieBreakPointsB++;
                }
                Team winner = numericWinner(
                        state.tieBreakPointsA,
                        state.tieBreakPointsB,
                        settings.tieBreakTarget,
                        true);
                if (winner != null) {
                    awardSet(state, winner);
                }
                return;
            }

            int beforeA = state.pointsA;
            int beforeB = state.pointsB;
            if (team == Team.A) {
                state.pointsA++;
            } else {
                state.pointsB++;
            }
            boolean goldenDeuce = settings.goldenPoint && beforeA >= 3 && beforeB >= 3;
            int own = team == Team.A ? state.pointsA : state.pointsB;
            int opponent = team == Team.A ? state.pointsB : state.pointsA;
            if (goldenDeuce || (own >= 4 && own - opponent >= 2)) {
                awardGame(state, team);
            }
        }

        private void awardGame(State state, Team team) {
            Settings settings = state.settings;
            state.pointsA = 0;
            state.pointsB = 0;
            if (team == Team.A) {
                state.gamesA++;
            } else {
                state.gamesB++;
            }
            if (settings.tieBreakEnabled
                    && state.gamesA == settings.tieBreakAt
                    && state.gamesB == settings.tieBreakAt) {
                state.inTieBreak = true;
                state.tieBreakPointsA = 0;
                state.tieBreakPointsB = 0;
                return;
            }
            int own = team == Team.A ? state.gamesA : state.gamesB;
            int opponent = team == Team.A ? state.gamesB : state.gamesA;
            boolean enough = own >= settings.gamesPerSet;
            boolean margin = !settings.winSetByTwo || own - opponent >= 2;
            if (enough && margin) {
                awardSet(state, team);
            }
        }

        private void awardSet(State state, Team team) {
            if (team == Team.A) {
                state.setsA++;
            } else {
                state.setsB++;
            }
            state.pointsA = 0;
            state.pointsB = 0;
            state.gamesA = 0;
            state.gamesB = 0;
            state.tieBreakPointsA = 0;
            state.tieBreakPointsB = 0;
            state.inTieBreak = false;
            int own = team == Team.A ? state.setsA : state.setsB;
            if (own >= state.settings.setsToWin) {
                state.completed = true;
                state.winner = team.name();
            }
        }

        @Override
        public String scoreLabel(State state, Team team) {
            if (state.inTieBreak) {
                return Integer.toString(team == Team.A
                        ? state.tieBreakPointsA : state.tieBreakPointsB);
            }
            int own = team == Team.A ? state.pointsA : state.pointsB;
            int opponent = team == Team.A ? state.pointsB : state.pointsA;
            String[] normal = {"0", "15", "30", "40"};
            if (own <= 3 && opponent <= 3) {
                return normal[own];
            }
            if (own == opponent) {
                return "40";
            }
            return own > opponent ? "AD" : "40";
        }

        @Override
        public String modeLabel(State state) {
            return state.mode == Mode.SINGLE_SET ? "SINGLE SET" : "CLASSIC";
        }
    }

    final class Numeric implements ScoringStrategy {
        @Override
        public void addPoint(State state, Team team) {
            if (team == Team.A) {
                state.pointsA++;
            } else {
                state.pointsB++;
            }
            Team winner = numericWinner(
                    state.pointsA,
                    state.pointsB,
                    state.settings.target,
                    state.settings.winByTwo);
            if (winner != null) {
                state.completed = true;
                state.winner = winner.name();
            }
        }

        @Override
        public String scoreLabel(State state, Team team) {
            return Integer.toString(team == Team.A ? state.pointsA : state.pointsB);
        }

        @Override
        public String modeLabel(State state) {
            if (state.mode == Mode.TIE_BREAK) {
                return "TIE-BREAK " + state.settings.target;
            }
            if (state.mode == Mode.SUPER_TIE_BREAK) {
                return "SUPER TB " + state.settings.target;
            }
            return "RACE TO " + state.settings.target;
        }
    }

    final class Americano implements ScoringStrategy {
        @Override
        public void addPoint(State state, Team team) {
            if (team == Team.A) {
                state.pointsA++;
            } else {
                state.pointsB++;
            }
            if (state.settings.trackServe) {
                state.pointsSinceServerChange++;
                if (state.pointsSinceServerChange >= state.settings.serveEvery) {
                    state.currentServer = state.currentServer.other();
                    state.pointsSinceServerChange = 0;
                }
            }
            if (state.pointsA + state.pointsB == state.settings.target) {
                state.completed = true;
                state.winner = state.pointsA == state.pointsB
                        ? "DRAW" : state.pointsA > state.pointsB ? "A" : "B";
                state.sessionPointsA += state.pointsA;
                state.sessionPointsB += state.pointsB;
                MatchModel.RoundResult result = new MatchModel.RoundResult();
                result.roundNumber = state.roundNumber;
                result.teamA = state.pointsA;
                result.teamB = state.pointsB;
                result.totalPoints = state.settings.target;
                result.winner = state.winner;
                state.roundHistory.add(result);
            }
        }

        @Override
        public String scoreLabel(State state, Team team) {
            return Integer.toString(team == Team.A ? state.pointsA : state.pointsB);
        }

        @Override
        public String modeLabel(State state) {
            return "AMERICANO " + state.settings.target;
        }
    }

    static Team numericWinner(int a, int b, int target, boolean winByTwo) {
        if (Math.max(a, b) < target) {
            return null;
        }
        if (winByTwo && Math.abs(a - b) < 2) {
            return null;
        }
        return a > b ? Team.A : Team.B;
    }
}
