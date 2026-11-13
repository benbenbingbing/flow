package com.workflow.admin.dictionary.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.infrastructure.adapter.DictionaryMigrationAdapter;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDict;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 用实际初始化表结构和 MyBatis 验证持久化、缓存、旧客户端更新与跨环境导入。 */
class DictionaryItemExtensionsTest {
    private JdbcTemplate jdbc;
    private SysDictItemService items;
    private SysDictService dictionaries;
    private DictCacheService cache;
    private DictionaryMigrationAdapter migration;
    private TransactionTemplate transactions;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private SysDict dictionary;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:dict_ext_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        String script = Files.readString(Path.of("../../docs/sql/mysql.sql"));
        for (String table : List.of("sys_dict", "sys_dict_item")) {
            var matcher = Pattern.compile("CREATE TABLE `" + table + "` \\([\\s\\S]*?\\n\\)").matcher(script);
            assertTrue(matcher.find());
            // H2 执行实际列定义，仅去除 MySQL 排序规则；索引、可空性和默认值均保留。
            jdbc.execute(matcher.group().replaceAll("COLLATE \\w+", ""));
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SysDictMapper.class);
        config.addMapper(SysDictItemMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        var itemMapper = session.getMapper(SysDictItemMapper.class);
        var dictMapper = session.getMapper(SysDictMapper.class);
        cache = new DictCacheService(itemMapper);
        items = new SysDictItemService(itemMapper, cache);
        dictionaries = new SysDictService(dictMapper, itemMapper, cache);
        migration = new DictionaryMigrationAdapter(dictMapper, itemMapper, cache, json);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        dictionary = dictionaries.saveDict(dictionary("expenses"));
    }

    @AfterEach
    void cleanup() { jdbc.execute("SHUTDOWN"); }

    @Test
    void savesAllFiveTextsAndReturnsThemInTreeJsonAndCache() throws Exception {
        SysDictItem parent = item("traffic");
        parent.setExt1("财".repeat(500));
        parent.setExt2("  外部编码  ");
        parent.setExt3("0");
        parent.setExt4("false");
        parent.setExt5("{\"自定义\":\"任意文本\"}");
        items.saveItem(parent);
        SysDictItem child = item("child");
        child.setParentId(parent.getId());
        child.setExt1("子项扩展");
        items.saveItem(child);
        assertEquals(extensions(parent), extensions(items.getById(parent.getId())));
        assertEquals(extensions(parent), extensions(cache.getItem("expenses", "traffic-value")));
        assertEquals(extensions(parent), extensions(cache.getItemByCode("expenses", "traffic")));
        var tree = items.getItemTreeByDictCode("expenses");
        assertEquals(1, tree.size());
        assertEquals("子项扩展", tree.get(0).getChildren().get(0).getExt1());
        var response = json.readTree(json.writeValueAsString(tree));
        assertEquals(parent.getExt1(), response.get(0).get("ext1").asText());
        assertEquals(parent.getExt5(), response.get(0).get("ext5").asText());
        assertEquals("traffic", tree.get(0).getItemCode());
        assertEquals("traffic-value", tree.get(0).getItemValue());
    }

    @Test
    void omittedOrNullFieldsPreserveValuesAndEmptyStringExplicitlyClears() {
        SysDictItem saved = item("traffic");
        saved.setExt1("old"); saved.setExt2("keep");
        items.saveItem(saved);
        var legacy = new SysDictItem();
        legacy.setId(saved.getId()); legacy.setItemLabel("新标签");
        items.saveItem(legacy);
        assertEquals("old", items.getById(saved.getId()).getExt1());
        legacy.setExt1("");
        items.saveItem(legacy);
        assertEquals("", items.getById(saved.getId()).getExt1());
        assertEquals("keep", items.getById(saved.getId()).getExt2());
        items.updateStatus(saved.getId(), "1");
        assertEquals("keep", items.getById(saved.getId()).getExt2());
    }

    @Test
    void quickCreateAndPortableSnapshotImportPreserveValuesAndLegacyPackages() {
        SysDictItem source = item("hotel");
        source.setExt1("FIN-01"); source.setExt2("500"); source.setExt3("true");
        source.setExt4("说明"); source.setExt5("  ");
        SysDict created = dictionaries.createWithItems(dictionary("quick"), List.of(source));
        assertEquals(extensions(source), extensions(items.getItemTreeByDictId(created.getId()).get(0)));

        // 导出协议按实体序列化全部属性；导入时不能沿用源库主键和 parentId。
        Map<String, Object> portable = json.convertValue(source, new TypeReference<LinkedHashMap<String, Object>>() { });
        portable.remove("id"); portable.remove("dictId"); portable.remove("parentId");
        Map<String, Object> snapshot = Map.of("definition", Map.of("dictCode", "expenses", "dictName", "费用"),
                "items", List.of(portable));
        transactions.executeWithoutResult(status -> migration.apply("expenses", snapshot));
        migration.refreshCache();
        SysDictItem target = items.getItemTreeByDictId(dictionary.getId()).get(0);
        assertNotEquals(source.getId(), target.getId());
        assertEquals(extensions(source), extensions(target));
        assertEquals(extensions(source), extensions(cache.getItemByCode("expenses", "hotel")));
        for (int index = 1; index <= 5; index++) portable.remove("ext" + index);
        transactions.executeWithoutResult(status -> migration.apply("expenses", snapshot));
        assertEquals(extensions(source), extensions(items.getById(target.getId())));
        portable.put("ext1", "");
        transactions.executeWithoutResult(status -> migration.apply("expenses", snapshot));
        assertEquals("", items.getById(target.getId()).getExt1());
        assertEquals("500", items.getById(target.getId()).getExt2());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void everyWriteEntryRejectsOverlongValuesBeforeTheyCanBeTruncated(int index) {
        SysDictItem invalid = item("invalid");
        org.springframework.beans.PropertyAccessorFactory.forBeanPropertyAccess(invalid)
                .setPropertyValue("ext" + index, "超".repeat(501));
        assertThrows(IllegalArgumentException.class, () -> items.saveItem(invalid));
        assertThrows(IllegalArgumentException.class, () -> dictionaries.createWithItems(
                dictionary("invalid"), List.of(item("valid"), invalid)));
        Map<String, Object> imported = Map.of("itemCode", "invalid", "itemLabel", "无效", "ext" + index, "超".repeat(501));
        Map<String, Object> snapshot = Map.of("definition", Map.of("dictCode", "expenses", "dictName", "不应保留"),
                "items", List.of(imported));
        assertThrows(IllegalArgumentException.class, () -> transactions.executeWithoutResult(
                status -> migration.apply("expenses", snapshot)));
        assertNull(dictionaries.getByCode("invalid"));
        assertEquals("费用", dictionaries.getById(dictionary.getId()).getDictName());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_item", Integer.class));
    }

    private SysDict dictionary(String code) {
        var dict = new SysDict(); dict.setDictCode(code); dict.setDictName("费用"); return dict;
    }

    private SysDictItem item(String code) {
        var item = new SysDictItem(); item.setDictId(dictionary.getId()); item.setDictCode(dictionary.getDictCode());
        item.setItemCode(code); item.setItemLabel(code); item.setItemValue(code + "-value"); return item;
    }

    private List<String> extensions(SysDictItem item) {
        return Arrays.asList(item.getExt1(), item.getExt2(), item.getExt3(), item.getExt4(), item.getExt5());
    }
}
