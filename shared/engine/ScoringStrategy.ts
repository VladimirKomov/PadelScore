import type { MatchPresentation, MatchState, Team } from './Types';

export interface ScoringStrategy {
  addPoint(state: MatchState, team: Team): void;
  presentation(state: MatchState, canUndo: boolean): MatchPresentation;
}
