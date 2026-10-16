<template>
  <router-view />
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
}

button:focus-visible,
[role='button']:focus-visible,
a:focus-visible,
.el-input__wrapper:has(input:focus-visible),
.el-select__wrapper:has(input:focus-visible) {
  outline: 2px solid #409eff;
  outline-offset: 2px;
}

.migration-steps .el-step.is-simple .el-step__title {
  max-width: none;
  white-space: nowrap;
}

.table-row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  column-gap: 4px;
}

.table-row-actions .el-button + .el-button {
  margin-left: 0;
}

@media (max-width: 760px) {
  .el-table .el-table-fixed-column--right,
  .el-table .el-table-fixed-column--left {
    position: static !important;
    right: auto !important;
    left: auto !important;
  }

  .el-card {
    border-right: 0;
    border-left: 0;
    border-radius: 0;
  }

  .el-table {
    width: 100%;
  }

  .el-pagination {
    flex-wrap: wrap;
    justify-content: flex-start !important;
    gap: 8px 0;
  }

  .el-pagination .el-pagination__sizes,
  .el-pagination .el-pagination__jump {
    display: none;
  }
}
</style>
