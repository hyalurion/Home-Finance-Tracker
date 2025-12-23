<template>
  <div class="splash-screen" v-if="showSplash">
    <div class="splash-content">
      <img 
        src="/assets/shengdan.jpg" 
        class="splash-image"
      />
      <button 
        class="close-button"
        @click="closeSplash"
      >
        <FontAwesomeIcon icon="times" />
      </button>
      <div class="countdown">
        {{ countdown }}s
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
  background-color: rgba(255, 255, 255, 1);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
  animation: fadeIn 0.5s ease-in-out;
  overflow: hidden;
}

.splash-content {
  position: relative;
  max-width: 90vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.splash-image {
  width: 100%;
  height: auto;
  max-height: 80vh;
  object-fit: contain;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
}

.close-button {
  position: absolute;
  top: -40px;
  right: 0;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.5);
  color: white;
  font-size: 18px;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  transition: all 0.3s ease;
}

.close-button:hover {
  background-color: rgba(0, 0, 0, 0.8);
  transform: scale(1.1);
}

.countdown {
  position: absolute;
  bottom: -40px;
  left: 50%;
  transform: translateX(-50%);
  background-color: rgba(0, 0, 0, 0.5);
  color: white;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 16px;
  font-weight: bold;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .splash-screen {
    background-color: rgba(18, 18, 18, 1);
  }
  
  .close-button, .countdown {
    background-color: rgba(255, 255, 255, 0.3);
  }
  
  .close-button:hover {
    background-color: rgba(255, 255, 255, 0.5);
  }
}
</style>