interface ProjectLike {
  id: number;
  name: string;
  git_url: string | null | undefined;
}

interface SuggestionMatchInput {
  title: string;
  target_repo_url: string | null;
}

export function matchProject(
  suggestion: SuggestionMatchInput,
  projects: ProjectLike[],
): number | null {
  if (suggestion.target_repo_url) {
    const normUrl = normalizeUrl(suggestion.target_repo_url);
    for (const p of projects) {
      if (p.git_url && normalizeUrl(p.git_url) === normUrl) {
        return p.id;
      }
    }
  }

  const titleLower = suggestion.title.toLowerCase();
  for (const p of projects) {
    const nameLower = p.name.toLowerCase();
    if (titleLower.includes(nameLower) || nameLower.includes(titleLower)) {
      return p.id;
    }
  }

  return null;
}

function normalizeUrl(url: string): string {
  return url.trim().toLowerCase().replace(/\.git$/, '').replace(/\/$/, '');
}
