# workflow-excel

基于 [EasyExcel 4.0.3](https://github.com/alibaba/easyexcel) 的通用 Excel 工具模块，供后续业务模块复用。
模块不依赖业务模块、Spring 容器或 Servlet，提供 DTO 字段校验和通用监听器。
业务唯一性、权限、入库事务和 Controller 由调用方负责。

转换器位于 `com.workflow.excel.converter`，通过 DTO 字段的 `@ExcelProperty(converter = ...)` 显式绑定，配置注解不会自动替换 EasyExcel 默认转换器。

## 引入模块

在需要使用的模块中添加依赖。当前仅在 `workflow-integration/pom.xml` 注册模块，尚未修改其他模块或根 POM 的依赖管理，因此调用方需要声明版本：

```xml
<dependency>
    <groupId>com.workflow</groupId>
    <artifactId>workflow-excel</artifactId>
    <version>${project.version}</version>
</dependency>
```

## 对象导入导出

为 Excel 数据行定义 DTO，使用 EasyExcel 注解描述列映射。DTO 需要无参构造器和 getter/setter；不建议使用 Java record 作为 EasyExcel 行模型。

```java
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.converters.longconverter.LongStringConverter;
import com.workflow.excel.annotation.validation.ExcelNotBlank;
import com.workflow.excel.annotation.validation.ExcelNotNull;
import com.workflow.excel.annotation.validation.ExcelSize;

public class UserExcelRow {
    // 编号以文本形式导出，避免 Excel 数值精度限制导致大整数编号被截断。
    @ExcelProperty(value = "编号", index = 0, converter = LongStringConverter.class)
    @ExcelNotNull(message = "编号必填")
    private Long id;

    @ExcelProperty(value = "姓名", index = 1)
    @ExcelNotBlank(message = "姓名必填")
    @ExcelSize(max = 50, message = "姓名不能超过 {max} 个字符")
    private String name;

    public UserExcelRow() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

```java
import com.workflow.excel.ExcelReadOptions;
import com.workflow.excel.ExcelUtils;

// 默认读取第一个 Sheet，跳过一行表头，并校验 DTO 的 Default 分组约束。
// inputStream 的关闭由调用方负责。
List<UserExcelRow> rows = ExcelUtils.read(inputStream, UserExcelRow.class);

// 导出为 XLSX，不自动执行字段校验；outputStream 的关闭由调用方负责。
ExcelUtils.write(outputStream, UserExcelRow.class, rows, "人员");

// 空集合生成只有表头的导入模板。
ExcelUtils.write(outputStream, UserExcelRow.class, List.of(), "导入模板");
```

## 分批处理

```java
// Sheet 索引从 0 开始；多级表头需要设置实际表头行数，无表头时设置为 0。
ExcelReadOptions options = new ExcelReadOptions(0, 1, 500);

// 适用于大文件；只缓存当前批次，最后不足 500 行的数据也会回调。
long imported = ExcelUtils.readBatches(inputStream, UserExcelRow.class, options, batch -> {
    userImportService.validateAndSave(batch);
});

// 惰性 Iterable 按需提供分页查询数据，各批追加到同一 Sheet，表头只写一次。
Iterable<List<UserExcelRow>> pages = userExportService.queryPages();
ExcelUtils.writeBatches(outputStream, UserExcelRow.class, pages, "人员");
```

批处理回调在解析线程同步执行；各批列表独立，可以保留。若调用方累计保留所有批次，仍会占用全量数据的内存。
解析或回调异常会中止导入，已完成的业务副作用不会自动回滚，事务、幂等和错误提示由业务模块负责。
导出或分页查询异常时，输出流可能已写入部分内容，调用方应丢弃失败产物。

## 常用转换器

| 转换器 | Java 字段类型 | 导入与导出行为 |
| --- | --- | --- |
| `ExcelLocalDateConverter` | `LocalDate` | 读取日期文本或原生日期序号，默认导出 `yyyy-MM-dd`；原生日期中的时间部分被舍弃 |
| `ExcelLocalDateTimeConverter` | `LocalDateTime` | 读取本地日期时间、ISO 本地日期时间或原生日期序号；默认导出到秒并保留非零小数秒 |
| `ExcelDateConverter` | `java.util.Date` | 按显式时区与本地日期时间转换，默认 `Asia/Shanghai`，保留毫秒精度 |
| `ExcelStringConverter` | `String` | 默认清理首尾空白、空文本转 null；数值单元格遵循显示格式；始终按文本导出以保留前导零 |
| `ExcelAmountConverter` | `BigDecimal` | 读取数值或金额文本，默认两位小数且禁止隐式舍入；默认按文本导出以保留精度 |
| `ExcelCodeConverter` | `String`、整数类型、`BigInteger`、`Boolean`、枚举 | 根据字段映射将显示名称还原成代码，并将代码导出为显示名称 |

### 日期与日期时间

```java
@ExcelProperty(value = "日期", converter = ExcelLocalDateConverter.class)
@ExcelDateFormat(value = "yyyy/MM/dd", readPatterns = {"yyyy-MM-dd", "yyyyMMdd"})
private LocalDate day;

@ExcelProperty(value = "时间", converter = ExcelLocalDateTimeConverter.class)
private LocalDateTime time;

@ExcelProperty(value = "时间点", converter = ExcelDateConverter.class)
@ExcelDateFormat(zoneId = "UTC")
private Date timestamp;
```

`ExcelDateFormat.value` 是导出格式，同时也用于导入；设置 `readPatterns` 后额外只接受这些格式，未设置时接受内置常用格式。
默认日期格式接受 `yyyy-MM-dd`、`yyyy/MM/dd`、`yyyyMMdd`。
默认日期时间还接受空格或 `T` 分隔的本地日期时间、斜线日期时间、小数秒和到分钟的格式；纯日期转为当天零点。
日期文本严格解析，`2026-02-30` 等不存在的日期会报错。

原生数值日期自动读取 XLS/XLSX 工作簿的 1900/1904 日期基准，仅解析元数据并按工作簿缓存，不加载完整 POI Workbook。
`LocalDate` 和 `LocalDateTime` 不做时区转换，`Date` 使用 `zoneId`，不依赖服务器的默认时区。
格式描述本地日期或时间，不能包含时区或 UTC 偏移；Date 的时区统一用 `zoneId` 配置，夏令时导致本地时间不存在或有歧义时会报错。
显式导出格式可能省略原值的秒或小数秒，应按业务需要配置。

### 字符串

```java
@ExcelProperty(value = "编号", converter = ExcelStringConverter.class)
private String identifier;

@ExcelProperty(value = "原始文本", converter = ExcelStringConverter.class)
@ExcelStringFormat(trim = false, emptyToNull = false)
private String rawText;

// 关闭 EasyExcel 在转换器执行前的空白清理，字段的 trim=false 才能保留源文本。
ExcelReadOptions options = new ExcelReadOptions(0, 1, 500, false);
List<MyExcelRow> rows = ExcelUtils.read(inputStream, MyExcelRow.class, options);
```

原有三参数 `ExcelReadOptions` 继续使用，默认 `autoTrim=true`。
转换器可保留文本 `00123`，也会将格式为 `00000` 的数值 123 读取为 `00123`；无法恢复源文件已经丢失的前导零或数值精度。

### 金额

```java
@ExcelProperty(value = "金额", converter = ExcelAmountConverter.class)
@ExcelAmountFormat(scale = 2, roundingMode = RoundingMode.HALF_UP, currencySymbol = "￥")
private BigDecimal amount;
```

可读取 `1234.50`、标准千分位 `1,234.50`，以及配置货币前缀后的 `￥1,234.50`。
`1,23.45` 等错误分组会报错；`allowThousands=false` 可禁止千分位。
货币前缀仅用于导入识别，不执行币种换算，导出不自动附加前缀。
默认 `RoundingMode.UNNECESSARY`，因此多出有效小数位会报错；需要四舍五入时显式指定舍入方式。
默认按固定小数位文本导出；需要 Excel 数值运算时设 `writeAsText=false`，超过 15 位有效数字或数值范围会报错。

### 代码值

```java
@ExcelProperty(value = "状态", converter = ExcelCodeConverter.class)
@ExcelCodeMapping({
    @ExcelCode(code = "0", label = "禁用"),
    @ExcelCode(code = "1", label = "启用")
})
private Integer status;
```

导出 `1` 为「启用」，导入「启用」还原为 `1`；默认也允许直接填写配置中的代码。
`allowCode=false` 仅接受显示名称；`strict=false` 允许未知值按字段类型原样转换，无法转换成目标类型的文本仍报错。
字符串代码保留前导零，枚举代码使用枚举常量名，Boolean 代码使用 `true` / `false`。
代码、名称及其对应的 Java 值必须唯一；名称与其他代码冲突也会报错，避免双向转换产生歧义。
该转换器提供静态映射，数据库字典等动态代码表由业务模块通过自定义 EasyExcel Converter 实现。

转换完成后才执行读取监听器的字段校验；格式转换错误使用 EasyExcel 的 `ExcelDataConvertException` 保留行列位置。

## 数据校验注解

注解位于 `com.workflow.excel.annotation`，基于 Jakarta Bean Validation 组合约束，可在字段、getter 上使用，也可由标准 `Validator` 执行。
每个注解支持 `message`、`groups` 和 `payload`，例如消息中的 `{min}`、`{max}` 会替换为配置值。

| 注解 | 校验规则 | null 是否合法 |
| --- | --- | --- |
| `ExcelNotNull` | 值不能为 null，适用于任意类型 | 否 |
| `ExcelNotBlank` | 文本不能为 null、空字符串或仅包含空白字符 | 否 |
| `ExcelNotEmpty` | 文本、集合、Map、数组不能为 null 或长度为 0；空白字符串仍合法 | 否 |
| `ExcelSize(min, max)` | 文本长度或集合、Map、数组元素数，包含边界 | 是 |
| `ExcelPattern(regexp, flags)` | 整个文本匹配正则表达式，可用于手机号、编码等格式 | 是 |
| `ExcelEmail` | 文本符合邮箱格式；空字符串合法 | 是 |
| `ExcelMin(value, inclusive)` | 整数、BigDecimal、BigInteger 或数值文本满足十进制下限 | 是 |
| `ExcelMax(value, inclusive)` | 整数、BigDecimal、BigInteger 或数值文本满足十进制上限 | 是 |

格式、长度和范围注解不会自动让字段必填，需要时搭配对应的非空约束。金额建议使用 `BigDecimal`，上下限通过字符串配置以保留精度：

```java
@ExcelNotNull(message = "金额必填")
@ExcelMin(value = "0", inclusive = false, message = "金额必须大于零")
@ExcelMax(value = "999999.99", message = "金额超过允许的上限")
private BigDecimal amount;
```

`read` / `readBatches` 默认在每行进入批处理回调之前执行字段校验。
遇到第一条不合格的数据行时抛出 `ExcelValidationException`，保留该行全部字段错误；不会继续读取或把该行交给业务回调。

```java
try {
    ExcelUtils.read(inputStream, UserExcelRow.class);
} catch (ExcelValidationException error) {
    String sheet = error.getSheetName();
    int visibleRow = error.getRowIndex() + 1; // 原始索引包含表头，从 0 开始
    List<ExcelValidationError> fields = error.getErrors(); // fieldName 是 DTO 属性路径
    // 业务模块用 sheet、visibleRow 和 fields 生成导入错误提示。
}
```

`ExcelValidator.validate(row, groups...)` 可单独返回字段错误，`validateOrThrow(row, sheetName, rowIndex, groups...)` 可按行抛出异常。
默认导出不执行字段校验，便于输出历史数据或错误报告；需要导出校验时由调用方显式调用 `ExcelValidator`。
错误对象不保存原始字段值，校验分组、嵌套的 `@Valid` 和标准 Jakarta 约束遵循 Bean Validation 的规则。

## 通用读取监听器

`ExcelReadListener<T>` 提供逐行字段校验、分批回调、表头快照和数据行计数；一次导入创建一个实例。
可直接配合 EasyExcel，也可传入 `readWithListener`，需要业务表头检查时可继承并覆盖 `invokeHeadMap`。
该方法调用 `super.invokeHeadMap` 时会继续保存表头快照。

```java
import com.workflow.excel.listener.ExcelReadListener;
import jakarta.validation.groups.Default;

// 自定义分组示例：DTO 的相关约束声明 groups = StrictImport.class。
public interface StrictImport {}

ExcelReadListener<UserExcelRow> listener = new ExcelReadListener<>(
    500, batch -> userImportService.validateAndSave(batch), Default.class, StrictImport.class);
ExcelUtils.readWithListener(inputStream, UserExcelRow.class, ExcelReadOptions.defaults(), listener);
long rows = listener.getRowCount();
List<Map<Integer, String>> headRows = listener.getHeadRows(); // 每一级表头的不可变快照
```

不传分组时校验 Default；显式传入自定义分组后，仅校验这些分组，需要默认规则时同时传入 `Default.class`。
`getRowCount()` 表示已通过字段校验并加入批次的数据行数；异常退出时，尾批可能尚未交给业务模块处理。
自定义的其他 `ReadListener` 自行决定校验行为，`readWithListener` 不额外再校验一次。

## 通用写入监听器

`ExcelWriteListener` 实现 EasyExcel `RowWriteHandler`，跳过表头、统计数据行，并支持数据行完成后的同步回调。
`write`、`writeBatches`、`writeRows` 均可通过最后的 `WriteHandler...` 参数注册它和其他样式处理器；无显式处理器的重载使用默认写监听器。

```java
import com.workflow.excel.listener.ExcelWriteListener;

ExcelWriteListener listener = new ExcelWriteListener(context -> {
    context.getRow().setHeightInPoints(24); // 可调整当前行的样式
    exportProgress.onRowWritten(context.getRowIndex());
});
ExcelUtils.writeBatches(outputStream, UserExcelRow.class, pages, "人员", listener);
long exported = listener.getRowCount(); // 所有批次的数据行总数，不包含表头
```

行完成回调不能代表整个文件已导出成功，仍需等待工具方法返回。回调异常会中止导出，应丢弃失败文件。
监听器用于普通写入；模板 `fill` 可能多次触发同一行回调，此时计数是回调次数。

## 动态表头

```java
// 每个内层列表是一列从上到下的标题，可以定义多级表头。
List<List<String>> head = List.of(List.of("人员", "姓名"), List.of("人员", "编号"));
List<List<String>> data = List.of(List.of("张三", "001"));
ExcelUtils.writeRows(outputStream, head, data, "动态数据");

List<Map<Integer, String>> rows = ExcelUtils.readRows(inputStream, new ExcelReadOptions(0, 2, 500));
// Map 的键是从 0 开始的列索引；空单元格可能缺少键或值为 null。
// 动态读取提供文本值，业务模块根据字段定义执行类型转换和校验。

ExcelUtils.readRowsBatches(inputStream, new ExcelReadOptions(0, 2, 500), batch -> {
    dynamicImportService.validateAndSave(batch);
});
```

需要检查原始表头、获取行号或定制错误处理时，可通过 `readWithListener` 传入本次导入专用的 EasyExcel `ReadListener`。
动态列可通过 `readRowsWithListener` 使用 `ExcelReadListener<Map<Integer, String>>` 收集多级表头和分批处理；动态 Map 没有 DTO 注解约束，字段校验由业务模块负责。
默认不吞掉转换错误，`ExcelDataConvertException` 中保留原始行列索引（从 0 开始），业务模块可转换为用户可见的错误位置。

## 支持范围和验证

- 读取 XLS/XLSX，导出 XLSX；一次工具调用处理一个 Sheet。
- 指定的 Sheet 不存在时抛出 `IllegalArgumentException`，避免将配置错误误认为一次成功的空数据导入。
- `read` / `readRows` 返回全量数据，适用于小文件；大文件使用分批读取。
- 所有流均由调用方创建和关闭，工具内部释放 EasyExcel reader/writer 及其临时资源。
- HTTP 响应的下载文件名和 Content-Type（`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`）由调用方设置。

在仓库根目录执行：

```sh
mvn -f workflow-server/pom.xml -pl workflow-integration/workflow-excel -am test
```

测试使用实际 Excel 文件内容验证对象/动态列往返、XLS 读取、多级表头、Sheet 选择、分批边界、模板、错误位置及流的生命周期，
并覆盖校验注解、分组、继承和嵌套字段、小数边界、通用监听器及写入回调，
以及日期、文本、金额、代码值转换器的实际文件往返和错误边界。

本模块不涉及数据库迁移。
