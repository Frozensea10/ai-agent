<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Nunito:wght@400;500;600;700;800;900&display=swap');

:root {
  --cream: #FFF8E7;
  --cream-dark: #FFF5E6;
  --coral: #FF6B6B;
  --coral-light: #FF8E8E;
  --coral-pale: #FFE8E8;
  --teal: #4ECDC4;
  --teal-dark: #45B7AA;
  --teal-pale: #E0F7F5;
  --lavender: #A8A4E6;
  --lavender-pale: #EDEAFB;
  --golden: #FFD93D;
  --golden-pale: #FFF8DC;
  --mint: #98D8C8;
  --mint-pale: #E8F8F3;
  --blue-pale: #E3F0FF;
  --pink-pale: #FFE0EC;
  --text-dark: #2D3436;
  --text-medium: #636E72;
  --text-light: #B2BEC3;
  --border-light: #e2e8f0;
  --card-bg: #FFFFFF;
  --sidebar-bg: #ffffff;
  --radius-sm: 8px;
  --radius-md: 14px;
  --radius-lg: 16px;
  --radius-xl: 20px;
  --radius-full: 9999px;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
}

body {
  font-family: 'Nunito', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: var(--text-dark);
  background: var(--cream);
  overflow-x: hidden;
}

/* Global scrollbar */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
::-webkit-scrollbar-track {
  background: var(--cream);
}
::-webkit-scrollbar-thumb {
  background: var(--coral-light);
  border-radius: 999px;
  border: 2px solid var(--cream);
}
::-webkit-scrollbar-thumb:hover {
  background: var(--coral);
}

/* Animations */
@keyframes float {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
}
@keyframes slideUpBounce {
  0% { transform: translateY(30px); opacity: 0; }
  60% { transform: translateY(-6px); opacity: 1; }
  100% { transform: translateY(0); opacity: 1; }
}
@keyframes wiggle {
  0%, 100% { transform: rotate(-2deg); }
  50% { transform: rotate(2deg); }
}
@keyframes twinkle {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}
@keyframes drift {
  0% { transform: translateX(0); }
  50% { transform: translateX(8px); }
  100% { transform: translateX(0); }
}
@keyframes popIn {
  0% { transform: scale(0.8); opacity: 0; }
  100% { transform: scale(1); opacity: 1; }
}
@keyframes slideUp {
  0% { transform: translateY(12px); opacity: 0; }
  100% { transform: translateY(0); opacity: 1; }
}
@keyframes typing {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.animate-float { animation: float 5s ease-in-out infinite; }
.animate-float-slow { animation: float 7s ease-in-out infinite; }
.animate-float-delay { animation: float 6s ease-in-out infinite 1s; }
.animate-slide-up-bounce { animation: slideUpBounce 0.6s ease-out forwards; }
.animate-slide-up { animation: slideUp 0.4s ease; }
.animate-wiggle { animation: wiggle 4s ease-in-out infinite; }
.animate-twinkle { animation: twinkle 3s ease-in-out infinite; }
.animate-drift { animation: drift 8s ease-in-out infinite; }
.animate-pop-in { animation: popIn 0.5s ease-out; }
.animate-typing { animation: typing 1s ease-in-out infinite; }

/* Cute floating illustration for pages */
.page-deco-img {
  position: absolute;
  pointer-events: none;
  z-index: 1;
  object-fit: cover;
  border-radius: 50%;
  border: 3px solid var(--text-dark);
  box-shadow: 3px 3px 0px rgba(45, 52, 54, 0.15);
  opacity: 0.85;
}

@media (max-width: 767px) {
  .page-deco-img { display: none; }
}

/* Hand-drawn card style */
.hand-card {
  background: var(--card-bg);
  border-radius: var(--radius-xl);
  border: 3px solid var(--text-dark);
  box-shadow: 4px 4px 0px var(--text-dark);
  transition: all 0.3s ease;
}

.hand-card:hover {
  box-shadow: 6px 6px 0px var(--text-dark);
  transform: translateY(-4px) rotate(0.3deg);
}

/* Playful card (softer) */
.playful-card {
  background: var(--card-bg);
  border-radius: var(--radius-lg);
  border: 2px solid rgba(45, 52, 54, 0.08);
  box-shadow: 0 4px 20px rgba(45, 52, 54, 0.06), 0 1px 3px rgba(45, 52, 54, 0.04);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}
.playful-card:hover {
  box-shadow: 0 8px 30px rgba(45, 52, 54, 0.1), 0 2px 6px rgba(45, 52, 54, 0.06);
  transform: translateY(-3px);
}

/* Pill buttons */
.btn-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 9999px;
  padding: 10px 24px;
  font-weight: 700;
  font-size: 14px;
  letter-spacing: 0.3px;
  border: 3px solid transparent;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
  text-decoration: none;
}
.btn-pill:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 3px 3px 0px rgba(45, 52, 54, 0.2);
}
.btn-primary-pill {
  background: var(--coral);
  color: white;
  border-color: var(--coral);
}
.btn-primary-pill:hover {
  background: var(--coral-light);
}
.btn-secondary-pill {
  background: var(--cream);
  color: var(--text-dark);
  border-color: rgba(45, 52, 54, 0.15);
}
.btn-secondary-pill:hover {
  background: var(--golden-pale);
  border-color: var(--golden);
}
.btn-ghost-pill {
  background: transparent;
  color: var(--text-medium);
  border-color: rgba(45, 52, 54, 0.15);
}
.btn-ghost-pill:hover {
  background: var(--cream);
  border-color: var(--text-dark);
}

