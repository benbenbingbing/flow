<template>
  <div class="manual-page">
    <header class="manual-hero">
      <div class="manual-hero__copy">
        <div class="manual-eyebrow">{{ manual.eyebrow || '用户手册' }}</div>
        <h1>{{ manual.title }}</h1>
        <p>{{ manual.subtitle }}</p>
        <div class="manual-meta">
          <el-tag v-if="manual.version" effect="plain">{{ manual.version }}</el-tag>
          <span v-if="manual.updatedAt">内容基线：{{ manual.updatedAt }}</span>
          <span>{{ totalTopicCount }} 个主题</span>
        </div>
      </div>
      <div class="manual-search">
        <el-input
          v-model="keyword"
          size="large"
          clearable
          placeholder="搜索功能、字段、选项、默认值或发布注意事项"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="manual-search__tip">
          <span v-if="keyword">找到 {{ visibleTopicCount }} 个相关主题</span>
          <span v-else>支持关键词过滤目录与正文</span>
        </div>
      </div>
    </header>

    <div v-if="manual.intro?.length" class="manual-intro">
      <el-alert
        v-for="item in manual.intro"
        :key="item.title"
        :title="item.title"
        :type="item.type || 'info'"
        :closable="false"
        show-icon
      >
        <template #default>
          <div class="manual-alert-text">{{ item.text }}</div>
        </template>
      </el-alert>
    </div>

    <div class="manual-mobile-actions">
      <el-button @click="drawerVisible = true">
        <el-icon><Menu /></el-icon>
        分组目录
      </el-button>
    </div>

    <div class="manual-layout">
      <aside class="manual-sidebar">
        <ManualToc
          :sections="visibleSections"
          :active-id="activeId"
          @navigate="scrollToAnchor"
        />
      </aside>

      <main class="manual-content">
        <el-empty
          v-if="visibleSections.length === 0"
          description="没有匹配的手册内容，请更换关键词"
        />

        <section
          v-for="section in visibleSections"
          :id="section.id"
          :key="section.id"
          class="manual-section"
        >
          <div class="manual-section__heading">
            <div>
              <span class="manual-section__index">{{ section.index }}</span>
              <h2>{{ section.title }}</h2>
            </div>
            <p v-if="section.summary">{{ section.summary }}</p>
          </div>

          <article
            v-for="topic in section.topics"
            :id="topic.id"
            :key="topic.id"
            class="manual-topic"
            :data-manual-anchor="topic.id"
          >
            <div class="manual-topic__heading">
              <h3>{{ topic.title }}</h3>
              <button type="button" class="anchor-button" @click="copyAnchor(topic.id)">
                #
              </button>
            </div>
            <p v-if="topic.lead" class="manual-topic__lead">{{ topic.lead }}</p>

            <template v-for="(block, blockIndex) in topic.blocks || []" :key="blockIndex">
              <p v-if="block.type === 'paragraph'" class="manual-paragraph">{{ block.text }}</p>

              <el-alert
                v-else-if="block.type === 'callout'"
                :title="block.title"
                :type="block.tone || 'info'"
                :closable="false"
                show-icon
                class="manual-callout"
              >
                <template #default>
                  <div class="manual-alert-text">{{ block.text }}</div>
                </template>
              </el-alert>

              <ul v-else-if="block.type === 'bullets'" class="manual-bullets">
                <li v-for="item in block.items" :key="item">{{ item }}</li>
              </ul>

              <ol v-else-if="block.type === 'steps'" class="manual-steps">
                <li v-for="item in block.items" :key="item.title">
                  <span class="manual-step__title">{{ item.title }}</span>
                  <span>{{ item.text }}</span>
                </li>
              </ol>

              <div v-else-if="block.type === 'checklist'" class="manual-checklist">
                <div v-for="item in block.items" :key="item" class="manual-checklist__item">
                  <span class="manual-checklist__mark">✓</span>
                  <span>{{ item }}</span>
                </div>
              </div>

              <div v-else-if="block.type === 'table'" class="manual-table-wrap">
                <div v-if="block.title" class="manual-table-title">{{ block.title }}</div>
                <table class="manual-table">
                  <thead>
                    <tr>
                      <th v-for="column in block.columns" :key="column.key">
                        {{ column.label }}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                      <td v-for="column in block.columns" :key="column.key">
                        {{ row[column.key] ?? '-' }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
          </article>
        </section>
      </main>
    </div>

    <el-drawer v-model="drawerVisible" title="分组目录" direction="ltr" size="84%">
      <ManualToc
        :sections="visibleSections"
        :active-id="activeId"
        @navigate="handleMobileNavigate"
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Menu, Search } from '@element-plus/icons-vue'

const props = defineProps({
  manual: {
    type: Object,
    required: true
  }
})

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const activeId = ref('')
const drawerVisible = ref(false)
let observer = null

