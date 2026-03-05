import { defineStore } from 'pinia'

export const useThemeStore = defineStore('theme', {
  state: () => ({
    isDark: localStorage.getItem('theme') !== 'light'
  }),

  getters: {
    themeClass: (state) => state.isDark ? 'theme-dark' : 'theme-light'
  },

  actions: {
    toggleTheme() {
      this.isDark = !this.isDark
      localStorage.setItem('theme', this.isDark ? 'dark' : 'light')
      this.applyTheme()
    },

    applyTheme() {
      if (this.isDark) {
        document.documentElement.classList.remove('theme-light')
        document.documentElement.classList.add('theme-dark')
      } else {
        document.documentElement.classList.remove('theme-dark')
        document.documentElement.classList.add('theme-light')
      }
    },

    initTheme() {
      this.applyTheme()
    }
  }
})
