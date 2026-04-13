import { describe, expect, it } from 'vitest'
import { filterSkillsByTemplate } from '../src/utils/skillWorkflowFilter'

const skills = [
  { id: 1, name: 'TDD' },
  { id: 2, name: 'SDD' },
  { id: 3, name: 'Debugging' },
  { id: 4, name: 'Code Review' }
]

const agents = [
  { id: 10, name: 'Architect', skills: [1, 2] },
  { id: 20, name: 'Backend Dev', skills: [3] },
  { id: 30, name: 'QA', skills: [4, 1] }
]

const templates = [
  {
    template_id: 'dev-feature-v1',
    name: 'Feature Dev',
    steps: [
      { id: 's1', agentId: 10 },
      { id: 's2', agentId: 20 }
    ]
  },
  {
    template_id: 'qa-v1',
    name: 'QA Only',
    steps: [
      { id: 's1', agentId: 30 }
    ]
  }
]

describe('filterSkillsByTemplate', () => {
  it('returns all skills when no template is selected', () => {
    const result = filterSkillsByTemplate(skills, templates, agents, null)
    expect(result).toEqual(skills)
  })

  it('returns all skills when template id is empty string', () => {
    const result = filterSkillsByTemplate(skills, templates, agents, '')
    expect(result).toEqual(skills)
  })

  it('filters skills to only those used by agents in the selected template', () => {
    // dev-feature-v1 uses agents 10 (skills [1,2]) and 20 (skills [3])
    const result = filterSkillsByTemplate(skills, templates, agents, 'dev-feature-v1')
    expect(result).toEqual([
      { id: 1, name: 'TDD' },
      { id: 2, name: 'SDD' },
      { id: 3, name: 'Debugging' }
    ])
  })

  it('returns skills for a different template', () => {
    // qa-v1 uses agent 30 (skills [4, 1])
    const result = filterSkillsByTemplate(skills, templates, agents, 'qa-v1')
    expect(result).toEqual([
      { id: 1, name: 'TDD' },
      { id: 4, name: 'Code Review' }
    ])
  })

  it('returns all skills when template id is not found', () => {
    const result = filterSkillsByTemplate(skills, templates, agents, 'nonexistent')
    expect(result).toEqual(skills)
  })

  it('handles template with steps that have no agentId', () => {
    const templatesWithNull = [
      {
        template_id: 'sparse',
        name: 'Sparse',
        steps: [
          { id: 's1', agentId: null },
          { id: 's2', agentId: 20 }
        ]
      }
    ]
    const result = filterSkillsByTemplate(skills, templatesWithNull, agents, 'sparse')
    expect(result).toEqual([{ id: 3, name: 'Debugging' }])
  })

  it('handles agent with no skills array', () => {
    const agentsNoSkills = [
      { id: 10, name: 'Architect' }
    ]
    const templatesOne = [
      {
        template_id: 't1',
        name: 'T1',
        steps: [{ id: 's1', agentId: 10 }]
      }
    ]
    const result = filterSkillsByTemplate(skills, templatesOne, agentsNoSkills, 't1')
    expect(result).toEqual([])
  })

  it('handles agent skills stored as JSON string', () => {
    const agentsJsonSkills = [
      { id: 10, name: 'Architect', skills: '[1, 2]' }
    ]
    const templatesOne = [
      {
        template_id: 't1',
        name: 'T1',
        steps: [{ id: 's1', agentId: 10 }]
      }
    ]
    const result = filterSkillsByTemplate(skills, templatesOne, agentsJsonSkills, 't1')
    expect(result).toEqual([
      { id: 1, name: 'TDD' },
      { id: 2, name: 'SDD' }
    ])
  })
})
