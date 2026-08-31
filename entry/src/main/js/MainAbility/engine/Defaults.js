import { SAVE_FORMAT_VERSION } from './Types';
export function defaultSettings(mode) {
    if (mode === 'classic' || mode === 'single_set') {
        const settings = {
            mode,
            gamesPerSet: 6,
            setsToWin: mode === 'single_set' ? 1 : 2,
            tieBreakTarget: 7,
            gameScoring: 'star',
            setEnding: 'tie_break',
            trackServe: true,
            startingServer: 'A'
        };
        return settings;
    }
    if (mode === 'tie_break' || mode === 'super_tie_break') {
        const settings = {
            mode,
            target: mode === 'super_tie_break' ? 10 : 7,
            winByTwo: true,
            trackServe: false,
            startingServer: 'A'
        };
        return settings;
    }
    if (mode === 'race_to_n') {
        const settings = {
            mode,
            target: 21,
            winByTwo: false,
            trackServe: false,
            startingServer: 'A'
        };
        return settings;
    }
    const settings = {
        mode: 'americano',
        totalPoints: 24,
        serveEvery: 4,
        trackServe: true,
        startingServer: 'A'
    };
    return settings;
}
function integer(value, fallback, minimum, maximum) {
    return typeof value === 'number' && Number.isFinite(value)
        ? Math.max(minimum, Math.min(maximum, Math.round(value)))
        : fallback;
}
function team(value, fallback) {
    return value === 'A' || value === 'B' ? value : fallback;
}
export function normalizeSettings(mode, supplied) {
    const fallback = defaultSettings(mode);
    if (supplied === null || typeof supplied !== 'object') {
        return fallback;
    }
    const source = supplied;
    if (mode === 'classic' || mode === 'single_set') {
        const gameScoring = source.gameScoring === 'star' || source.gameScoring === 'silver' ||
            source.gameScoring === 'advantage' || source.gameScoring === 'golden'
            ? source.gameScoring
            : source.advantageMode === 'golden' || source.goldenPoint === true
                ? 'golden'
                : 'advantage';
        const setEnding = source.setEnding === 'tie_break' || source.setEnding === 'two_game_lead' ||
            source.setEnding === 'first_to'
            ? source.setEnding
            : source.winSetByTwo === false
                ? 'first_to'
                : source.tieBreakEnabled === false
                    ? 'two_game_lead'
                    : 'tie_break';
        return {
            mode,
            gamesPerSet: integer(source.gamesPerSet, fallback.gamesPerSet, 1, 12),
            setsToWin: mode === 'single_set'
                ? 1
                : integer(source.setsToWin, fallback.setsToWin, 1, 3),
            tieBreakTarget: integer(source.tieBreakTarget, fallback.tieBreakTarget, 1, 30),
            gameScoring,
            setEnding,
            trackServe: typeof source.trackServe === 'boolean' ? source.trackServe : fallback.trackServe,
            startingServer: team(source.startingServer, fallback.startingServer)
        };
    }
    if (mode === 'americano') {
        return {
            mode,
            totalPoints: integer(source.totalPoints, fallback.totalPoints, 1, 99),
            serveEvery: integer(source.serveEvery, fallback.serveEvery, 1, 16),
            trackServe: typeof source.trackServe === 'boolean' ? source.trackServe : fallback.trackServe,
            startingServer: team(source.startingServer, fallback.startingServer)
        };
    }
    return {
        mode,
        target: integer(source.target, fallback.target, 1, 99),
        winByTwo: typeof source.winByTwo === 'boolean' ? source.winByTwo : fallback.winByTwo,
        trackServe: false,
        startingServer: team(source.startingServer, fallback.startingServer)
    };
}
export function createInitialState(mode, supplied) {
    const settings = normalizeSettings(mode, supplied);
    return {
        formatVersion: SAVE_FORMAT_VERSION,
        mode,
        settings,
        pointsA: 0,
        pointsB: 0,
        gamesA: 0,
        gamesB: 0,
        setsA: 0,
        setsB: 0,
        tieBreakPointsA: 0,
        tieBreakPointsB: 0,
        inTieBreak: false,
        tieBreakStartingServer: null,
        completed: false,
        winner: null,
        currentServer: settings.startingServer,
        pointsSinceServerChange: 0,
        roundNumber: 1,
        roundHistory: [],
        sessionPointsA: 0,
        sessionPointsB: 0,
        revision: 0
    };
}
