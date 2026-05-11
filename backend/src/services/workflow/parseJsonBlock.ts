export function extractJsonBlock(text: string): unknown {
  const fencedJson = /```json\s*\n([\s\S]*?)\n```/i.exec(text);
  if (fencedJson && fencedJson[1]) {
    return parseOrThrow(fencedJson[1]);
  }

  const fencedPlain = /```\s*\n([\s\S]*?)\n```/.exec(text);
  if (fencedPlain && fencedPlain[1]) {
    const body = fencedPlain[1].trim();
    if (body.startsWith('[') || body.startsWith('{')) {
      return parseOrThrow(body);
    }
  }

  const arrayMatch = /\[[\s\S]*\]/.exec(text);
  if (arrayMatch) {
    return parseOrThrow(arrayMatch[0]);
  }

  throw new Error('no JSON block or array found in output');
}

function parseOrThrow(raw: string): unknown {
  try {
    return JSON.parse(raw.trim());
  } catch (e) {
    throw new Error(`failed to parse JSON block: ${(e as Error).message}`);
  }
}
