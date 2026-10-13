package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.schema.SchemaType;
import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/** 结构发布前读取存量冲突；物理名称仅由已验证的实体元数据提供。 */
@Mapper
public interface EntityUniqueConflictMapper {
    /** 完整比较大文本，每个字段最多五组；不截断 LOB 或改变数据库原有比较规则。 */
    @SelectProvider(type=Sql.class, method="scan")
    @Results({@Result(column="value_text", property="valueText", javaType=String.class),
            @Result(column="duplicate_count", property="duplicateCount", javaType=long.class)})
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<Conflict> scan(@Param("table") String table, @Param("column") String column,
                        @Param("kind") SchemaType.Kind kind, @Param("dialect") DatabaseQueryDialect dialect);

    @Data
    class Conflict { private String valueText; private long duplicateCount; }

    class Sql {
        /** Provider 只插入经方言严格校验的标识符，其他条件均为固定模板。 */
        public static String scan(Map<String,Object> args) {
            var dialect = (DatabaseQueryDialect) args.get("dialect");
            String physicalTable=(String)args.get("table"), physicalColumn=(String)args.get("column");
            String table=dialect.quoteIdentifier(physicalTable), column=dialect.quoteIdentifier(physicalColumn);
            var kind=(SchemaType.Kind)args.get("kind");
            if (kind != SchemaType.Kind.TEXT && kind != SchemaType.Kind.LARGE_TEXT) {
                return dialect.paginate("SELECT "+column+" value_text,COUNT(*) duplicate_count FROM "+table
                        +" WHERE "+dialect.quoteIdentifier("deleted")+"=0 AND "+column+" IS NOT NULL GROUP BY "+column
                        +" HAVING COUNT(*)>1 ORDER BY "+column,"0","5");
            }
            // Oracle 系不能 GROUP BY LOB；选择每组最小 ID 的代表行，同时保留完整字符串比较。
            String candidate=dialect.quoteIdentifier("candidate"), duplicate=dialect.quoteIdentifier("duplicate_row"),
                    earlier=dialect.quoteIdentifier("earlier_row"), id=dialect.quoteIdentifier("id"), deleted=dialect.quoteIdentifier("deleted");
            String duplicates=" FROM "+table+" "+duplicate+" WHERE "+duplicate+"."+deleted+" = 0 AND "
                    +dialect.columnComparisonPredicate("duplicate_row."+physicalColumn,kind,"=","candidate."+physicalColumn);
            String sql="SELECT "+candidate+"."+column+" value_text,(SELECT COUNT(*)"+duplicates+") duplicate_count FROM "+table+" "+candidate
                    +" WHERE "+candidate+"."+deleted+" = 0 AND "+candidate+"."+column+" IS NOT NULL"
                    +" AND EXISTS (SELECT 1"+duplicates+" AND "+duplicate+"."+id+" <> "+candidate+"."+id+")"
                    +" AND NOT EXISTS (SELECT 1 FROM "+table+" "+earlier+" WHERE "+earlier+"."+deleted+" = 0 AND "+earlier+"."+id+" < "+candidate+"."+id
                    +" AND "+dialect.columnComparisonPredicate("earlier_row."+physicalColumn,kind,"=","candidate."+physicalColumn)+")"
                    +" ORDER BY "+candidate+"."+id;
            return dialect.paginate(sql,"0","5");
        }
    }
}
