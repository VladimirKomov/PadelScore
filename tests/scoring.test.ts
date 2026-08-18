import assert from 'node:assert/strict';
import test from 'node:test';
import { MatchEngine } from '../shared/engine/MatchEngine.ts';
import type {
  AmericanoSettings,
  ClassicSettings,
  MatchMode,
  ModeSettings,
  RaceSettings,
  TieBreakSettings
} from '../shared/engine/Types.ts';
import { defaultSettings } from '../shared/engine/Defaults.ts';
import {
  joinChunks,
  splitIntoChunks
} from '../shared/persistence/ChunkCodec.ts';

class TestClock {
  private value = 1000;

  point(engine: MatchEngine, team: 'A' | 'B'): boolean {
    this.value += 1000;
    return engine.point(team, this.value).accepted;
  }
}

function add(engine: MatchEngine, team: 'A' | 'B', count: number, clock: TestClock): void {
  for (let index = 0; index < count; index += 1) {
    assert.equal(clock.point(engine, team), true);
  }
}

function classicSettings(overrides: Partial<ClassicSettings> = {}): ClassicSettings {
  return {
    ...(defaultSettings('classic') as ClassicSettings),
    ...overrides,
    mode: overrides.mode ?? 'classic'
  };
}

function tieSettings(overrides: Partial<TieBreakSettings> = {}): TieBreakSettings {
  return {
    ...(defaultSettings('tie_break') as TieBreakSettings),
    ...overrides,
    mode: overrides.mode ?? 'tie_break'
  };
}

function raceSettings(overrides: Partial<RaceSettings> = {}): RaceSettings {
  return {
    ...(defaultSettings('race_to_n') as RaceSettings),
    ...overrides,
    mode: 'race_to_n'
  };
}

function americanoSettings(overrides: Partial<AmericanoSettings> = {}): AmericanoSettings {
  return {
    ...(defaultSettings('americano') as AmericanoSettings),
    ...overrides,
    mode: 'americano'
  };
}

test('Classic: ordinary game without deuce', () => {
  const engine = new MatchEngine('classic', classicSettings());
  const clock = new TestClock();
  add(engine, 'A', 4, clock);
  assert.equal(engine.state.gamesA, 1);
  assert.equal(engine.presentation.scoreA, '0');
});

test('Classic: reaches 40:40 and displays deuce score', () => {
  const engine = new MatchEngine('classic', classicSettings());
  const clock = new TestClock();
  add(engine, 'A', 3, clock);
  add(engine, 'B', 3, clock);
  assert.equal(engine.presentation.scoreA, '40');
  assert.equal(engine.presentation.scoreB, '40');
});

test('Classic: advantage, return to deuce and advantage victory', () => {
  const engine = new MatchEngine('classic', classicSettings());
  const clock = new TestClock();
  add(engine, 'A', 3, clock);
  add(engine, 'B', 3, clock);
  add(engine, 'A', 1, clock);
  assert.equal(engine.presentation.scoreA, 'AD');
  add(engine, 'B', 1, clock);
  assert.equal(engine.presentation.scoreA, '40');
  assert.equal(engine.presentation.scoreB, '40');
  add(engine, 'B', 2, clock);
  assert.equal(engine.state.gamesB, 1);
});

test('Classic: Golden Point ends the game immediately after deuce', () => {
  const settings = classicSettings({ advantageMode: 'golden' });
  const engine = new MatchEngine('classic', settings);
  const clock = new TestClock();
  add(engine, 'A', 3, clock);
  add(engine, 'B', 3, clock);
  add(engine, 'A', 1, clock);
  assert.equal(engine.state.gamesA, 1);
});

test('Classic: set and match victory are detected', () => {
  const settings = classicSettings({
    gamesPerSet: 2,
    setsToWin: 1,
    tieBreakEnabled: false
  });
  const engine = new MatchEngine('classic', settings);
  const clock = new TestClock();
  for (let game = 0; game < 2; game += 1) {
    add(engine, 'A', 4, clock);
  }
  assert.equal(engine.state.setsA, 1);
  assert.equal(engine.state.completed, true);
  assert.equal(engine.state.winner, 'A');
});

