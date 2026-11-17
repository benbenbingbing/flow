package com.workflow.admin.identity.group.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroupTag;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 组内标签和成员标签关联；所有写操作由服务在用户组行锁下执行。 */
@Mapper
public interface SysGroupTagMapper extends BaseMapper<SysGroupTag> {
    @Select("SELECT ug.user_id, ut.tag_id FROM sys_user_group ug JOIN sys_user_group_tag ut ON ut.user_group_id = ug.id WHERE ug.group_id = #{groupId}")
    List<MemberTagRow> memberTags(@Param("groupId") String groupId);

    @Delete("DELETE FROM sys_user_group_tag WHERE user_group_id = #{memberId}")
    void clearMember(@Param("memberId") String memberId);

    @Delete("DELETE FROM sys_user_group_tag WHERE tag_id = #{tagId}")
    void clearTag(@Param("tagId") String tagId);

    @Insert("INSERT INTO sys_user_group_tag (user_group_id, tag_id) VALUES (#{memberId}, #{tagId})")
    void bind(@Param("memberId") String memberId, @Param("tagId") String tagId);

    /** EXISTS 避免一个成员命中多个标签后重复返回，空标签由调用方显式传空列表。 */
    @Select("""
        <script>
        SELECT u.username, u.nickname FROM sys_user_group ug
        JOIN sys_user u ON u.id = ug.user_id
        JOIN sys_group g ON g.id = ug.group_id AND g.deleted = 0 AND g.status = '0'
        WHERE ug.group_id = #{groupId} AND u.deleted = 0 AND u.status = '0'
        <if test="tagIds.size() > 0">
          AND EXISTS (SELECT 1 FROM sys_user_group_tag ut
            WHERE ut.user_group_id = ug.id AND ut.tag_id IN
            <foreach collection="tagIds" item="tag" open="(" separator="," close=")">#{tag}</foreach>)
        </if>
        ORDER BY u.username, u.id
        </script>
        """)
    List<MemberPreviewRow> resolveMembers(@Param("groupId") String groupId, @Param("tagIds") List<String> tagIds);

    /** 同次范围查询带出昵称，预览无需逐人补查；流程运行仍只使用登录名。 */
    @lombok.Data
    class MemberPreviewRow {
        private String username;
        private String nickname;
    }

    @lombok.Data
    class MemberTagRow {
        private String userId;
        private String tagId;
    }
}
