<template>
  <div class="brand-logo" :class="[`brand-logo--${size}`]" :style="logoStyle">
    <svg v-if="showIcon" class="brand-logo__icon" :width="iconSize" :height="iconSize" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
      <!--  -->
      <circle cx="24" cy="24" r="24" :fill="bgGradientUrl" />
      <defs>
        <linearGradient :id="gradientId" x1="0" y1="0" x2="48" y2="48" gradientUnits="userSpaceOnUse">
          <stop :stop-color="colorStart" />
          <stop offset="1" :stop-color="colorEnd" />
        </linearGradient>
      </defs>
      <!--  +  -->
      <path d="M24 36C24 36 12 28 12 20C12 16 15 13 18 13C20.5 13 22.5 14.5 24 16.5C25.5 14.5 27.5 13 30 13C33 13 36 16 36 20C36 28 24 36 24 36Z" fill="white" fill-opacity="0.9"/>
      <!--  -->
      <rect x="21" y="19" width="6" height="12" rx="1" :fill="colorStart" fill-opacity="0.8"/>
      <rect x="18" y="22" width="12" height="6" rx="1" :fill="colorStart" fill-opacity="0.8"/>
    </svg>
    <span v-if="showText" class="brand-logo__text" :style="textStyle">{{ text }}</span>
  </div>
</template>

<script>
export default {
  name: 'BrandLogo',
  props: {
    text: {
      type: String,
      default: ''
    },
    size: {
      type: String,
      default: 'medium',
      validator: v => ['small', 'medium', 'large'].includes(v)
    },
    color: {
      type: String,
      default: 'teal' // teal / blue / red
    },
    showIcon: {
      type: Boolean,
      default: true
    },
    showText: {
      type: Boolean,
      default: true
    },
    textColor: {
      type: String,
      default: ''
    },
    vertical: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      gradientId: 'brandGrad_' + Math.random().toString(36).substr(2, 9)
    }
  },
  computed: {
    colorStart() {
      const colors = {
        teal: '#0EA5A5',
        blue: '#15559a',
        red: '#ff2442',
        purple: '#a855f7'
      }
      return colors[this.color] || colors.teal
    },
    colorEnd() {
      const colors = {
        teal: '#14B8A6',
        blue: '#4a90d9',
        red: '#ff6b81',
        purple: '#c084fc'
      }
      return colors[this.color] || colors.teal
    },
    bgGradientUrl() {
      return `url(#${this.gradientId})`
    },
    iconSize() {
      const sizes = { small: 32, medium: 40, large: 56 }
      return sizes[this.size]
    },
    logoStyle() {
      return {
        flexDirection: this.vertical ? 'column' : 'row',
        gap: this.vertical ? '8px' : '10px'
      }
    },
    textStyle() {
      if (this.textColor) return { color: this.textColor }
      const colors = {
        teal: '#0EA5A5',
        blue: '#15559a',
        red: '#ff2442',
        purple: '#a855f7'
      }
      return { color: colors[this.color] || colors.teal }
    }
  }
}
</script>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  user-select: none;
}

.brand-logo__icon {
  flex-shrink: 0;
}

.brand-logo__text {
  font-weight: 700;
  letter-spacing: 1px;
  white-space: nowrap;
}

.brand-logo--small .brand-logo__text {
  font-size: 16px;
}

.brand-logo--medium .brand-logo__text {
  font-size: 22px;
}

.brand-logo--large .brand-logo__text {
  font-size: 32px;
}
</style>