test('Classic: configured tie-break begins and decides the match', () => {
  const settings = classicSettings({
    gamesPerSet: 2,
    setsToWin: 1,
    tieBreakAt: 2,
    tieBreakTarget: 3
  });
  const engine = new MatchEngine('classic', settings);
  const clock = new TestClock();
  for (let game = 0; game < 2; game += 1) {
    add(engine, 'A', 4, clock);
    add(engine, 'B', 4, clock);
  }
  assert.equal(engine.state.inTieBreak, true);
  add(engine, 'A', 2, clock);
  add(engine, 'B', 1, clock);
  add(engine, 'A', 1, clock);
  assert.equal(engine.state.completed, true);
  assert.equal(engine.state.winner, 'A');
});

test('Classic: points are blocked after completion', () => {
  const settings = classicSettings({
    gamesPerSet: 1,
    setsToWin: 1,
    winSetByTwo: false,
    tieBreakEnabled: false
  });
  const engine = new MatchEngine('classic', settings);
  const clock = new TestClock();
  add(engine, 'A', 4, clock);
  const before = engine.state.revision;
  assert.equal(clock.point(engine, 'B'), false);
  assert.equal(engine.state.revision, before);
});

test('Single Set: is an independent one-set mode', () => {
  const settings = classicSettings({
    mode: 'single_set',
    gamesPerSet: 1,
    setsToWin: 1,
    winSetByTwo: false,
    tieBreakEnabled: false
  });
  const engine = new MatchEngine('single_set', settings);
  const clock = new TestClock();
  add(engine, 'B', 4, clock);
  assert.equal(engine.state.winner, 'B');
  assert.equal(engine.presentation.modeLabel, 'SINGLE SET');
});

test('Tie-break: target 7 wins', () => {
  const engine = new MatchEngine('tie_break', tieSettings({ target: 7, winByTwo: false }));
  const clock = new TestClock();
  add(engine, 'A', 7, clock);
  assert.equal(engine.state.winner, 'A');
});

test('Super tie-break: target 10 wins', () => {
  const settings = tieSettings({ mode: 'super_tie_break', target: 10, winByTwo: false });
  const engine = new MatchEngine('super_tie_break', settings);
  const clock = new TestClock();
  add(engine, 'B', 10, clock);
  assert.equal(engine.state.winner, 'B');
  assert.equal(engine.presentation.modeLabel, 'SUPER TIE-BREAK');
});

test('Tie-break: win-by-two continues at insufficient margin', () => {
  const engine = new MatchEngine('tie_break', tieSettings({ target: 7, winByTwo: true }));
  const clock = new TestClock();
  add(engine, 'A', 6, clock);
  add(engine, 'B', 6, clock);
  add(engine, 'A', 1, clock);
  assert.equal(engine.state.completed, false);
  add(engine, 'A', 1, clock);
  assert.equal(engine.state.winner, 'A');
});

test('Race to N: ordinary and custom targets', () => {
  const engine = new MatchEngine('race_to_n', raceSettings({ target: 13 }));
  const clock = new TestClock();
  add(engine, 'A', 13, clock);
  assert.equal(engine.state.winner, 'A');
  assert.equal(engine.presentation.remainingPoints, 0);
});

test('Race to N: win-by-two extends the game', () => {
  const engine = new MatchEngine('race_to_n', raceSettings({ target: 3, winByTwo: true }));
  const clock = new TestClock();
  add(engine, 'A', 2, clock);
  add(engine, 'B', 2, clock);
  add(engine, 'B', 1, clock);
  assert.equal(engine.state.completed, false);
  add(engine, 'B', 1, clock);
  assert.equal(engine.state.winner, 'B');
});

test('Race to N: undo winning point reactivates match', () => {
  const engine = new MatchEngine('race_to_n', raceSettings({ target: 2 }));
  const clock = new TestClock();
  add(engine, 'A', 2, clock);
  assert.equal(engine.state.completed, true);
  assert.equal(engine.dispatch({ type: 'Undo' }).accepted, true);
  assert.equal(engine.state.completed, false);
  assert.equal(engine.state.pointsA, 1);
});

