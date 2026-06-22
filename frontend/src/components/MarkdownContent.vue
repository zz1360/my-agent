<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

const props = defineProps<{ content: string }>()

const markdown = new MarkdownIt({
  breaks: true,
  linkify: true,
  typographer: false,
})

const html = computed(() => DOMPurify.sanitize(markdown.render(props.content || '')))
</script>

<template>
  <div class="markdown-body" v-html="html" />
</template>

<style scoped>
.markdown-body {
  overflow-wrap: anywhere;
  color: var(--ink);
  line-height: 1.72;
}

.markdown-body :deep(p) {
  margin: 0 0 10px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 18px 0 8px;
  font-size: 15px;
  line-height: 1.4;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0;
  padding-left: 22px;
}

.markdown-body :deep(code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: #edf1f5;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
}

.markdown-body :deep(pre) {
  overflow-x: auto;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #18212a;
  color: #f7fafc;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-body :deep(table) {
  width: 100%;
  margin: 10px 0;
  border-collapse: collapse;
  font-size: 13px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 7px 9px;
  border: 1px solid var(--line);
  text-align: left;
}

.markdown-body :deep(a) {
  color: var(--blue);
  text-decoration: underline;
}
</style>
