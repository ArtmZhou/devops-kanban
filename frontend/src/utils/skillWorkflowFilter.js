function parseSkillIds(skills) {
  if (Array.isArray(skills)) return skills
  if (typeof skills === 'string') {
    try { return JSON.parse(skills) } catch { return [] }
  }
  return []
}

export function filterSkillsByTemplate(skills, templates, agents, selectedTemplateId) {
  if (!selectedTemplateId) return skills

  const template = templates.find(t => t.template_id === selectedTemplateId)
  if (!template) return skills

  const agentIds = new Set(
    template.steps
      .map(step => step.agentId)
      .filter(id => typeof id === 'number')
  )
  const relatedAgents = agents.filter(a => agentIds.has(a.id))
  const skillIds = new Set(relatedAgents.flatMap(a => parseSkillIds(a.skills)))

  return skills.filter(s => skillIds.has(s.id))
}
