<template>
  <div class="brand-decoration" :class="[`brand-decoration--${variant}`]">
    <!--  -->
    <svg v-if="variant === 'wave'" class="decoration-wave" viewBox="0 0 1440 120" preserveAspectRatio="none">
      <path d="M0,60 C360,120 720,0 1080,60 C1260,90 1350,75 1440,60 L1440,120 L0,120 Z" :fill="waveColor" fill-opacity="0.08"/>
      <path d="M0,80 C360,30 720,100 1080,50 C1260,30 1350,45 1440,40 L1440,120 L0,120 Z" :fill="waveColor" fill-opacity="0.05"/>
    </svg>
    
    <!--  -->
    <div v-if="variant === 'topbar'" class="decoration-topbar">
      <div class="topbar-line" :style="{ background: `linear-gradient(90deg, transparent, ${barColor}, transparent)` }"></div>
      <div class="topbar-dot" :style="{ background: barColor }"></div>
      <div class="topbar-line" :style="{ background: `linear-gradient(90deg, transparent, ${barColor}, transparent)` }"></div>
    </div>
    
    <!--  -->
    <div v-if="variant === 'pattern'" class="decoration-pattern">
      <svg class="pattern-svg" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <pattern id="crossPattern" x="0" y="0" width="20" height="20" patternUnits="userSpaceOnUse">
            <rect x="8" y="4" width="4" height="12" rx="1" :fill="patternColor" fill-opacity="0.06"/>
            <rect x="4" y="8" width="12" height="4" rx="1" :fill="patternColor" fill-opacity="0.06"/>
          </pattern>
        </defs>
        <rect width="100" height="100" fill="url(#crossPattern)"/>
      </svg>
    </div>

    <!--  -->
    <div v-if="variant === 'corner'" class="decoration-corner">
      <svg width="120" height="120" viewBox="0 0 120 120" fill="none">
        <circle cx="60" cy="60" r="50" :stroke="cornerColor" stroke-opacity="0.08" stroke-width="2" fill="none"/>
        <circle cx="60" cy="60" r="35" :stroke="cornerColor" stroke-opacity="0.05" stroke-width="1.5" fill="none"/>
        <circle cx="60" cy="60" r="20" :stroke="cornerColor" stroke-opacity="0.03" stroke-width="1" fill="none"/>
      </svg>
    </div>
  </div>
</template>

<script>
export default {
  name: 'BrandDecoration',
  props: {
    variant: {
      type: String,
      default: 'wave',
      validator: v => ['wave', 'topbar', 'pattern', 'corner'].includes(v)
    },
    color: {
      type: String,
      default: 'teal' // teal / blue / red
    }
  },
  computed: {
    waveColor() {
      return this.getColor()
    },
    barColor() {
      return this.getColor()
    },
    patternColor() {
      return this.getColor()
    },
    cornerColor() {
      return this.getColor()
    }
  },
  methods: {
    getColor() {
      const colors = {
        teal: '#0EA5A5',
        blue: '#15559a',
        red: '#ff2442',
        purple: '#a855f7'
      }
      return colors[this.color] || colors.teal
    }
  }
}
</script>

<style scoped>
.brand-decoration {
  position: relative;
  pointer-events: none;
}

/*  */
.brand-decoration--wave {
  width: 100%;
  overflow: hidden;
}

.decoration-wave {
  display: block;
  width: 100%;
  height: 80px;
}

/*  */
.brand-decoration--topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.decoration-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.topbar-line {
  flex: 1;
  height: 2px;
  border-radius: 1px;
}

.topbar-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/*  */
.brand-decoration--pattern {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.pattern-svg {
  width: 100%;
  height: 100%;
  opacity: 0.5;
}

/*  */
.brand-decoration--corner {
  position: absolute;
  top: -20px;
  right: -20px;
  opacity: 0.6;
}
</style>
