// 处理主题样式
export function handleThemeStyle(theme) {
  const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark')
  const primary = isDark ? softenPrimaryForDark(theme) : theme
  document.documentElement.style.setProperty('--el-color-primary', primary)
  for (let i = 1; i <= 9; i++) {
    document.documentElement.style.setProperty(`--el-color-primary-light-${i}`, `${getLightColor(primary, i / 10)}`)
  }
  for (let i = 1; i <= 9; i++) {
    document.documentElement.style.setProperty(`--el-color-primary-dark-${i}`, `${getDarkColor(primary, i / 10)}`)
  }
}

/** 混合两种十六进制颜色 */
export function mixHexColors(fg, bg, t) {
  const a = hexToRgb(String(fg).replace('#', ''))
  const b = hexToRgb(String(bg).replace('#', ''))
  const out = [0, 1, 2].map((i) => Math.round(a[i] * (1 - t) + b[i] * t))
  return rgbToHex(out[0], out[1], out[2])
}

/** 暗色模式下调整主题色：向白色提亮，保证在深色底上仍然可读。
 *  （原来向 #2d3036 压暗，会让主色在暗色背景下对比度不足） */
export function softenPrimaryForDark(theme) {
  return mixHexColors(theme, '#ffffff', 0.18)
}

// hex颜色转rgb颜色
export function hexToRgb(str) {
  str = str.replace('#', '')
  let hexs = str.match(/../g)
  for (let i = 0; i < 3; i++) {
    hexs[i] = parseInt(hexs[i], 16)
  }
  return hexs
}

// rgb颜色转Hex颜色
export function rgbToHex(r, g, b) {
  let hexs = [r.toString(16), g.toString(16), b.toString(16)]
  for (let i = 0; i < 3; i++) {
    if (hexs[i].length == 1) {
      hexs[i] = `0${hexs[i]}`
    }
  }
  return `#${hexs.join('')}`
}

// 变浅颜色值
export function getLightColor(color, level) {
  let rgb = hexToRgb(color)
  for (let i = 0; i < 3; i++) {
    rgb[i] = Math.floor((255 - rgb[i]) * level + rgb[i])
  }
  return rgbToHex(rgb[0], rgb[1], rgb[2])
}

// 变深颜色值
export function getDarkColor(color, level) {
  let rgb = hexToRgb(color)
  for (let i = 0; i < 3; i++) {
    rgb[i] = Math.floor(rgb[i] * (1 - level))
  }
  return rgbToHex(rgb[0], rgb[1], rgb[2])
}