for (const total of [16, 20, 21, 24, 32, 27]) {
  test(`Americano: exact fixed total ${total}`, () => {
    const engine = new MatchEngine('americano', americanoSettings({ totalPoints: total }));
    const clock = new TestClock();
    const pointsA = Math.ceil(total / 2);
    const pointsB = total - pointsA;
    add(engine, 'A', pointsA, clock);
    add(engine, 'B', pointsB, clock);
    assert.equal(engine.state.pointsA + engine.state.pointsB, total);
    assert.equal(engine.state.completed, true);
    assert.equal(engine.presentation.playedPoints, total);
    assert.equal(engine.presentation.remainingPoints, 0);
    assert.equal(engine.presentation.progressPercent, 100);
  });
}

test('Americano: remaining and played points update before completion', () => {
  const engine = new MatchEngine('americano', americanoSettings({ totalPoints: 24 }));
  const clock = new TestClock();
  add(engine, 'A', 7, clock);
  add(engine, 'B', 3, clock);
  assert.equal(engine.presentation.playedPoints, 10);
  assert.equal(engine.presentation.remainingPoints, 14);
});

test('Americano: no point can exceed the fixed total', () => {
  const engine = new MatchEngine('americano', americanoSettings({ totalPoints: 3 }));
  const clock = new TestClock();
  add(engine, 'A', 2, clock);
  add(engine, 'B', 1, clock);
  assert.equal(clock.point(engine, 'A'), false);
  assert.equal(engine.state.pointsA + engine.state.pointsB, 3);
});

test('Americano: even total allows a draw', () => {
  const engine = new MatchEngine('americano', americanoSettings({ totalPoints: 4 }));
  const clock = new TestClock();
  add(engine, 'A', 2, clock);
  add(engine, 'B', 2, clock);
  assert.equal(engine.state.winner, 'draw');
});

test('Americano: odd total always has a winner', () => {
  const engine = new MatchEngine('americano', americanoSettings({ totalPoints: 5 }));
  const clock = new TestClock();
  add(engine, 'A', 3, clock);
  add(engine, 'B', 2, clock);
  assert.equal(engine.state.winner, 'A');
});

test('Americano: undo completion restores score, remaining points and server', () => {
  const settings = americanoSettings({
    totalPoints: 4,
    serveEvery: 2,
    startingServer: 'A',
    trackServe: true
  });
  const engine = new MatchEngine('americano', settings);
  const clock = new TestClock();
  add(engine, 'A', 3, clock);
  const serverBeforeFinal = engine.state.currentServer;
  add(engine, 'B', 1, clock);
  assert.equal(engine.state.completed, true);
  engine.undo();
  assert.equal(engine.state.completed, false);
  assert.equal(engine.presentation.remainingPoints, 1);
  assert.equal(engine.state.currentServer, serverBeforeFinal);
  assert.equal(engine.state.roundHistory.length, 0);
});

test('Americano: next round keeps history and session totals', () => {
  const engine = new MatchEngine('americano', americanoSettings({ totalPoints: 4 }));
  const clock = new TestClock();
  add(engine, 'A', 3, clock);
  add(engine, 'B', 1, clock);
  assert.equal(engine.dispatch({ type: 'StartNextRound' }).accepted, true);
  assert.equal(engine.state.roundNumber, 2);
  assert.equal(engine.state.roundHistory.length, 1);
  assert.equal(engine.state.sessionPointsA, 3);
  assert.equal(engine.state.sessionPointsB, 1);
  assert.equal(engine.state.pointsA, 0);
});

test('Americano: multiple rounds accumulate session totals', () => {
  const engine = new MatchEngine('americano', americanoSettings({ totalPoints: 2 }));
  const clock = new TestClock();
  add(engine, 'A', 2, clock);
  engine.startNextRound();
  add(engine, 'B', 2, clock);
  assert.equal(engine.state.roundHistory.length, 2);
  assert.equal(engine.state.sessionPointsA, 2);
  assert.equal(engine.state.sessionPointsB, 2);
});

