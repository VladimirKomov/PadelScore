export const SAVE_FORMAT_VERSION = 1;
export const DEFAULT_DEBOUNCE_MS = 350;
export function otherTeam(team) {
    return team === 'A' ? 'B' : 'A';
}
export function cloneState(state) {
    return JSON.parse(JSON.stringify(state));
}
