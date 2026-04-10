import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import CodeEditor from '../src/components/editor/CodeEditor.vue'

vi.mock('../src/api/git', () => ({
  getFileTree: vi.fn(),
  readFileContent: vi.fn(),
  writeFileContent: vi.fn(),
}))

describe('CodeEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders with correct title', () => {
    const wrapper = mount(CodeEditor, {
      props: {
        projectId: 1,
        taskId: 1,
        taskTitle: 'Fix bug',
      },
      global: {
        stubs: {
          Teleport: {
            template: '<div><slot /></div>',
          },
        },
      },
    })

    expect(wrapper.text()).toContain('Fix bug')
  })

  it('emits close when close button clicked', async () => {
    const wrapper = mount(CodeEditor, {
      props: {
        projectId: 1,
        taskId: 1,
        taskTitle: 'Test',
      },
      global: {
        stubs: {
          Teleport: {
            template: '<div><slot /></div>',
          },
        },
      },
    })

    const closeBtn = wrapper.find('.editor-close')
    await closeBtn.trigger('click')

    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
