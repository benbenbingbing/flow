package com.workflow.excel.converter;

import com.alibaba.excel.read.metadata.holder.ReadWorkbookHolder;
import com.alibaba.excel.read.metadata.holder.xls.XlsReadWorkbookHolder;
import com.alibaba.excel.read.metadata.holder.xlsx.XlsxReadWorkbookHolder;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.apache.poi.hssf.eventusermodel.AbortableHSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.BOFRecord;
import org.apache.poi.hssf.record.DateWindow1904Record;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/** 仅读取工作簿日期基准元数据，不创建完整 POI Workbook，避免大文件退回全量内存解析。 */
public final class DateWorkbookSupport {
    // 使用生命周期随 reader 结束的容器作为弱键，值不反向引用容器，避免缓存保留已完成的上传文件。
    private static final Map<Object, Boolean> DATE_SYSTEMS = Collections.synchronizedMap(new WeakHashMap<>());

    private DateWorkbookSupport() {
    }

    /** 动态读取与 DTO 转换共用实际工作簿日期基准，不依赖调用方手动猜测。 */
    public static boolean use1904(ReadWorkbookHolder holder) {
        Object container;
        if (holder instanceof XlsxReadWorkbookHolder xlsx) {
            container = xlsx.getOpcPackage();
        } else if (holder instanceof XlsReadWorkbookHolder xls) {
            container = xls.getPoifsFileSystem();
        } else {
            return Boolean.TRUE.equals(holder.globalConfiguration().getUse1904windowing());
        }
        Boolean cached = DATE_SYSTEMS.get(container);
        if (cached != null) {
            return cached;
        }
        try {
            boolean result = holder instanceof XlsxReadWorkbookHolder xlsx ? readXlsx(xlsx)
                    : readXls((XlsReadWorkbookHolder) holder);
            DATE_SYSTEMS.put(container, result);
            return result;
        } catch (Exception error) {
            // 不能把读取元数据失败当作默认 1900，否则会把错误日期当作成功导入的数据。
            throw new IllegalArgumentException("无法识别工作簿的日期基准", error);
        }
    }

    private static boolean readXlsx(XlsxReadWorkbookHolder holder) throws Exception {
        WorkbookPropertiesHandler handler = new WorkbookPropertiesHandler();
        // POI 的 XMLHelper 禁用外部实体和 DTD；只解析 workbook.xml，不读取工作表数据。
        XMLReader reader = XMLHelper.newXMLReader();
        reader.setContentHandler(handler);
        try (InputStream input = new XSSFReader(holder.getOpcPackage()).getWorkbookData()) {
            reader.parse(new InputSource(input));
        }
        return handler.use1904;
    }

    private static boolean readXls(XlsReadWorkbookHolder holder) throws Exception {
        DateWindowListener listener = new DateWindowListener();
        HSSFRequest request = new HSSFRequest();
        request.addListenerForAllRecords(listener);
        // 找到日期基准记录，或进入第一张表时即停止，不再次扫描所有业务数据行。
        new HSSFEventFactory().abortableProcessWorkbookEvents(request, holder.getPoifsFileSystem());
        return listener.use1904;
    }

    private static final class WorkbookPropertiesHandler extends DefaultHandler {
        private boolean use1904;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("workbookPr".equals(localName)) {
                String value = attributes.getValue("date1904");
                if (value != null && !value.equals("true") && !value.equals("false")
                        && !value.equals("1") && !value.equals("0")) {
                    throw new IllegalArgumentException("工作簿的 date1904 标记不合法");
                }
                use1904 = "true".equals(value) || "1".equals(value);
            }
        }
    }

    private static final class DateWindowListener extends AbortableHSSFListener {
        private boolean use1904;

        @Override
        public short abortableProcessRecord(Record record) {
            if (record instanceof DateWindow1904Record dateWindow) {
                if (dateWindow.getWindowing() != 0 && dateWindow.getWindowing() != 1) {
                    throw new IllegalArgumentException("工作簿的日期基准记录不合法");
                }
                use1904 = dateWindow.getWindowing() == 1;
                return 1;
            }
            return record instanceof BOFRecord bof && bof.getType() == BOFRecord.TYPE_WORKSHEET ? (short) 1 : 0;
        }
    }
}
