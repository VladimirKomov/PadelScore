export const PREFERENCE_CHUNK_SIZE = 7000;
export function splitIntoChunks(value, chunkSize = PREFERENCE_CHUNK_SIZE) {
    if (value.length === 0) {
        return [''];
    }
    const chunks = [];
    for (let offset = 0; offset < value.length; offset += chunkSize) {
        chunks.push(value.slice(offset, offset + chunkSize));
    }
    return chunks;
}
export function joinChunks(chunks) {
    return chunks.join('');
}