const ManualToc = defineComponent({
  name: 'ManualToc',
  props: {
    sections: { type: Array, default: () => [] },
    activeId: { type: String, default: '' }
  },
  emits: ['navigate'],
  setup(tocProps, { emit }) {
    return () => h('nav', { class: 'manual-toc', 'aria-label': '用户手册目录' }, [
      h('div', { class: 'manual-toc__title' }, '分组目录'),
      ...tocProps.sections.map(section => h('div', { class: 'manual-toc__group', key: section.id }, [
        h('button', {
          type: 'button',
          class: 'manual-toc__section',
          onClick: () => emit('navigate', section.id)
        }, `${section.index} ${section.title}`),
        h('div', { class: 'manual-toc__topics' }, section.topics.map(topic => h('button', {
          type: 'button',
          class: ['manual-toc__topic', { active: tocProps.activeId === topic.id }],
          onClick: () => emit('navigate', topic.id)
        }, topic.title)))
      ]))
    ])
  }
})

const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())

const visibleSections = computed(() => {
  if (!normalizedKeyword.value) return props.manual.sections || []
  const term = normalizedKeyword.value
  return (props.manual.sections || []).reduce((result, section) => {
    const sectionMatched = `${section.title} ${section.summary || ''}`.toLowerCase().includes(term)
    const topics = sectionMatched
      ? section.topics
      : section.topics.filter(topic => JSON.stringify(topic).toLowerCase().includes(term))
    if (topics.length) result.push({ ...section, topics })
    return result
  }, [])
})

const totalTopicCount = computed(() =>
  (props.manual.sections || []).reduce((count, section) => count + section.topics.length, 0)
)

const visibleTopicCount = computed(() =>
  visibleSections.value.reduce((count, section) => count + section.topics.length, 0)
)

