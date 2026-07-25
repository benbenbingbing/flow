<template>
  <div class="legacy-data-redirect">
    <PageState
      v-if="errorMessage"
      type="error"
      title="无法打开业务数据"
      :description="errorMessage"
      retryable
      @retry="resolveDefaultList"
    />
    <el-result
      v-else
      icon="info"
      title="正在打开统一业务列表"
      sub-title="旧数据管理入口已合并，正在定位该实体的默认已发布列表。"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { entityApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'
import PageState from '@/components/PageState.vue'

const route = useRoute()
const router = useRouter()
const errorMessage = ref('')

async function resolveDefaultList() {
  errorMessage.value = ''
  try {
    const entity = await entityApi.getByCode(String(route.params.code || ''))
    if (!entity?.id) {
      errorMessage.value = '实体不存在、尚未发布，或当前账号没有访问权限。'
      return
    }
    const configs = await entityListConfigApi.getByEntityId(entity.id)
    const available = (configs || []).filter(item => item?.listKey)
    const target = available.find(item => item.isDefault) || available[0]
    if (!target) {
      errorMessage.value = '该实体还没有可用列表。请联系配置人员创建并发布默认列表。'
      return
    }
    await router.replace({
      name: 'EntityListRuntime',
      params: {
        entityCode: entity.entityCode,
        listKey: target.listKey
      },
      query: { migratedFrom: 'legacy-data' }
    })
  } catch (error) {
    errorMessage.value = error?.message || '定位默认列表失败，请重试。'
  }
}

onMounted(resolveDefaultList)
</script>

<style scoped>
.legacy-data-redirect {
  min-height: 360px;
  padding: 20px;
}
</style>
