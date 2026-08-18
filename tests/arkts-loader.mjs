import { access } from 'node:fs/promises';

export async function resolve(specifier, context, nextResolve) {
  try {
    return await nextResolve(specifier, context);
  } catch (error) {
    if (
      (specifier.startsWith('./') || specifier.startsWith('../')) &&
      !specifier.endsWith('.ts') &&
      !specifier.endsWith('.ets')
    ) {
      const candidate = new URL(specifier + '.ts', context.parentURL);
      try {
        await access(candidate);
        return { url: candidate.href, shortCircuit: true };
      } catch (_missing) {
        // Preserve Node's original, more useful resolution error.
      }
    }
    throw error;
  }
}