/* Hand-drawn input */
.hand-input {
  width: 100%;
  padding: 12px 16px;
  border-radius: 9999px;
  border: 3px solid var(--text-dark);
  background: #fff;
  font-size: 15px;
  font-family: inherit;
  color: var(--text-dark);
  transition: all 0.2s ease;
  outline: none;
}
.hand-input:focus {
  border-color: var(--coral);
  box-shadow: 4px 4px 0px rgba(255, 107, 107, 0.2);
  transform: translateY(-2px);
}
.hand-input::placeholder {
  color: var(--text-light);
}

/* Icon bubble */
.icon-bubble {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--text-dark);
  transition: all 0.3s ease;
  flex-shrink: 0;
}
.icon-bubble:hover {
  transform: scale(1.1) rotate(-5deg);
}

/* Decorative dots */
.deco-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

/* Decorative corner SVG elements */
.deco-corner {
  position: absolute;
  width: 24px;
  height: 24px;
  opacity: 0.15;
  pointer-events: none;
}
.deco-corner-tl { top: 8px; left: 8px; }
.deco-corner-tr { top: 8px; right: 8px; }
.deco-corner-bl { bottom: 8px; left: 8px; }
.deco-corner-br { bottom: 8px; right: 8px; }

/* Hand tags */
.hand-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 700;
  border: 2px solid transparent;
}
.tag-coral { background: var(--coral-pale); color: var(--coral); border-color: rgba(255,107,107,0.2); }
.tag-teal { background: var(--teal-pale); color: var(--teal-dark); border-color: rgba(78,205,196,0.2); }
.tag-golden { background: var(--golden-pale); color: var(--text-dark); border-color: rgba(255,217,61,0.3); }
.tag-lavender { background: var(--lavender-pale); color: var(--lavender); border-color: rgba(168,164,230,0.2); }
.tag-mint { background: var(--mint-pale); color: #16a34a; border-color: rgba(152,216,200,0.3); }
.tag-error { background: #fef2f2; color: #ef4444; border-color: rgba(239,68,68,0.2); }
.tag-warning { background: #fffbeb; color: #f59e0b; border-color: rgba(245,158,11,0.2); }

/* Activity item */
.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px dashed rgba(45, 52, 54, 0.08);
  transition: all 0.2s ease;
}
.activity-item:last-child { border-bottom: none; }
.activity-item:hover { transform: translateX(4px); }
.activity-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(255,255,255,0.5);
  flex-shrink: 0;
  font-weight: 800;
  font-size: 14px;
  color: white;
}

/* Toggle switch */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 48px;
  height: 26px;
  flex-shrink: 0;
  cursor: pointer;
}
.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}
.toggle-slider {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--text-light);
  border-radius: 999px;
  transition: all 0.3s ease;
}
.toggle-slider:before {
  position: absolute;
  content: "";
  height: 20px;
  width: 20px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  border-radius: 50%;
  transition: all 0.3s ease;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.toggle-switch input:checked + .toggle-slider {
  background-color: var(--coral);
}
.toggle-switch input:checked + .toggle-slider:before {
  transform: translateX(22px);
}
.toggle-switch input:focus-visible + .toggle-slider {
  box-shadow: 0 0 0 3px rgba(255, 107, 107, 0.3);
}

/* Element Plus overrides */
.el-button--primary {
  background: var(--coral) !important;
  border-color: var(--coral) !important;
  border-radius: 9999px !important;
  font-weight: 700 !important;
  box-shadow: 3px 3px 0px rgba(45, 52, 54, 0.15);
}
.el-button--primary:hover,
.el-button--primary:focus {
  background: var(--coral-light) !important;
  border-color: var(--coral-light) !important;
  transform: translateY(-2px);
  box-shadow: 4px 4px 0px rgba(45, 52, 54, 0.2);
}
.el-button--primary:active {
  transform: translateY(0);
}
.el-button {
  font-family: inherit;
}
.el-input__wrapper,
.el-textarea__inner,
.el-select .el-input__wrapper {
  border-radius: 9999px !important;
  border: 3px solid var(--text-dark) !important;
  box-shadow: none !important;
  background: #fff !important;
  font-family: inherit;
}
.el-textarea__inner {
  border-radius: var(--radius-lg) !important;
}
.el-input__wrapper.is-focus,
.el-textarea__inner:focus,
.el-select .el-input.is-focus .el-input__wrapper {
  border-color: var(--coral) !important;
  box-shadow: 4px 4px 0px rgba(255, 107, 107, 0.2) !important;
}
.el-input__inner {
  color: var(--text-dark) !important;
  font-weight: 600 !important;
}
.el-input__inner::placeholder {
  color: var(--text-light) !important;
}
.el-card {
  border-radius: var(--radius-lg) !important;
  border: 2px solid rgba(45, 52, 54, 0.08) !important;
  box-shadow: 0 4px 20px rgba(45, 52, 54, 0.06) !important;
  font-family: inherit;
}
.el-dialog {
  border-radius: var(--radius-xl) !important;
  border: 3px solid var(--text-dark) !important;
  box-shadow: 8px 8px 0px rgba(45, 52, 54, 0.15) !important;
}
.el-dialog__header {
  font-weight: 800 !important;
  color: var(--text-dark) !important;
  padding: 20px 24px !important;
  border-bottom: 2px dashed rgba(45, 52, 54, 0.08);
}
.el-dialog__body {
  padding: 24px !important;
}
.el-form-item__label {
  font-weight: 700 !important;
  color: var(--text-dark) !important;
}
.el-menu {
  border-right: none !important;
}
.el-dropdown-menu {
  border-radius: var(--radius-lg) !important;
  border: 2px solid var(--text-dark) !important;
  box-shadow: 4px 4px 0px rgba(45, 52, 54, 0.15) !important;
  padding: 8px !important;
}
.el-dropdown-menu__item {
  border-radius: var(--radius-md) !important;
  font-weight: 700 !important;
}
.el-tag {
  border-radius: 9999px !important;
  font-weight: 700 !important;
  border-width: 2px !important;
}
.el-upload-dragger {
  border-radius: var(--radius-lg) !important;
  border: 3px dashed var(--text-light) !important;
  background: var(--cream) !important;
}
.el-upload-dragger:hover {
  border-color: var(--coral) !important;
}
.el-table {
  border-radius: var(--radius-lg) !important;
  overflow: hidden !important;
}
.el-table th.el-table__cell {
  background: var(--cream) !important;
  color: var(--text-dark) !important;
  font-weight: 800 !important;
  border-bottom: 1px dashed rgba(45, 52, 54, 0.12) !important;
}
.el-table td.el-table__cell {
  border-bottom: 1px dashed rgba(45, 52, 54, 0.08) !important;
}
.el-empty__description {
  color: var(--text-medium) !important;
  font-weight: 600 !important;
}

/* Responsive helpers */
@media (max-width: 767px) {
  .hand-input { font-size: 16px; }
}
</style>
