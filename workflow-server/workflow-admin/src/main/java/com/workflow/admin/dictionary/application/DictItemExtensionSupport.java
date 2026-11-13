package com.workflow.admin.dictionary.application;

import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;

/** 字典扩展值只做存储长度检查，所有写入入口共用，避免配置导入绕过限制。 */
public final class DictItemExtensionSupport {
    public static final int MAX_LENGTH = 500;

    private DictItemExtensionSupport() { }

    /**
     * 校验五个可选文本字段；保留空白和空字符串，由定制模块自行解释内容。
     *
     * @param item 待新增、更新或迁移导入的字典项
     * @throws IllegalArgumentException 任一扩展值超过 500 个字符时拒绝写入
     */
    public static void validate(SysDictItem item) {
        String[] values = {item.getExt1(), item.getExt2(), item.getExt3(), item.getExt4(), item.getExt5()};
        for (int index = 0; index < values.length; index++) {
            if (values[index] != null && values[index].length() > MAX_LENGTH) {
                throw new IllegalArgumentException("扩展字段" + (index + 1) + "不能超过" + MAX_LENGTH + "个字符");
            }
        }
    }
}
