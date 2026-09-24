<template>
  <ConversationChat app-type="MANUS" title="校园问答" :assistant-avatar="campusAvatar" />
</template>

<script setup lang="ts">
import ConversationChat from '../components/ConversationChat.vue'
import campusAvatar from '../assets/avatar-campus.svg'
</script>
