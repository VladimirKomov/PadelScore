export const PREFERENCE_CHUNK_SIZE = 7000;

export function splitIntoChunks(value: string, chunkSize: number = PREFERENCE_CHUNK_SIZE): string[] {
  if (value.length === 0) {
    return [''];
  }
  const chunks: string[] = [];
  for (let offset = 0; offset < value.length; offset += chunkSize) {
    chunks.push(value.slice(offset, offset + chunkSize));
  }
  return chunks;
}

export function joinChunks(chunks: string[]): string {
  return chunks.join('');
}

