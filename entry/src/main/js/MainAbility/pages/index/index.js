import featureAbility from '@ohos.ability.featureAbility';
import prompt from '@ohos.prompt';
import vibrator from '@ohos.vibrator';
import { defaultSettings } from '../../engine/Defaults';
import { MatchEngine } from '../../engine/MatchEngine';
import { MatchRepository } from '../../persistence/MatchRepository';

let engine = new MatchEngine('americano');
let repository = null;

function clamp(value, minimum, maximum) {
  return Math.min(maximum, Math.max(minimum, value));
}

function checkedValue(event, previous) {
  if (event && typeof event.checked === 'boolean') {
    return event.checked;
  }
  if (event && event.detail && typeof event.detail.checked === 'boolean') {
    return event.detail.checked;
  }
  return !previous;
}

export default {
  data: {
    showHome: true,
    showSettings: false,
    showMatch: false,
    showMenuView: false,
    showHistory: false,
    historyReturnView: 'match',
    selectedMode: 'americano',
    settingsTitle: 'AMERICANO',
    showAmericanoSettings: true,
    showTargetSettings: true,
    showClassicSettings: false,
    showSetsSetting: false,
    showTieAtSetting: false,
    showWinByTwoSetting: false,
    showServeSetting: true,
    target: 24,
    gamesPerSet: 6,
    setsToWin: 2,
    tieBreakAt: 6,
    winByTwo: true,
    goldenPoint: false,
    tieBreakEnabled: true,
    serveEvery: 4,
    trackServe: true,
    startingServerA: true,
    modeTitle: 'AMERICANO 24',
    scoreAValue: '0',
    scoreBValue: '0',
    gamesA: 0,
    gamesB: 0,
    setsA: 0,
    setsB: 0,
    showGames: false,
    showRemaining: true,
    playedPoints: 0,
    remainingPoints: 24,
    progressPercent: 0,
    showServer: true,
    currentServer: 'A',
    matchInfoText: 'AM24 · R1 · 24 LEFT',
    matchInfoClass: '',
    statusText: 'IN PLAY',
    statusClass: '',
    completedClass: '',
    undoDisabled: true,
    showNextRound: false,
    showRepeat: false,
    roundNumber: 1,
    hasHistory: false,
    noRounds: true,
    rounds: [],
    sessionPointsA: 0,
    sessionPointsB: 0
  },

  async onInit() {
    try {
      const context = featureAbility.getContext();
      const filesDir = await context.getFilesDir();
      repository = new MatchRepository(filesDir + '/padelscore_state');
      engine = await repository.loadEngine('americano');
      const state = engine.state;
      this.renderEngine();
      if (state.revision > 0 || state.completed || state.roundHistory.length > 0) {
        this.setView('match');
      }
    } catch (error) {
      engine = new MatchEngine('americano');
      this.renderEngine();
    }
  },

  onShow() {
    this.updateKeepScreen();
  },

  onHide() {
    this.persist();
    this.releaseScreen();
  },

  setView(name) {
    this.showHome = name === 'home';
    this.showSettings = name === 'settings';
    this.showMatch = name === 'match';
    this.showMenuView = name === 'menu';
    this.showHistory = name === 'history';
    this.updateKeepScreen();
  },

  async updateKeepScreen() {
    try {
      const active = this.showMatch;
      const mainWindow = await featureAbility.getWindow();
      await mainWindow.setKeepScreenOn(active);
    } catch (error) {
    }
  },

  async releaseScreen() {
    try {
      const mainWindow = await featureAbility.getWindow();
      await mainWindow.setKeepScreenOn(false);
    } catch (error) {
    }
  },

  async haptic(duration) {
    try {
      await vibrator.vibrate(duration);
    } catch (error) {
    }
  },

  async persist() {
    if (repository !== null) {
      await repository.saveEngine(engine);
    }
  },

  modeLabel(mode, settings) {
    if (mode === 'classic') return this.$t('strings.classic');
    if (mode === 'single_set') return this.$t('strings.singleSet');
    if (mode === 'tie_break') return this.$t('strings.tieBreak');
    if (mode === 'super_tie_break') return this.$t('strings.superTieBreak');
    if (mode === 'race_to_n') return this.$t('strings.raceToN') + ' ' + String(settings.target);
    return this.$t('strings.americano') + ' ' + String(settings.totalPoints);
  },

  compactModeLabel(mode, settings) {
    if (mode === 'classic') return 'CLASS';
    if (mode === 'single_set') return '1SET';
    if (mode === 'tie_break') return 'TB' + String(settings.target);
    if (mode === 'super_tie_break') return 'STB' + String(settings.target);
    if (mode === 'race_to_n') return 'RACE' + String(settings.target);
    return 'AM' + String(settings.totalPoints);
  },

  compactMatchInfo(state, presentation) {
    const mode = this.compactModeLabel(state.mode, state.settings);
    if (presentation.completed) {
      const result = presentation.winner === 'draw'
        ? 'DRAW'
        : String(presentation.winner) + ' WINS';
      return mode + ' · ' + result;
    }
    if ((state.mode === 'classic' || state.mode === 'single_set')
        && presentation.tieBreak) {
      return mode + ' · TB · S' + presentation.setsA + ':' + presentation.setsB
        + ' · G' + presentation.gamesA + ':' + presentation.gamesB;
    }
    if (state.mode === 'classic') {
      return mode + ' · S' + presentation.setsA + ':' + presentation.setsB
        + ' · G' + presentation.gamesA + ':' + presentation.gamesB;
    }
    if (state.mode === 'single_set') {
      return mode + ' · G' + presentation.gamesA + ':' + presentation.gamesB;
    }
    if (state.mode === 'americano') {
      return mode + ' · R' + state.roundNumber + ' · '
        + presentation.remainingPoints + ' LEFT';
    }
    return mode + ' · ' + presentation.remainingPoints + ' LEFT';
  },

  renderEngine() {
    const state = engine.state;
    const presentation = engine.presentation;
    this.modeTitle = this.modeLabel(state.mode, state.settings);
    this.scoreAValue = presentation.scoreA;
    this.scoreBValue = presentation.scoreB;
    this.gamesA = presentation.gamesA;
    this.gamesB = presentation.gamesB;
    this.setsA = presentation.setsA;
    this.setsB = presentation.setsB;
    this.showGames = state.mode === 'classic' || state.mode === 'single_set';
    this.showRemaining = presentation.remainingPoints !== null;
    this.playedPoints = presentation.playedPoints === null ? 0 : presentation.playedPoints;
    this.remainingPoints = presentation.remainingPoints === null ? 0 : presentation.remainingPoints;
    this.progressPercent = presentation.progressPercent === null ? 0 : presentation.progressPercent;
    this.showServer = state.settings.trackServe;
    this.currentServer = presentation.currentServer;
    this.completedClass = presentation.completed ? 'complete-card' : '';
    this.statusClass = presentation.completed ? 'finished-status' : '';
    this.matchInfoText = this.compactMatchInfo(state, presentation);
    this.matchInfoClass = presentation.completed ? 'match-info-finished' : '';
    if (presentation.completed) {
      this.statusText = presentation.winner === 'draw'
        ? this.$t('strings.roundDraw')
        : this.$t('strings.team' + presentation.winner) + this.$t('strings.teamWins');
    } else if (presentation.tieBreak) {
      this.statusText = this.$t('strings.tieBreak');
    } else {
      this.statusText = this.$t('strings.inPlay');
    }
    this.undoDisabled = !presentation.canUndo;
    this.showNextRound = presentation.completed && state.mode === 'americano';
    this.showRepeat = presentation.completed && state.mode !== 'americano';
    this.roundNumber = state.roundNumber;
    this.hasHistory = state.roundHistory.length > 0;
    this.noRounds = state.roundHistory.length === 0;
    this.sessionPointsA = state.sessionPointsA;
    this.sessionPointsB = state.sessionPointsB;
    this.rounds = state.roundHistory.map((round) => {
      let result = this.$t('strings.roundDraw');
      if (round.winner === 'A' || round.winner === 'B') {
        result = this.$t('strings.team' + round.winner) + this.$t('strings.teamWins');
      }
      return {
        roundNumber: round.roundNumber,
        teamA: round.teamA,
        teamB: round.teamB,
        result
      };
    });
  },

  updateSettingsVisibility() {
    const classic = this.selectedMode === 'classic' || this.selectedMode === 'single_set';
    const americano = this.selectedMode === 'americano';
    this.showClassicSettings = classic;
    this.showSetsSetting = this.selectedMode === 'classic';
    this.showTieAtSetting = classic && this.tieBreakEnabled;
    this.showTargetSettings = !classic;
    this.showWinByTwoSetting = this.selectedMode === 'tie_break' ||
      this.selectedMode === 'super_tie_break' || this.selectedMode === 'race_to_n';
    this.showAmericanoSettings = americano;
    this.showServeSetting = americano && this.trackServe;
  },

  applySettings(settings) {
    this.startingServerA = settings.startingServer === 'A';
    this.trackServe = settings.trackServe;
    if (settings.mode === 'classic' || settings.mode === 'single_set') {
      this.gamesPerSet = settings.gamesPerSet;
      this.setsToWin = settings.setsToWin;
      this.tieBreakAt = settings.tieBreakAt;
      this.tieBreakEnabled = settings.tieBreakEnabled;
      this.goldenPoint = settings.advantageMode === 'golden';
    } else if (settings.mode === 'americano') {
      this.target = settings.totalPoints;
      this.serveEvery = settings.serveEvery;
    } else {
      this.target = settings.target;
      this.winByTwo = settings.winByTwo;
    }
    this.settingsTitle = this.modeLabel(settings.mode, settings);
    this.updateSettingsVisibility();
  },

  async chooseMode(mode) {
    this.selectedMode = mode;
    let settings = defaultSettings(mode);
    if (repository !== null) {
      const saved = await repository.loadLastSettings(mode);
      if (saved !== null) settings = saved;
    }
    this.applySettings(settings);
    this.setView('settings');
  },

  chooseClassic() { this.chooseMode('classic'); },
  chooseSingleSet() { this.chooseMode('single_set'); },
  chooseTieBreak() { this.chooseMode('tie_break'); },
  chooseSuperTieBreak() { this.chooseMode('super_tie_break'); },
  chooseRace() { this.chooseMode('race_to_n'); },
  chooseAmericano() { this.chooseMode('americano'); },

  makeSettings() {
    const startingServer = this.startingServerA ? 'A' : 'B';
    if (this.selectedMode === 'classic' || this.selectedMode === 'single_set') {
      return {
        mode: this.selectedMode,
        gamesPerSet: this.gamesPerSet,
        setsToWin: this.selectedMode === 'single_set' ? 1 : this.setsToWin,
        winSetByTwo: true,
        tieBreakEnabled: this.tieBreakEnabled,
        tieBreakAt: this.tieBreakAt,
        tieBreakTarget: 7,
        advantageMode: this.goldenPoint ? 'golden' : 'advantage',
        trackServe: false,
        startingServer
      };
    }
    if (this.selectedMode === 'tie_break' || this.selectedMode === 'super_tie_break') {
      return {
        mode: this.selectedMode,
        target: this.target,
        winByTwo: this.winByTwo,
        trackServe: false,
        startingServer
      };
    }
    if (this.selectedMode === 'race_to_n') {
      return {
        mode: 'race_to_n',
        target: this.target,
        winByTwo: this.winByTwo,
        trackServe: false,
        startingServer
      };
    }
    return {
      mode: 'americano',
      totalPoints: this.target,
      serveEvery: this.serveEvery,
      trackServe: this.trackServe,
      startingServer
    };
  },

  async startSelectedMode() {
    const settings = this.makeSettings();
    engine = new MatchEngine(this.selectedMode, settings);
    if (repository !== null) {
      await repository.saveLastSettings(this.selectedMode, settings);
      await repository.saveEngine(engine);
    }
    this.renderEngine();
    this.setView('match');
  },

  async repeatLast() {
    const state = engine.state;
    engine = new MatchEngine(state.mode, state.settings);
    this.renderEngine();
    this.setView('match');
    await this.persist();
  },

  async addPoint(team) {
    const result = engine.dispatch({ type: 'PointWon', team });
    if (!result.accepted) return;
    this.renderEngine();
    await this.persist();
    await this.haptic(result.completedNow ? 120 : 35);
    await this.updateKeepScreen();
  },

  scoreA() { this.addPoint('A'); },
  scoreB() { this.addPoint('B'); },

  async undo() {
    const result = engine.dispatch({ type: 'Undo' });
    if (!result.accepted) return;
    this.renderEngine();
    await this.persist();
    await this.haptic(18);
    await this.updateKeepScreen();
  },

  async changeServer() {
    engine.dispatch({ type: 'ChangeServer' });
    this.renderEngine();
    await this.persist();
    await this.haptic(25);
  },

  async nextRound() {
    const result = engine.dispatch({ type: 'StartNextRound' });
    if (!result.accepted) return;
    this.renderEngine();
    await this.persist();
    await this.updateKeepScreen();
  },

  async requestHome() {
    const state = engine.state;
    if (!state.completed && state.revision > 0) {
      const response = await prompt.showDialog({
        title: this.$t('strings.exitTitle'),
        message: this.$t('strings.exitQuestion'),
        buttons: [
          { text: this.$t('strings.stay'), color: '#41D9FF' },
          { text: this.$t('strings.exit'), color: '#FF7A86' }
        ]
      });
      if (response.index !== 1) return;
    }
    this.setView('home');
  },

  settingsBack() { this.setView('home'); },
  historyBack() { this.setView(this.historyReturnView); },

  openHistory() {
    this.renderEngine();
    this.historyReturnView = this.showMenuView ? 'menu' : 'match';
    this.setView('history');
  },

  showMenu() {
    this.setView('menu');
  },

  continueMatch() {
    this.setView('match');
  },

  async menuChangeServer() {
    await this.changeServer();
    this.setView('match');
  },

  menuHistory() {
    this.openHistory();
  },

  async menuReset() {
    if (await this.confirmReset()) {
      this.setView('match');
    }
  },

  async menuNewMatch() {
    await this.requestHome();
  },

  async confirmReset() {
    const response = await prompt.showDialog({
      title: this.$t('strings.resetMatch'),
      message: this.$t('strings.resetQuestion'),
      buttons: [
        { text: this.$t('strings.cancel'), color: '#AEB8C8' },
        { text: this.$t('strings.reset'), color: '#FF7A86' }
      ]
    });
    if (response.index !== 1) return false;
    engine.dispatch({ type: 'Reset' });
    this.renderEngine();
    await this.persist();
    await this.updateKeepScreen();
    return true;
  },

  async clearSession() {
    const response = await prompt.showDialog({
      title: this.$t('strings.clearSession'),
      message: this.$t('strings.clearQuestion'),
      buttons: [
        { text: this.$t('strings.cancel'), color: '#AEB8C8' },
        { text: this.$t('strings.reset'), color: '#FF7A86' }
      ]
    });
    if (response.index !== 1) return;
    engine.dispatch({ type: 'ClearSession' });
    this.renderEngine();
    this.setView('match');
    await this.persist();
  },

  preset16() { this.target = 16; },
  preset20() { this.target = 20; },
  preset21() { this.target = 21; },
  preset24() { this.target = 24; },
  preset28() { this.target = 28; },
  preset32() { this.target = 32; },
  targetDown() { this.target = clamp(this.target - 1, 1, 99); },
  targetUp() { this.target = clamp(this.target + 1, 1, 99); },
  gamesDown() { this.gamesPerSet = clamp(this.gamesPerSet - 1, 1, 12); },
  gamesUp() { this.gamesPerSet = clamp(this.gamesPerSet + 1, 1, 12); },
  setsDown() { this.setsToWin = clamp(this.setsToWin - 1, 1, 3); },
  setsUp() { this.setsToWin = clamp(this.setsToWin + 1, 1, 3); },
  tieAtDown() { this.tieBreakAt = clamp(this.tieBreakAt - 1, 1, 12); },
  tieAtUp() { this.tieBreakAt = clamp(this.tieBreakAt + 1, 1, 12); },
  serveDown() { this.serveEvery = clamp(this.serveEvery - 1, 1, 16); },
  serveUp() { this.serveEvery = clamp(this.serveEvery + 1, 1, 16); },

  toggleWinByTwo(event) { this.winByTwo = checkedValue(event, this.winByTwo); },
  toggleGolden(event) { this.goldenPoint = checkedValue(event, this.goldenPoint); },
  toggleTieBreak(event) {
    this.tieBreakEnabled = checkedValue(event, this.tieBreakEnabled);
    this.updateSettingsVisibility();
  },
  toggleTrackServe(event) {
    this.trackServe = checkedValue(event, this.trackServe);
    this.updateSettingsVisibility();
  },
  toggleStartingServer(event) {
    this.startingServerA = checkedValue(event, this.startingServerA);
  }
}
