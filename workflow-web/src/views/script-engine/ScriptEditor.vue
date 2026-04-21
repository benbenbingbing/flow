<template>
  <div class="script-editor">
    <div class="page-header">
      <h2>脚本规则引擎</h2>
      <el-button type="primary" @click="handleExecute">
        <el-icon><VideoPlay /></el-icon>运行脚本
      </el-button>
    </div>
    
    <el-row :gutter="20" class="editor-container">
      <!-- 左侧：脚本编辑区 -->
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>Groovy 脚本编辑器</span>
              <div class="header-actions">
                <el-select v-model="selectedTemplate" placeholder="选择模板" size="small" @change="handleLoadTemplate">
                  <el-option label="基础模板" value="basic" />
                  <el-option label="计算模板" value="calculation" />
                  <el-option label="条件模板" value="condition" />
                  <el-option label="循环模板" value="loop" />
                </el-select>
                <el-button type="primary" link size="small" @click="handleValidate">
                  验证语法
                </el-button>
              </div>
            </div>
          </template>
          
          <el-input
            v-model="scriptContent"
            type="textarea"
            :rows="20"
            placeholder="在此输入Groovy脚本..."
            class="script-textarea"
          />
          
          <div class="editor-tips">
            <p>提示：</p>
            <ul>
              <li>使用 <code>return</code> 返回结果</li>
              <li>可使用 <code>_util</code> 工具类进行日期、字符串操作</li>
              <li>使用 <code>_log</code> 输出日志</li>
              <li>上下文变量可直接使用</li>
            </ul>
          </div>
        </el-card>
      </el-col>
      
      <!-- 右侧：上下文和结果 -->
      <el-col :span="10">
        <el-card class="context-card">
          <template #header>
            <span>上下文变量</span>
          </template>
          <el-input
            v-model="contextContent"
            type="textarea"
            :rows="6"
            placeholder='{"变量名": "值"}'
          />
        </el-card>
        
        <el-card class="result-card">
          <template #header>
            <span>执行结果</span>
          </template>
          
          <div v-if="!result" class="empty-result">
            <el-icon size="48" color="#dcdfe6"><VideoPlay /></el-icon>
            <p>点击"运行脚本"查看结果</p>
          </div>
          
          <div v-else class="result-content" :class="result.success ? 'success' : 'error'">
            <div class="result-status">
              <el-icon v-if="result.success" size="24" color="#67c23a"><CircleCheck /></el-icon>
              <el-icon v-else size="24" color="#f56c6c"><CircleClose /></el-icon>
              <span>{{ result.success ? '执行成功' : '执行失败' }}</span>
            </div>
            
            <div class="result-info">
              <p>执行耗时: {{ result.durationMs }}ms</p>
              <p v-if="!result.success" class="error-message">错误: {{ result.errorMessage }}</p>
            </div>
            
            <div v-if="result.success" class="result-data">
              <pre>{{ JSON.stringify(result.result, null, 2) }}</pre>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { VideoPlay, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { executeScript, validateScript, getScriptTemplates } from '@/api/script-engine'

const scriptContent = ref(`// Groovy 脚本示例
// 可用变量: _util (工具类), _log (日志)

def name = 'World'
def message = "Hello, ${name}!"

_log.info("执行脚本: " + message)

// 返回结果
return [
    message: message,
    timestamp: _util.formatDate(new Date(), 'yyyy-MM-dd HH:mm:ss')
]`)

const contextContent = ref('{}')
const selectedTemplate = ref('')
const result = ref(null)
const templates = ref({})

// 加载模板
const loadTemplates = async () => {
  try {
    const res = await getScriptTemplates()
    templates.value = res || {}
  } catch (error) {
    console.error('加载模板失败:', error)
  }
}

// 加载模板
const handleLoadTemplate = () => {
  const template = templates.value[selectedTemplate.value]
  if (template) {
    scriptContent.value = template
  }
}

// 验证脚本
const handleValidate = async () => {
  try {
    const res = await validateScript({ script: scriptContent.value })
    if (res) {
      ElMessage.success('脚本语法正确')
    } else {
      ElMessage.error('脚本语法有误')
    }
  } catch (error) {
    ElMessage.error('验证失败')
  }
}

// 执行脚本
const handleExecute = async () => {
  try {
    let context = {}
    try {
      context = JSON.parse(contextContent.value || '{}')
    } catch {
      ElMessage.warning('上下文变量JSON格式不正确，使用空对象')
    }
    
    const res = await executeScript({
      script: scriptContent.value,
      context: context,
      timeoutMs: 5000
    })
    
    result.value = res
    
    if (res.success) {
      ElMessage.success('执行成功')
    } else {
      ElMessage.error(res.errorMessage || '执行失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '执行失败')
  }
}

onMounted(() => {
  loadTemplates()
})
</script>

<style scoped lang="scss">
.script-editor {
  padding: 20px;
  
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h2 {
      margin: 0;
    }
  }
  
  .editor-container {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      
      .header-actions {
        display: flex;
        gap: 10px;
      }
    }
    
    .script-textarea {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 14px;
      
      :deep(textarea) {
        font-family: inherit;
      }
    }
    
    .editor-tips {
      margin-top: 15px;
      padding: 10px;
      background: #f5f7fa;
      border-radius: 4px;
      font-size: 13px;
      color: #606266;
      
      p {
        margin: 0 0 5px 0;
        font-weight: bold;
      }
      
      ul {
        margin: 0;
        padding-left: 20px;
      }
      
      code {
        background: #e4e7ed;
        padding: 2px 6px;
        border-radius: 3px;
        font-family: monospace;
      }
    }
    
    .context-card {
      margin-bottom: 20px;
    }
    
    .result-card {
      .empty-result {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 40px;
        color: #909399;
        
        p {
          margin-top: 10px;
        }
      }
      
      .result-content {
        &.success {
          .result-status {
            color: #67c23a;
          }
        }
        
        &.error {
          .result-status {
            color: #f56c6c;
          }
        }
        
        .result-status {
          display: flex;
          align-items: center;
          gap: 10px;
          margin-bottom: 15px;
          font-weight: bold;
        }
        
        .result-info {
          margin-bottom: 15px;
          
          p {
            margin: 5px 0;
          }
          
          .error-message {
            color: #f56c6c;
          }
        }
        
        .result-data {
          pre {
            background: #f5f7fa;
            padding: 15px;
            border-radius: 4px;
            overflow-x: auto;
            margin: 0;
          }
        }
      }
    }
  }
}
</style>
