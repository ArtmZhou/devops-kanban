interface NodeWithDeps {
  depends_on_indices: number[];
}

export function hasCycle(nodes: NodeWithDeps[]): boolean {
  const WHITE = 0, GRAY = 1, BLACK = 2;
  const color = new Array(nodes.length).fill(WHITE);

  function dfs(i: number): boolean {
    if (color[i] === GRAY) return true;
    if (color[i] === BLACK) return false;
    color[i] = GRAY;
    for (const dep of nodes[i]!.depends_on_indices) {
      if (dep < 0 || dep >= nodes.length) continue;
      if (dfs(dep)) return true;
    }
    color[i] = BLACK;
    return false;
  }

  for (let i = 0; i < nodes.length; i++) {
    if (color[i] === WHITE && dfs(i)) return true;
  }
  return false;
}