test('Americano: automatic and manual serving changes', () => {
  const engine = new MatchEngine(
    'americano',
    americanoSettings({ totalPoints: 8, serveEvery: 2, startingServer: 'A' })
  );
  const clock = new TestClock();
  add(engine, 'A', 1, clock);
  assert.equal(engine.state.currentServer, 'A');
  add(engine, 'B', 1, clock);
  assert.equal(engine.state.currentServer, 'B');
  engine.dispatch({ type: 'ChangeServer' });
  assert.equal(engine.state.currentServer, 'A');
});

test('General: multiple Undo operations restore consecutive states', () => {
  const engine = new MatchEngine('race_to_n', raceSettings({ target: 10 }));
  const clock = new TestClock();
  add(engine, 'A', 2, clock);
  add(engine, 'B', 1, clock);
  engine.undo();
  engine.undo();
  assert.equal(engine.state.pointsA, 1);
  assert.equal(engine.state.pointsB, 0);
});

test('General: Reset clears current score', () => {
  const engine = new MatchEngine('race_to_n', raceSettings({ target: 10 }));
  const clock = new TestClock();
  add(engine, 'A', 3, clock);
  engine.dispatch({ type: 'Reset' });
  assert.equal(engine.state.pointsA, 0);
  assert.equal(engine.historyDepth, 0);
});

test('Americano: ClearSession removes history and accumulated points', () => {
  const engine = new MatchEngine('americano', americanoSettings({ totalPoints: 2 }));
  const clock = new TestClock();
  add(engine, 'A', 2, clock);
  assert.equal(engine.state.roundHistory.length, 1);
  engine.dispatch({ type: 'StartNextRound' });
  engine.dispatch({ type: 'ClearSession' });
  assert.equal(engine.state.roundHistory.length, 0);
  assert.equal(engine.state.sessionPointsA, 0);
  assert.equal(engine.state.sessionPointsB, 0);
  assert.equal(engine.state.roundNumber, 1);
});

for (const mode of [
  'classic',
  'single_set',
  'tie_break',
  'super_tie_break',
  'race_to_n',
  'americano'
] as MatchMode[]) {
  test(`Persistence: restores ${mode}`, () => {
    const settings = defaultSettings(mode) as ModeSettings;
    const engine = new MatchEngine(mode, settings);
    const clock = new TestClock();
    add(engine, 'A', 1, clock);
    const restored = MatchEngine.restore(engine.serialize(), 'americano');
    assert.equal(restored.restored, true);
    assert.equal(restored.engine.state.mode, mode);
    assert.equal(restored.engine.state.pointsA, 1);
    assert.equal(restored.engine.historyDepth, 1);
  });
}

test('Persistence: corrupt and incompatible saves fall back safely', () => {
  const corrupt = MatchEngine.restore('{broken', 'race_to_n');
  assert.equal(corrupt.restored, false);
  assert.equal(corrupt.engine.state.mode, 'race_to_n');
  const incompatible = MatchEngine.restore(
    JSON.stringify({ formatVersion: 999, state: {}, history: [] }),
    'americano'
  );
  assert.equal(incompatible.restored, false);
  assert.match(incompatible.reason, /unsupported/);
});

test('Input safety: rapid duplicate point is debounced', () => {
  const engine = new MatchEngine('race_to_n', raceSettings({ target: 10 }), 350);
  assert.equal(engine.point('A', 1000).accepted, true);
  assert.equal(engine.point('A', 1100).accepted, false);
  assert.equal(engine.point('A', 1400).accepted, true);
  assert.equal(engine.state.pointsA, 2);
});

test('Persistence: long match payload is losslessly chunked for Preferences', () => {
  const payload = 'padel-score-state:'.repeat(1500);
  const chunks = splitIntoChunks(payload, 1024);
  assert.ok(chunks.length > 1);
  assert.ok(chunks.every((chunk) => chunk.length <= 1024));
  assert.equal(joinChunks(chunks), payload);
});
