import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import IterationForm from '../src/components/iteration/IterationForm.vue'
import i18n from '../src/locales'

function mountForm(props = {}) {
  return mount(IterationForm, {
    props: {
      modelValue: true,
      ...props
    },
    global: {
      plugins: [i18n],
      stubs: {
        'el-dialog': {
          template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
          props: ['modelValue', 'title', 'width'],
          emits: ['update:modelValue']
        },
        'el-form': {
          template: '<form class="el-form" @submit.prevent="$emit(\'submit\')"><slot /></form>',
          props: ['model', 'rules'],
          emits: ['submit'],
          methods: {
            validate: vi.fn().mockResolvedValue(true)
          }
        },
        'el-form-item': {
          template: '<div class="el-form-item"><slot /></div>',
          props: ['label', 'prop']
        },
        'el-input': {
          template: '<input class="el-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          props: ['modelValue', 'type', 'rows', 'placeholder'],
          emits: ['update:modelValue']
        },
        'el-select': {
          template: '<select class="el-select" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
          props: ['modelValue'],
          emits: ['update:modelValue']
        },
        'el-option': {
          template: '<option :value="value"><slot /></option>',
          props: ['label', 'value']
        },
        'el-button': {
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
          props: ['type', 'disabled'],
          emits: ['click']
        },
        'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
        'el-col': { template: '<div class="el-col"><slot /></div>', props: ['span'] },
        'el-date-picker': {
          template: '<input class="el-date-picker" />',
          props: ['modelValue', 'type', 'placeholder', 'valueFormat']
        }
      }
    }
  })
}

describe('IterationForm', () => {
  it('shows create title for new iteration', () => {
    const wrapper = mountForm({ iteration: null })
    expect(wrapper.vm.isEditing).toBe(false)
  })

  it('shows edit title for existing iteration', async () => {
    const wrapper = mountForm({
      iteration: { id: 1, name: 'Sprint 1', status: 'ACTIVE' }
    })
    await nextTick()
    expect(wrapper.vm.isEditing).toBe(true)
  })

  it('populates form from iteration prop', async () => {
    const wrapper = mountForm({
      iteration: {
        id: 1,
        name: 'Sprint 1',
        description: 'A sprint',
        goal: 'Goal',
        status: 'ACTIVE',
        start_date: '2025-01-01',
        end_date: '2025-01-31'
      }
    })
    await nextTick()

    expect(wrapper.vm.form.name).toBe('Sprint 1')
    expect(wrapper.vm.form.description).toBe('A sprint')
    expect(wrapper.vm.form.goal).toBe('Goal')
    expect(wrapper.vm.form.status).toBe('ACTIVE')
  })

  it('resets form when iteration is null', async () => {
    const wrapper = mountForm({
      iteration: { id: 1, name: 'Old' }
    })
    await nextTick()
    expect(wrapper.vm.form.name).toBe('Old')

    await wrapper.setProps({ iteration: null })
    await nextTick()
    expect(wrapper.vm.form.name).toBe('')
    expect(wrapper.vm.form.status).toBe('PLANNED')
  })

  it('handleSubmit emits submit with form data', async () => {
    const wrapper = mountForm()
    wrapper.vm.form.name = 'New Sprint'
    await nextTick()

    await wrapper.vm.handleSubmit()
    expect(wrapper.emitted('submit')).toBeTruthy()
    expect(wrapper.emitted('submit')[0][0].name).toBe('New Sprint')
  })

  it('handleCancel emits cancel and closes dialog', async () => {
    const wrapper = mountForm()
    wrapper.vm.handleCancel()
    await nextTick()

    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')[0][0]).toBe(false)
  })

  it('shows status select only when editing', async () => {
    const wrapper = mountForm({ iteration: null })
    expect(wrapper.vm.isEditing).toBe(false)

    await wrapper.setProps({
      iteration: { id: 1, name: 'Sprint', status: 'ACTIVE' }
    })
    await nextTick()
    expect(wrapper.vm.isEditing).toBe(true)
  })

  it('has name validation rule', () => {
    const wrapper = mountForm()
    expect(wrapper.vm.rules.name).toBeDefined()
    expect(wrapper.vm.rules.name.length).toBeGreaterThan(0)
    expect(wrapper.vm.rules.name[0].required).toBe(true)
  })
})
