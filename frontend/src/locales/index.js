import { createI18n } from 'vue-i18n'
import en from './en.js'
import zh from './zh.js'

// 从 localStorage 获取保存的语言设置，默认跟随浏览器
const getDefaultLocale = () => {
  const saved = localStorage.getItem('locale')
  if (saved && ['en', 'zh'].includes(saved)) {
    return saved
  }
  // 跟随浏览器语言
  const browserLang = navigator.language.toLowerCase()
  return browserLang.startsWith('zh') ? 'zh' : 'en'
}

const i18n = createI18n({
  legacy: false,
  locale: getDefaultLocale(),
  fallbackLocale: 'en',
  messages: {
    en,
    zh
  }
})

export default i18n

// 切换语言的辅助函数
export const setLocale = (locale) => {
  if (['en', 'zh'].includes(locale)) {
    i18n.global.locale.value = locale
    localStorage.setItem('locale', locale)
    document.documentElement.setAttribute('lang', locale)
  }
}

export const getLocale = () => i18n.global.locale.value

export const t = (key, params) => i18n.global.t(key, params)
