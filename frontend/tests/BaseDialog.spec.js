import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseDialog from '../src/components/BaseDialog.vue'

const ElDialogStub = {
  name: 'ElDialog',
  template: `
    <div
      class="el-dialog"
      v-if="modelValue"
      :data-width="width"
      :data-close-on-click-modal="closeOnClickModal"
      :data-append-to-body="appendToBody"
      :data-top="top"
    >
      <div class="el-dialog__header">
        <span class="el-dialog__title">{{ title }}</span>
      </div>
      <div class="el-dialog__body"><slot /></div>
      <div class="el-dialog__footer"><slot name="footer" /></div>
    </div>
  `,
  props: [
    'modelValue', 'title', 'width', 'top',
    'closeOnClickModal', 'appendToBody', 'class'
  ],
  emits: ['update:modelValue', 'close', 'opened']
}

function mountDialog(props = {}, options = {}) {
  return mount(BaseDialog, {
    props: {
      modelValue: true,
      title: 'Test Dialog',
      ...props
    },
    slots: options.slots,
    global: {
      stubs: {
        'el-dialog': ElDialogStub
      }
    }
  })
}

describe('BaseDialog', () => {
  describe('Rendering', () => {
    it('renders el-dialog when modelValue is true', () => {
      const wrapper = mountDialog({ modelValue: true })
      expect(wrapper.find('.el-dialog').exists()).toBe(true)
    })

    it('does not render el-dialog when modelValue is false', () => {
      const wrapper = mountDialog({ modelValue: false })
      expect(wrapper.find('.el-dialog').exists()).toBe(false)
    })

    it('displays the title text', () => {
      const wrapper = mountDialog({ title: 'My Title' })
      expect(wrapper.find('.el-dialog__title').text()).toBe('My Title')
    })
  })

  describe('Props', () => {
    it('passes width to el-dialog', () => {
      const wrapper = mountDialog({ width: '600px' })
      expect(wrapper.find('.el-dialog').attributes('data-width')).toBe('600px')
    })

    it('defaults closeOnClickModal to false', () => {
      const wrapper = mountDialog()
      expect(wrapper.find('.el-dialog').attributes('data-close-on-click-modal')).toBe('false')
    })

    it('passes appendToBody to el-dialog', () => {
      const wrapper = mountDialog({ appendToBody: true })
      expect(wrapper.find('.el-dialog').attributes('data-append-to-body')).toBe('true')
    })

    it('passes top to el-dialog', () => {
      const wrapper = mountDialog({ top: '10vh' })
      expect(wrapper.find('.el-dialog').attributes('data-top')).toBe('10vh')
    })
  })

  describe('Body padding', () => {
    it('wraps body content in base-dialog__body by default', () => {
      const wrapper = mountDialog({}, {
        slots: { default: '<p>Content</p>' }
      })
      expect(wrapper.find('.base-dialog__body').exists()).toBe(true)
    })

    it('adds no-padding class when bodyPadding is false', () => {
      const wrapper = mountDialog({ bodyPadding: false }, {
        slots: { default: '<p>Content</p>' }
      })
      expect(wrapper.find('.base-dialog__body--no-padding').exists()).toBe(true)
    })

    it('does not add no-padding class when bodyPadding is true', () => {
      const wrapper = mountDialog({ bodyPadding: true }, {
        slots: { default: '<p>Content</p>' }
      })
      expect(wrapper.find('.base-dialog__body--no-padding').exists()).toBe(false)
    })
  })

  describe('Events', () => {
    it('emits update:modelValue when dialog visibility changes', async () => {
      const wrapper = mountDialog()
      const dialog = wrapper.findComponent({ name: 'ElDialog' })
      await dialog.vm.$emit('update:modelValue', false)
      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')[0][0]).toBe(false)
    })

    it('emits close event when dialog closes', async () => {
      const wrapper = mountDialog()
      const dialog = wrapper.findComponent({ name: 'ElDialog' })
      await dialog.vm.$emit('close')
      expect(wrapper.emitted('close')).toBeTruthy()
    })

    it('emits opened event when dialog opens', async () => {
      const wrapper = mountDialog()
      const dialog = wrapper.findComponent({ name: 'ElDialog' })
      await dialog.vm.$emit('opened')
      expect(wrapper.emitted('opened')).toBeTruthy()
    })
  })

  describe('Slots', () => {
    it('renders default slot content', () => {
      const wrapper = mountDialog({}, {
        slots: { default: '<div class="test-content">Hello</div>' }
      })
      expect(wrapper.find('.test-content').exists()).toBe(true)
      expect(wrapper.find('.test-content').text()).toBe('Hello')
    })

    it('renders footer slot content', () => {
      const wrapper = mountDialog({}, {
        slots: { footer: '<button class="test-btn">Save</button>' }
      })
      expect(wrapper.find('.test-btn').exists()).toBe(true)
      expect(wrapper.find('.test-btn').text()).toBe('Save')
    })
  })

  describe('Style classes', () => {
    it('passes base-dialog class to el-dialog', () => {
      const wrapper = mountDialog()
      const dialog = wrapper.findComponent({ name: 'ElDialog' })
      // Check the class prop includes 'base-dialog'
      const classProp = dialog.props('class')
      expect(classProp).toContain('base-dialog')
    })
  })
})
