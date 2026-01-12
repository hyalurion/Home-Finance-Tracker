<template>
  <div class="splash-screen" v-if="showSplash">
    <div class="splash-content">
      <img 
        src="/assets/splash.png" 
        class="splash-image"
      />
      <div class="splash-overlay">
        <div class="top-right-controls">
          <div class="countdown">
            {{ countdown }}s
          </div>
          <button 
            class="close-button"
            @click="closeSplash"
          >
            <FontAwesomeIcon icon="times" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

const showSplash = ref(true);
const countdown = ref(5);
let countdownInterval = null;

const closeSplash = () => {
  if (countdownInterval) {
    clearInterval(countdownInterval);
  }
  showSplash.value = false;
};

onMounted(() => {
  // 倒计时逻辑
  countdownInterval = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      closeSplash();
    }
  }, 1000);

  // 5秒后自动关闭（作为备份）
  setTimeout(() => {
    closeSplash();
  }, 5000);
});

onUnmounted(() => {
  if (countdownInterval) {
    clearInterval(countdownInterval);
  }
});
</script>

<style scoped>
.splash-screen {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(255, 255, 255, 0.9);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
  animation: fadeIn 0.5s ease-in-out;
  overflow: hidden;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.splash-content {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.splash-image {
  width: 100vw;
  height: 100vh;
  object-fit: cover;
  animation: zoomIn 0.8s ease-out;
}

.splash-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  padding: 2rem;
}

.top-right-controls {
  display: flex;
  align-items: center;
  gap: 15px;
  pointer-events: auto;
}

.countdown {
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: white;
  padding: 12px 24px;
  border-radius: 30px;
  font-size: 18px;
  font-weight: bold;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.3);
  animation: pulse 2s infinite;
}

.close-button {
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: white;
  font-size: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.close-button:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.1);
  box-shadow: 0 6px 25px rgba(0, 0, 0, 0.4);
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes zoomIn {
  from {
    transform: scale(1.1);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

@keyframes pulse {
  0% {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  }
  50% {
    box-shadow: 0 6px 25px rgba(0, 0, 0, 0.4);
  }
  100% {
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .splash-screen {
    background-color: rgba(18, 18, 18, 0.9);
  }
  
  .close-button, .countdown {
    background: rgba(255, 255, 255, 0.15);
    border: 1px solid rgba(255, 255, 255, 0.2);
  }
  
  .close-button:hover {
    background: rgba(255, 255, 255, 0.25);
  }
}
</style>