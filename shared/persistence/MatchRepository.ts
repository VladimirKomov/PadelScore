import data_storage from '@ohos.data.storage';
import hilog from '@ohos.hilog';
import { MatchEngine } from '../engine/MatchEngine';
import type { MatchMode, ModeSettings } from '../engine/Types';
import { joinChunks, splitIntoChunks } from './ChunkCodec';

const CHUNK_COUNT_KEY = 'active_chunk_count';
const CHUNK_PREFIX = 'active_chunk_';
const SETTINGS_PREFIX = 'settings_';
const LOG_DOMAIN = 0x5044;
const LOG_TAG = 'PadelScoreStore';

export class MatchRepository {
  private storagePath: string;
  private store?: data_storage.Storage;

  constructor(storagePath: string) {
    this.storagePath = storagePath;
  }

  private async preferences(): Promise<data_storage.Storage> {
    if (this.store === undefined) {
      this.store = await data_storage.getStorage(this.storagePath);
    }
    return this.store;
  }

  async saveEngine(engine: MatchEngine): Promise<boolean> {
    try {
      const store = await this.preferences();
      const previousCountValue = await store.get(CHUNK_COUNT_KEY, 0);
      const previousCount =
        typeof previousCountValue === 'number' ? Math.max(0, previousCountValue) : 0;
      const chunks = splitIntoChunks(engine.serialize());
      await store.put(CHUNK_COUNT_KEY, chunks.length);
      for (let index = 0; index < chunks.length; index += 1) {
        await store.put(CHUNK_PREFIX + String(index), chunks[index]);
      }
      for (let index = chunks.length; index < previousCount; index += 1) {
        await store.delete(CHUNK_PREFIX + String(index));
      }
      await store.flush();
      return true;
    } catch (error) {
      hilog.error(
        LOG_DOMAIN,
        LOG_TAG,
        'Failed to persist match state: %{public}s',
        String(error)
      );
      return false;
    }
  }

  async loadEngine(fallbackMode: MatchMode = 'americano'): Promise<MatchEngine> {
    try {
      const store = await this.preferences();
      const countValue = await store.get(CHUNK_COUNT_KEY, 0);
      const count = typeof countValue === 'number' ? countValue : 0;
      if (count <= 0 || count > 256) {
        return new MatchEngine(fallbackMode);
      }
      const chunks: string[] = [];
      for (let index = 0; index < count; index += 1) {
        const value = await store.get(CHUNK_PREFIX + String(index), '');
        if (typeof value !== 'string') {
          return new MatchEngine(fallbackMode);
        }
        chunks.push(value);
      }
      const restored = MatchEngine.restore(joinChunks(chunks), fallbackMode);
      if (!restored.restored) {
        hilog.warn(LOG_DOMAIN, LOG_TAG, 'Stored match ignored: %{public}s', restored.reason);
      }
      return restored.engine;
    } catch (error) {
      hilog.error(
        LOG_DOMAIN,
        LOG_TAG,
        'Failed to restore match state: %{public}s',
        String(error)
      );
      return new MatchEngine(fallbackMode);
    }
  }

  async saveLastSettings(mode: MatchMode, settings: ModeSettings): Promise<boolean> {
    try {
      const store = await this.preferences();
      await store.put(SETTINGS_PREFIX + mode, JSON.stringify(settings));
      await store.flush();
      return true;
    } catch (error) {
      hilog.error(
        LOG_DOMAIN,
        LOG_TAG,
        'Failed to persist mode settings: %{public}s',
        String(error)
      );
      return false;
    }
  }

  async loadLastSettings(mode: MatchMode): Promise<ModeSettings | null> {
    try {
      const store = await this.preferences();
      const raw = await store.get(SETTINGS_PREFIX + mode, '');
      if (typeof raw !== 'string' || raw.length === 0) {
        return null;
      }
      const value = JSON.parse(raw) as ModeSettings;
      return value.mode === mode ? value : null;
    } catch (error) {
      hilog.warn(
        LOG_DOMAIN,
        LOG_TAG,
        'Stored mode settings ignored: %{public}s',
        String(error)
      );
      return null;
    }
  }
}
