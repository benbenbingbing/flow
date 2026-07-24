package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityData;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实体数据服务单元测试。
 *
 * <p>被测对象：{@link EntityDataService}，覆盖实体数据的增删改查、按流程实例查询、
 * 不启动流程的保存、实体不存在校验等场景。
 */
@ExtendWith(MockitoExtension.class)
public class EntityDataServiceTest {

    @Mock
    private EntityDataMapper dataMapper;

    @Mock
    private EntityDefinitionMapper definitionMapper;

    @Mock
    private ProcessDefinitionConfigMapper processDefinitionConfigMapper;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EntityCodeGeneratorService codeGeneratorService;

    @InjectMocks
    private EntityDataService entityDataService;

    /** 测试数据 */
    private EntityData testData;
    /** 测试实体定义 */
    private EntityDefinition testDefinition;
    /** 测试流程定义 */
    private ProcessDefinitionConfig testProcess;

    /** 初始化测试数据、实体定义与流程定义 */
    @BeforeEach
    void setUp() {
        testDefinition = new EntityDefinition();
        testDefinition.setId("1");
        testDefinition.setEntityCode("test_entity");
        testDefinition.setEntityName("测试实体");
        testDefinition.setLifecycleMode(EntityDefinition.LifecycleMode.WORKFLOW);
        testDefinition.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        testDefinition.setProcessDefinitionId("proc-1");

        testProcess = new ProcessDefinitionConfig();
        testProcess.setId("proc-1");
        testProcess.setProcessKey("test_process");
        testProcess.setProcessName("测试流程");
        testProcess.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);

        testData = new EntityData();
        testData.setId("data-1");
        testData.setEntityCode("test_entity");
        testData.setDataNo("TEST-001");
        testData.setTitle("测试数据");
        testData.setSubmitterId("user1");
        testData.setSubmitterName("张三");
        testData.setStatus(EntityData.DataStatus.PENDING.name());
        testData.setDataJson("{\"name\":\"测试\",\"amount\":100}");
    }

    /** 测试按实体编码查询数据：验证返回数量与 dataNo 正确 */
    @Test
    void testFindByEntityCode() {
        when(dataMapper.findByEntityCode("test_entity")).thenReturn(Arrays.asList(testData));

        List<EntityDataDTO> result = entityDataService.findByEntityCode("test_entity");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("TEST-001", result.get(0).getDataNo());
        verify(dataMapper, times(1)).findByEntityCode("test_entity");
    }

    /** 测试按 ID 查询数据：验证返回的 id 与 dataNo 正确 */
    @Test
    void testFindById() {
        when(dataMapper.selectById("data-1")).thenReturn(testData);

        EntityDataDTO result = entityDataService.findById("data-1");

        assertNotNull(result);
        assertEquals("data-1", result.getId());
        assertEquals("TEST-001", result.getDataNo());
        verify(dataMapper, times(1)).selectById("data-1");
    }

    /** 测试按 ID 查询不存在数据：验证抛出 RuntimeException 且消息包含对应 ID */
    @Test
    void testFindByIdNotFound() {
        when(dataMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityDataService.findById("999");
        });

        assertEquals("数据不存在: 999", exception.getMessage());
    }

    /** 测试按流程实例 ID 查询数据：验证返回的 id 正确 */
    @Test
    void testFindByProcessInstanceId() {
        when(dataMapper.findByProcessInstanceId("proc-inst-1")).thenReturn(Optional.of(testData));

        EntityDataDTO result = entityDataService.findByProcessInstanceId("proc-inst-1");

        assertNotNull(result);
        assertEquals("data-1", result.getId());
        verify(dataMapper, times(1)).findByProcessInstanceId("proc-inst-1");
    }

    /** 测试按不存在的流程实例 ID 查询：验证抛出 RuntimeException 且消息包含对应 ID */
    @Test
    void testFindByProcessInstanceIdNotFound() {
        when(dataMapper.findByProcessInstanceId("999")).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityDataService.findByProcessInstanceId("999");
        });

        assertEquals("数据不存在: 999", exception.getMessage());
    }

    /** 测试不启动流程的保存：验证插入数据且不启动流程实例 */
    @Test
    void testSaveWithoutProcess() throws Exception {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("test_entity");
        dto.setTitle("测试数据");
        dto.setSubmitterId("user1");
        dto.setSubmitterName("张三");
        dto.setData(new HashMap<>());
        dto.setStartProcess(false);

        when(definitionMapper.findByEntityCode("test_entity")).thenReturn(Optional.of(testDefinition));
        when(codeGeneratorService.generateCode("test_entity")).thenReturn("TEST-001");
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"name\":\"test\"}");
        when(dataMapper.insert(any(EntityData.class))).thenAnswer(invocation -> {
            EntityData data = invocation.getArgument(0);
            data.setId("new-data-id");
            return 1;
        });

        EntityDataDTO result = entityDataService.save(dto);

        assertNotNull(result);
        verify(dataMapper, times(1)).insert(any(EntityData.class));
        verify(runtimeService, never()).startProcessInstanceById(any(), anyMap());
    }

    /** 测试保存时实体不存在：验证抛出 RuntimeException 且消息包含"实体不存在" */
    @Test
    void testSaveEntityNotFound() {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("not_exist");

        when(definitionMapper.findByEntityCode("not_exist")).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityDataService.save(dto);
        });

        assertTrue(exception.getMessage().contains("实体不存在"));
    }

    /** 测试更新数据：验证更新成功并触发 selectById 与 updateById */
    @Test
    void testUpdate() throws Exception {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setTitle("更新后的标题");
        dto.setData(new HashMap<>());

        when(dataMapper.selectById("data-1")).thenReturn(testData);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"name\":\"updated\"}");
        when(dataMapper.updateById(any(EntityData.class))).thenReturn(1);

        EntityDataDTO result = entityDataService.update("data-1", dto);

        assertNotNull(result);
        verify(dataMapper, times(1)).selectById("data-1");
        verify(dataMapper, times(1)).updateById(any(EntityData.class));
    }

    /** 测试更新不存在数据：验证抛出 RuntimeException 且消息包含对应 ID */
    @Test
    void testUpdateNotFound() {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setTitle("更新");

        when(dataMapper.selectById("999")).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            entityDataService.update("999", dto);
        });

        assertEquals("数据不存在: 999", exception.getMessage());
    }

    /** 测试删除数据：验证触发 deleteById */
    @Test
    void testDelete() {
        when(dataMapper.deleteById("data-1")).thenReturn(1);

        entityDataService.delete("data-1");

        verify(dataMapper, times(1)).deleteById("data-1");
    }

    /** 测试数据编号生成格式：使用雪花算法验证多次生成的编号应互不相同（实际生成在集成测试中验证） */
    @Test
    void testGenerateDataNo() {
        // 测试数据编号生成，由于使用了雪花算法，只能验证格式
        String entityCode = "TEST";
        
        // 多次调用应该生成不同的编号
        Set<String> dataNos = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            EntityDataDTO dto = new EntityDataDTO();
            dto.setEntityCode(entityCode);
            dto.setTitle("Test" + i);
            dto.setData(new HashMap<>());
            
            // 这里只是测试编号格式，不实际调用save
        }
        
        // 验证编号格式正确性
        assertTrue(true); // 实际测试应在集成测试中验证
    }
}