function scrollToAnchor(id) {
  const target = document.getElementById(id)
  if (!target) return
  activeId.value = id
  router.replace({ path: route.path, query: route.query, hash: `#${id}` })
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function handleMobileNavigate(id) {
  drawerVisible.value = false
  nextTick(() => scrollToAnchor(id))
}

async function copyAnchor(id) {
  const url = `${window.location.origin}${route.path}#${id}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('锚点链接已复制')
  } catch {
    scrollToAnchor(id)
  }
}

function setupObserver() {
  observer?.disconnect()
  const elements = [...document.querySelectorAll('[data-manual-anchor]')]
  if (!elements.length) return
  observer = new IntersectionObserver(
    entries => {
      const visible = entries
        .filter(entry => entry.isIntersecting)
        .sort((left, right) => left.boundingClientRect.top - right.boundingClientRect.top)
      if (visible[0]?.target?.id) activeId.value = visible[0].target.id
    },
    { rootMargin: '-90px 0px -68% 0px', threshold: [0, 0.1, 0.5] }
  )
  elements.forEach(element => observer.observe(element))
}

watch(visibleSections, () => {
  nextTick(setupObserver)
}, { deep: true })

onMounted(() => {
  nextTick(() => {
    setupObserver()
    const hashId = route.hash.replace(/^#/, '')
    if (hashId) {
      document.getElementById(hashId)?.scrollIntoView({ block: 'start' })
      activeId.value = hashId
    } else {
      activeId.value = visibleSections.value[0]?.topics?.[0]?.id || ''
    }
  })
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<style>
.manual-page {
  min-height: 100%;
  padding: 18px;
  color: #303133;
}

.manual-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.8fr);
  gap: 28px;
  align-items: center;
  padding: 34px 38px;
  overflow: hidden;
  border: 1px solid rgb(64 158 255 / 18%);
  border-radius: 18px;
  background:
    radial-gradient(circle at 88% 18%, rgb(103 194 58 / 16%), transparent 32%),
    radial-gradient(circle at 8% 4%, rgb(64 158 255 / 18%), transparent 38%),
    linear-gradient(135deg, #fff 0%, #f7fbff 55%, #f9fff7 100%);
  box-shadow: 0 14px 38px rgb(31 45 61 / 8%);
}

.manual-eyebrow {
  margin-bottom: 8px;
  color: #409eff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.manual-hero h1 {
  margin: 0;
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.15;
}

.manual-hero p {
  max-width: 760px;
  margin: 14px 0 0;
  color: #606266;
  font-size: 15px;
  line-height: 1.9;
}

.manual-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 18px;
  color: #909399;
  font-size: 12px;
}

.manual-search {
  padding: 22px;
  border: 1px solid rgb(64 158 255 / 14%);
  border-radius: 14px;
  background: rgb(255 255 255 / 78%);
  box-shadow: 0 10px 24px rgb(64 158 255 / 8%);
  backdrop-filter: blur(10px);
}

.manual-search__tip {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
}

.manual-intro {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  margin: 16px 0;
}

.manual-alert-text {
  white-space: pre-line;
  line-height: 1.75;
}

.manual-layout {
  display: grid;
  grid-template-columns: 268px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.manual-sidebar {
  position: sticky;
  top: 12px;
  max-height: calc(100vh - 24px);
  overflow-y: auto;
}

.manual-toc {
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(31 45 61 / 5%);
}

.manual-toc__title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 700;
}

.manual-toc__group + .manual-toc__group {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}

.manual-toc__section,
.manual-toc__topic {
  display: block;
  width: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.manual-toc__section {
  padding: 4px 6px;
  font-weight: 650;
}

.manual-toc__topics {
  margin-top: 4px;
}

.manual-toc__topic {
  padding: 6px 8px 6px 18px;
  border-radius: 6px;
  color: #606266;
  font-size: 12px;
  line-height: 1.45;
}

.manual-toc__topic:hover,
.manual-toc__topic.active {
  background: #ecf5ff;
  color: #409eff;
}

.manual-content {
  min-width: 0;
}

.manual-section + .manual-section {
  margin-top: 22px;
}

.manual-section__heading {
  padding: 8px 4px 14px;
}

.manual-section__heading > div {
  display: flex;
  gap: 10px;
  align-items: center;
}

.manual-section__heading h2 {
  margin: 0;
  font-size: 24px;
}

.manual-section__heading p {
  margin: 8px 0 0 44px;
  color: #909399;
  line-height: 1.7;
}

.manual-section__index {
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: linear-gradient(135deg, #409eff, #53a8ff);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 6px 14px rgb(64 158 255 / 24%);
}

.manual-topic {
  scroll-margin-top: 18px;
  padding: 24px;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(31 45 61 / 5%);
}

.manual-topic + .manual-topic {
  margin-top: 14px;
}

.manual-topic__heading {
  display: flex;
  gap: 8px;
  align-items: center;
}

.manual-topic h3 {
  margin: 0;
  font-size: 19px;
}

.anchor-button {
  border: 0;
  background: transparent;
  color: #c0c4cc;
  cursor: pointer;
  font-size: 18px;
}

.anchor-button:hover {
  color: #409eff;
}

.manual-topic__lead,
.manual-paragraph {
  color: #606266;
  line-height: 1.85;
  white-space: pre-line;
}

.manual-topic__lead {
  margin: 10px 0 18px;
}

.manual-paragraph {
  margin: 14px 0;
}

.manual-callout {
  margin: 14px 0;
}

.manual-bullets {
  margin: 14px 0;
  padding-left: 22px;
  color: #606266;
  line-height: 1.85;
}

.manual-bullets li + li {
  margin-top: 5px;
}

.manual-steps {
  display: grid;
  gap: 10px;
  margin: 14px 0;
  padding: 0;
  list-style: none;
  counter-reset: manual-step;
}

.manual-steps li {
  position: relative;
  min-height: 34px;
  padding: 10px 12px 10px 48px;
  border-radius: 8px;
  background: #f7f9fc;
  color: #606266;
  line-height: 1.7;
  counter-increment: manual-step;
}

.manual-steps li::before {
  position: absolute;
  top: 10px;
  left: 12px;
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  content: counter(manual-step);
  font-size: 12px;
  font-weight: 700;
}

.manual-step__title {
  display: block;
  color: #303133;
  font-weight: 650;
}

.manual-checklist {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 9px;
  margin: 14px 0;
}

.manual-checklist__item {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #e1f3d8;
  border-radius: 8px;
  background: #f0f9eb;
  color: #529b2e;
  line-height: 1.55;
}

.manual-checklist__mark {
  font-weight: 800;
}

.manual-table-wrap {
  margin: 16px 0;
  overflow-x: auto;
  border: 1px solid #ebeef5;
  border-radius: 9px;
}

.manual-table-title {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #f5f7fa;
  font-weight: 650;
}

.manual-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  font-size: 13px;
}

.manual-table th,
.manual-table td {
  padding: 11px 12px;
  border-bottom: 1px solid #ebeef5;
  border-right: 1px solid #ebeef5;
  text-align: left;
  vertical-align: top;
  white-space: pre-line;
  line-height: 1.65;
}

.manual-table th {
  background: #f5f7fa;
  color: #303133;
  font-weight: 650;
}

.manual-table td {
  color: #606266;
}

.manual-table tr:last-child td {
  border-bottom: 0;
}

.manual-table th:last-child,
.manual-table td:last-child {
  border-right: 0;
}

.manual-mobile-actions {
  display: none;
  margin: 14px 0;
}

@media (max-width: 1100px) {
  .manual-hero {
    grid-template-columns: 1fr;
  }

  .manual-layout {
    grid-template-columns: 230px minmax(0, 1fr);
  }
}

@media (max-width: 820px) {
  .manual-page {
    padding: 10px;
  }

  .manual-hero {
    padding: 24px 20px;
    border-radius: 14px;
  }

  .manual-layout {
    display: block;
  }

  .manual-sidebar {
    display: none;
  }

  .manual-mobile-actions {
    display: flex;
  }

  .manual-topic {
    padding: 18px 16px;
  }

  .manual-section__heading p {
    margin-left: 0;
  }
}

@media (max-width: 520px) {
  .manual-search {
    padding: 14px;
  }

  .manual-meta {
    align-items: flex-start;
    flex-direction: column;
  }

  .manual-topic h3 {
    font-size: 17px;
  }
}
</style>
