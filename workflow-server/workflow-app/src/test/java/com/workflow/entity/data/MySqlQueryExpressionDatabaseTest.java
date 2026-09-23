package com.workflow.entity.data;

import com.workflow.admin.identity.position.infrastructure.persistence.mapper.SysPositionMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMenuMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.process.cc.infrastructure.persistence.mapper.ProcessCcRecordMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** 用原 MySQL 表达式作对照，覆盖绑定模式、0/1 映射、空排除参数及撤销条件的真实执行。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlQueryExpressionDatabaseTest {
    private static final String EMBED = "com.workflow.embed.management.infrastructure.persistence.EmbedManagementMapper";

    @Test void organizationPrefixKeepsNullFromWideningReadsOrUpdatesAndRollsBack() {
        try(var f=new Fixture()) {
            f.table("sys_organization","id VARCHAR(64) PRIMARY KEY,path VARCHAR(200),deleted INT,org_code VARCHAR(64),org_name VARCHAR(100),type VARCHAR(20),business_level_code VARCHAR(64),parent_id VARCHAR(64),level INT,sort_order INT,leader_id VARCHAR(64),leader_name VARCHAR(100),phone VARCHAR(40),email VARCHAR(100),address VARCHAR(200),status VARCHAR(20),description TEXT,create_time DATETIME,update_time DATETIME");
            var h=new Harness(f,SysOrganizationMapper.class);var mapper=h.mapper(SysOrganizationMapper.class);
            h.jdbc.update("INSERT INTO sys_organization(id,path,deleted) VALUES ('a','/root/a/',0),('b','/root/b/',0),('other','/other/',0),('deleted','/root/gone/',1)");
            assertEquals(Set.of("a","b"),new HashSet<>(mapper.selectAllChildrenByPath("/root/").stream().map(row->row.getId()).toList()));
            assertTrue(mapper.selectAllChildrenByPath(null).isEmpty());
            assertEquals(0,mapper.updateChildrenPath(null,"/new/"));
            assertEquals(3,mapper.selectAllChildrenByPath("").size());
            h.tx.executeWithoutResult(status->{
                assertEquals(2,mapper.updateChildrenPath("/root/","/new/"));
                assertEquals("/new/a/",h.jdbc.queryForObject("SELECT path FROM sys_organization WHERE id='a'",String.class));
                assertEquals("/other/",h.jdbc.queryForObject("SELECT path FROM sys_organization WHERE id='other'",String.class));
                assertEquals("/root/gone/",h.jdbc.queryForObject("SELECT path FROM sys_organization WHERE id='deleted'",String.class));
                assertEquals(2,mapper.updateChildrenPath("/new/",null));
                assertNull(h.jdbc.queryForObject("SELECT path FROM sys_organization WHERE id='a'",String.class));
                status.setRollbackOnly();
            });
            assertEquals(2,mapper.selectAllChildrenByPath("/root/").size());
        }
    }

    @Test void existenceQueriesMapNumbersToBooleansAndTreatAbsentExclusionAsAnInsertCheck() {
        try (var f = new Fixture()) {
            var cases = List.of(
                    new Existence(SysUserMapper.class,"sys_user","username","existsUsername","username",true),
                    new Existence(SysMenuMapper.class,"sys_menu","perm","existsPerm","perm",true),
                    new Existence(SysRoleMapper.class,"sys_role","role_code","existsRoleCode","roleCode",true),
                    new Existence(SysDictMapper.class,"sys_dict","dict_code","existsDictCode","dictCode",true),
                    new Existence(SysGroupMapper.class,"sys_group","group_code","existsGroupCode","groupCode",false),
                    new Existence(SysOrganizationMapper.class,"sys_organization","org_code","existsCode","orgCode",true),
                    new Existence(EntityFormMapper.class,"entity_form","form_key","existsFormKey","formKey",true));
            for (var item : cases) f.table(item.table(), "id VARCHAR(64) PRIMARY KEY, " + item.column() + " VARCHAR(100), entity_id VARCHAR(64), deleted INT");
            var h = new Harness(f, cases.stream().map(Existence::mapper).toArray(Class<?>[]::new));
            for (var item : cases) {
                h.jdbc.update("INSERT INTO " + item.table() + " VALUES ('self','match','entity',0),('deleted','old','entity',1)");
                var params = new HashMap<String,Object>(); params.put(item.parameter(),"match"); params.put("entityId","entity");
                for (String excluded : new String[]{null,"","other","self"}) {
                    params.put("excludeId",excluded);
                    assertEquals(!"self".equals(excluded), bool(h,item.mapper(),item.method(),params),item.table());
                }
                params.put("excludeId", ""); params.put(item.parameter(),"missing' OR 1=1 --");
                assertFalse(bool(h,item.mapper(),item.method(),params));
                params.put(item.parameter(),"old"); assertEquals(!item.filtersDeleted(),bool(h,item.mapper(),item.method(),params));
            }
        }
    }

    /** DISTINCT 必须在数据库中执行，以保留不区分大小写及重音的权限去重口径。 */
    @Test void entityPermissionsKeepDatabaseCollationDistinctAndScope() {
        try (var f = new Fixture()) {
            f.table("sys_menu", "id VARCHAR(64) PRIMARY KEY,entity_code VARCHAR(64),perm VARCHAR(100),menu_type VARCHAR(10),status VARCHAR(10),deleted INT");
            var h = new Harness(f, SysMenuMapper.class);
            h.jdbc.update("INSERT INTO sys_menu VALUES ('1','asset','READ','F','0',0),('2','asset','read','F','0',0),('3','asset','réad','F','0',0),('4','asset','WRITE','F','0',0),('5','asset','hidden','F','0',1),('6','other','foreign','F','0',0),('7','asset','','F','0',0),('8','asset',NULL,'F','0',0)");
            var expected = new HashSet<>(h.jdbc.queryForList("SELECT DISTINCT perm FROM sys_menu WHERE entity_code='asset' AND menu_type='F' AND status='0' AND deleted=0 AND NULLIF(perm,'') IS NOT NULL", String.class));
            assertEquals(2, expected.size());
            assertEquals(expected, h.mapper(SysMenuMapper.class).selectPermsByEntityCode("asset"));
        }
    }

    @Test void aggregateAndLimitedExistenceQueriesKeepBootstrapChildrenMembershipAndHistoryScope() {
        try (var f = new Fixture()) {
            f.table("sys_user","id VARCHAR(64) PRIMARY KEY,username VARCHAR(64),status CHAR(1),deleted INT,password VARCHAR(100)");
            f.table("sys_menu","id VARCHAR(64) PRIMARY KEY,parent_id VARCHAR(64),deleted INT");
            f.table("sys_role_menu","role_id VARCHAR(64),menu_id VARCHAR(64)");
            f.table("process_definition_config","process_key VARCHAR(64),deleted INT");
            f.table("entity_record_version","id VARCHAR(64),entity_code VARCHAR(64)");
            var h = new Harness(f,SysUserMapper.class,SysMenuMapper.class,SysRoleMenuMapper.class,ProcessDefinitionConfigMapper.class,EntityRecordVersionMapper.class);
            h.jdbc.update("INSERT INTO sys_user VALUES ('1','admin','1',0,'hash')");
            assertTrue(h.mapper(SysUserMapper.class).isBootstrapAdministratorPending("hash"));
            assertFalse(h.mapper(SysUserMapper.class).isBootstrapAdministratorPending("wrong"));
            h.jdbc.update("UPDATE sys_user SET status='0'"); assertFalse(h.mapper(SysUserMapper.class).isBootstrapAdministratorPending("hash"));
            h.jdbc.update("INSERT INTO sys_menu VALUES ('a','p',0),('b','gone',1)");
            assertTrue(h.mapper(SysMenuMapper.class).hasChildren("p")); assertFalse(h.mapper(SysMenuMapper.class).hasChildren("gone"));
            h.jdbc.update("INSERT INTO sys_role_menu VALUES ('r','m'),('r','m')");
            assertTrue(h.mapper(SysRoleMenuMapper.class).existsRoleMenu("r","m")); assertFalse(h.mapper(SysRoleMenuMapper.class).existsRoleMenu("r","other"));
            h.jdbc.update("INSERT INTO process_definition_config VALUES ('p',0),('gone',1)");
            assertTrue(h.mapper(ProcessDefinitionConfigMapper.class).existsByProcessKey("p")); assertFalse(h.mapper(ProcessDefinitionConfigMapper.class).existsByProcessKey("gone"));
            assertFalse(h.mapper(EntityRecordVersionMapper.class).existsByEntityCode("asset"));
            h.jdbc.update("INSERT INTO entity_record_version VALUES ('1','asset'),('2','asset'),('3','other')");
            assertTrue(h.mapper(EntityRecordVersionMapper.class).existsByEntityCode("asset"));
            assertFalse(h.mapper(EntityRecordVersionMapper.class).existsByEntityCode("asset' OR 1=1 --"));
        }
    }

    @Test void ccSearchMatchesLegacyContainsForWildcardsQuotesUnicodeAndNullFilters() {
        try (var f = new Fixture()) {
            f.table("process_cc_record","id VARCHAR(64) PRIMARY KEY,cc_user_id VARCHAR(64),deleted INT,process_name VARCHAR(200),data_name VARCHAR(200),node_name VARCHAR(200),business_key VARCHAR(200),comment TEXT,operator_name VARCHAR(200),create_time DATETIME,process_instance_id VARCHAR(64),process_definition_id VARCHAR(64),process_key VARCHAR(100),node_id VARCHAR(64),cc_user_name VARCHAR(100),cc_type VARCHAR(40),cc_timing VARCHAR(40),operator_id VARCHAR(64),source_task_id VARCHAR(64),source_type VARCHAR(40),recipient_rule_snapshot TEXT,unique_key VARCHAR(200),read_status VARCHAR(20),read_time DATETIME,update_time DATETIME");
            var h = new Harness(f,ProcessCcRecordMapper.class); var mapper=h.mapper(ProcessCcRecordMapper.class);
            int id=0;
            for (String text:List.of("10","O'Neil","百分比100%","under_score","back\\slash","中文审批")) {
                h.jdbc.update("INSERT INTO process_cc_record(id,cc_user_id,deleted,process_name,operator_name,create_time) VALUES (?,'u',0,?,'办理人',?)",Integer.toString(++id),text,LocalDateTime.of(2026,1,1,0,0).plusSeconds(id));
            }
            h.jdbc.update("INSERT INTO process_cc_record(id,cc_user_id,deleted,process_name) VALUES ('foreign','other',0,'10'),('deleted','u',1,'10')");
            for (String keyword:new String[]{null,"","10","O'Neil","%","_","\\","中文","x' OR 1=1 --"}) {
                String where=" FROM process_cc_record WHERE cc_user_id='u' AND deleted=0";
                Object[] values=new Object[0];
                if(keyword!=null&&!keyword.isEmpty()) {
                    where+=" AND (process_name LIKE CONCAT('%',?,'%') OR data_name LIKE CONCAT('%',?,'%') OR node_name LIKE CONCAT('%',?,'%') OR business_key LIKE CONCAT('%',?,'%') OR comment LIKE CONCAT('%',?,'%'))";
                    values=new Object[]{keyword,keyword,keyword,keyword,keyword};
                }
                var expected=h.jdbc.queryForList("SELECT id"+where+" ORDER BY create_time DESC",String.class,values);
                assertEquals(expected,mapper.findByCcUserIdFiltered("u",keyword,null,null,null,0,100).stream().map(row->row.getId()).toList());
                assertEquals(expected.size(),mapper.countByCcUserIdFiltered("u",keyword,null,null,null));
            }
            assertEquals(6,mapper.countByCcUserIdFiltered("u",null,"办理",null,null));
            assertEquals(0,mapper.countByCcUserIdFiltered("u",null,"不存在",null,null));
            assertEquals(2,mapper.findByCcUserIdFiltered("u",null,null,null,null,2,2).size());
        }
    }

    @Test void positionReferenceSumKeepsThreeSourcesDeletionAndNullPatternBehavior() {
        try (var f = new Fixture()) {
            f.table("process_node_config","config_json TEXT,deleted INT");
            f.table("process_definition_config","bpmn_xml TEXT,deleted INT");
            f.table("process_version_history","bpmn_xml TEXT,deleted INT");
            var h=new Harness(f,SysPositionMapper.class); var mapper=h.mapper(SysPositionMapper.class);
            assertEquals(0,mapper.countProcessReferences("LEADER"));
            for(String table:List.of("process_node_config","process_definition_config","process_version_history"))
                h.jdbc.update("INSERT INTO "+table+" VALUES ('UNIT_LEADER',0),('UNIT_LEADER',1),('OTHER',0),(NULL,0)");
            assertEquals(3,mapper.countProcessReferences("LEADER"));
            assertEquals(6,mapper.countProcessReferences("%"));
            assertEquals(0,mapper.countProcessReferences(null));
            assertEquals(0,mapper.countProcessReferences("x' OR 1=1 --"));
        }
    }

    @Test void uiReferenceCandidatePatternsPreserveRawDocumentAndBlankSemantics() {
        try(var f=new Fixture()) {
            f.table("ui_config_release","id VARCHAR(64) PRIMARY KEY,config_type VARCHAR(20),config_id VARCHAR(64),version INT,status VARCHAR(20),snapshot_document LONGTEXT,content_hash VARCHAR(64),description TEXT,release_mode VARCHAR(20),base_release_id VARCHAR(64),risk_level VARCHAR(20),rollout_scope VARCHAR(40),patch_document LONGTEXT,override_risk INT,override_reason TEXT,published_by VARCHAR(64),published_at DATETIME");
            f.table("ui_event_binding","id VARCHAR(64) PRIMARY KEY,owner_type VARCHAR(20),owner_id VARCHAR(64),target_type VARCHAR(20),target_key VARCHAR(64),event_code VARCHAR(64),deleted INT,steps_document LONGTEXT,inheritance_mode VARCHAR(20),revision INT,enabled INT,create_time DATETIME,update_time DATETIME");
            var h=new Harness(f,UiConfigReleaseMapper.class,UiEventBindingMapper.class);
            int id=0;
            for(String document:new String[]{null,"","   ","{\"serviceId\":\"O'Neil\"}","{\"serviceId\": \"O'Neil\"}","{\"中文\":\"100%\\\\path\"}"}) {
                String key=Integer.toString(++id);
                h.jdbc.update("INSERT INTO ui_config_release(id,config_type,config_id,version,status,snapshot_document) VALUES (?,'FORM','f',1,'ACTIVE',?)",key,document);
                h.jdbc.update("INSERT INTO ui_event_binding(id,owner_type,owner_id,target_type,target_key,event_code,deleted,steps_document) VALUES (?,'FORM','f','FIELD','field','change',0,?)",key,document);
            }
            for(String needle:new String[]{null,"","O'Neil","%","_","\\","中文"}) {
                var expected=h.jdbc.queryForList("SELECT id FROM ui_config_release WHERE status='ACTIVE' AND config_type IN ('FORM','LIST') AND snapshot_document IS NOT NULL AND (snapshot_document LIKE CONCAT('%',?,'%') OR snapshot_document LIKE CONCAT('%',?,'%')) ORDER BY id",String.class,needle,needle);
                assertEquals(expected,h.mapper(UiConfigReleaseMapper.class).findActiveReferenceCandidates(needle,needle).stream().map(row->row.getId()).sorted().toList());
                assertEquals(expected,h.mapper(UiConfigReleaseMapper.class).findExecutableDataSourceReferenceCandidates(needle).stream().map(row->row.getId()).sorted().toList());
                var draft=h.jdbc.queryForList("SELECT id FROM ui_event_binding WHERE deleted=0 AND steps_document IS NOT NULL AND TRIM(steps_document) <> '' AND (steps_document LIKE CONCAT('%',?,'%') OR steps_document LIKE CONCAT('%',?,'%')) ORDER BY id",String.class,needle,needle);
                assertEquals(draft,h.mapper(UiEventBindingMapper.class).findDraftReferenceCandidates(needle,needle).stream().map(row->row.getId()).sorted().toList());
            }
        }
    }

    @Test void embedReadinessCaseMapsToBooleanAndLockProjectionRemainsFalse() throws Exception {
        try(var f=new Fixture()) {
            f.table("sys_user","id VARCHAR(64) PRIMARY KEY,status CHAR(1),deleted INT,password_reset_required INT");
            f.table("embed_external_identity_binding","id VARCHAR(64) PRIMARY KEY,application_id VARCHAR(64),identity_provider_id VARCHAR(64),subject_digest VARCHAR(64),subject_digest_key_version VARCHAR(64),subject_hint VARCHAR(64),flow_user_id VARCHAR(64),status VARCHAR(20),binding_version BIGINT,effective_at DATETIME,expires_at DATETIME,create_by VARCHAR(64),create_time DATETIME,update_by VARCHAR(64),update_time DATETIME,revoked_by VARCHAR(64),revoked_at DATETIME");
            var h=new Harness(f,Class.forName(EMBED));
            h.jdbc.update("INSERT INTO sys_user VALUES ('u','0',0,0)");
            h.jdbc.update("INSERT INTO embed_external_identity_binding(id,application_id,identity_provider_id,subject_digest,flow_user_id,status,binding_version) VALUES ('b','app','provider','digest','u','ACTIVE',1)");
            var params=new HashMap<String,Object>();params.put("id","b");params.put("applicationId","app");params.put("providerId","provider");params.put("digests",List.of("digest"));params.put("limit",10);params.put("offset",0);params.put("flowUserId",null);params.put("status",null);
            for(String assignment:List.of("status='0',deleted=0,password_reset_required=0","status='1',deleted=0,password_reset_required=0","status='0',deleted=1,password_reset_required=0","status='0',deleted=0,password_reset_required=1")) {
                h.jdbc.update("UPDATE sys_user SET "+assignment);
                boolean expected=assignment.equals("status='0',deleted=0,password_reset_required=0");
                for(String method:List.of("findBinding","findBindingByDigests"))
                    assertEquals(expected,h.json.valueToTree(MapperMethodCalls.call(h.session,EMBED+"."+method,params)).path("flowUserReady").asBoolean());
                assertEquals(expected,h.json.valueToTree(MapperMethodCalls.<List<Object>>call(h.session,EMBED+".findBindings",params).get(0)).path("flowUserReady").asBoolean());
                assertEquals(expected,h.session.selectOne(EMBED+".flowUserExistsAndEnabled",Map.of("id","u")));
                h.tx.executeWithoutResult(status->assertFalse(h.json.valueToTree(h.session.selectOne(EMBED+".lockBinding",params)).path("flowUserReady").asBoolean()));
            }
        }
    }

    @Test void numericRevocationFlagsKeepActorTimestampAndOptimisticVersionGuards() throws Exception {
        try(var f=new Fixture()) {
            for(String table:List.of("embed_application_grant","embed_identity_provider","embed_external_identity_binding"))
                f.table(table,"id VARCHAR(64) PRIMARY KEY,status VARCHAR(20),lock_version BIGINT,binding_version BIGINT,security_version BIGINT,update_by VARCHAR(64),update_time DATETIME(6),revoked_by VARCHAR(64),revoked_at DATETIME(6)");
            var h=new Harness(f,Class.forName(EMBED)); var now=LocalDateTime.of(2026,9,22,12,0,1,123456000);
            for(var item:List.of(new String[]{"embed_application_grant","changeGrantStatus","grantId","lock_version"},new String[]{"embed_identity_provider","changeProviderStatus","providerId","lock_version"},new String[]{"embed_external_identity_binding","changeBindingStatus","bindingId","binding_version"})) {
                h.jdbc.update("INSERT INTO "+item[0]+"(id,status,lock_version,binding_version,security_version) VALUES ('one','ACTIVE',1,1,1)");
                var params=new HashMap<String,Object>();params.put(item[2],"one");params.put("expectedVersion",1L);params.put("status","REVOKED");params.put("actorId","actor");params.put("now",now);params.put("revoked",true);
                assertEquals(1,h.session.update(EMBED+"."+item[1],params));
                assertEquals("actor",h.jdbc.queryForObject("SELECT revoked_by FROM "+item[0],String.class));
                assertEquals(now,h.jdbc.queryForObject("SELECT revoked_at FROM "+item[0],LocalDateTime.class));
                assertEquals(0,h.session.update(EMBED+"."+item[1],params));
                params.put("expectedVersion",2L);params.put("status","ACTIVE");params.put("revoked",false);
                assertEquals(1,h.session.update(EMBED+"."+item[1],params));
                assertNull(h.jdbc.queryForObject("SELECT revoked_by FROM "+item[0],String.class));
                assertNull(h.jdbc.queryForObject("SELECT revoked_at FROM "+item[0],LocalDateTime.class));
                assertEquals(3L,h.jdbc.queryForObject("SELECT "+item[3]+" FROM "+item[0],Long.class));
            }
        }
    }

    private static boolean bool(Harness h,Class<?> mapper,String method,Map<String,Object> parameters) {
        return Boolean.TRUE.equals(MapperMethodCalls.call(h.session,mapper.getName()+"."+method,parameters));
    }
    private record Existence(Class<?> mapper,String table,String column,String method,String parameter,boolean filtersDeleted) {}
}
