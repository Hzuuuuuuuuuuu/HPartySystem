package com.hparty.system.report.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Excel 导出工具。
 *
 * <p><b>分批查询 + 流式写出</b>：调用方传入一个「按页取数」的函数，
 * 本工具每取满一批就 {@code writer.write(...)} 一次并释放该批内存，
 * 全程不会把全表读进 JVM。批次大小取 {@link #BATCH_SIZE}（与
 * {@code PageQuery.MAX_PAGE_SIZE} 一致，避开分页插件的 500 条上限）。</p>
 *
 * <p><b>中文文件名</b>：{@code Content-Disposition} 用 RFC 5987 的
 * {@code filename*=UTF-8''...} 形式，并按 UTF-8 做 URL 编码，
 * 否则浏览器拿到的是乱码文件名。</p>
 */
public final class ExcelExportUtils {

    /** 每批取数条数，与分页插件 maxLimit 对齐 */
    public static final int BATCH_SIZE = 500;

    private ExcelExportUtils() {
    }

    /** 按页取数：页码从 1 开始，返回该页记录。 */
    @FunctionalInterface
    public interface PageFetcher<T> {
        List<T> fetch(int pageNum, int pageSize);
    }

    /**
     * 设置下载响应头。
     *
     * @param response HttpServletResponse
     * @param fileName 不带扩展名的中文文件名
     */
    public static void prepare(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded + ".xlsx");
        // 让前端能读到文件名（跨域/代理场景下默认不暴露）
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
    }

    /**
     * 流式写出 Excel。
     *
     * @param response  HTTP 响应
     * @param fileName  不带扩展名的文件名
     * @param sheetName sheet 名
     * @param head      表头类型（带 EasyExcel 注解）
     * @param fetcher   按页取数函数
     * @param converter 记录 → 导出行；第二个参数是**本批第一条的全局序号 - 1**，
     *                  用于跨批连续编号
     */
    public static <T, V> void writeStreaming(HttpServletResponse response, String fileName, String sheetName,
                                             Class<V> head, PageFetcher<T> fetcher,
                                             BiFunction<List<T>, Integer, List<V>> converter)
            throws IOException {
        prepare(response, fileName);
        ExcelWriter writer = EasyExcel.write(response.getOutputStream(), head).build();
        try {
            WriteSheet sheet = EasyExcel.writerSheet(sheetName).build();
            int pageNum = 1;
            int written = 0;
            boolean headerWritten = false;
            while (true) {
                List<T> records = fetcher.fetch(pageNum, BATCH_SIZE);
                if (records == null || records.isEmpty()) {
                    break;
                }
                writer.write(converter.apply(records, written), sheet);
                headerWritten = true;
                written += records.size();
                pageNum++;
                // 不满一批说明已经到底，省掉一次空查询
                if (records.size() < BATCH_SIZE) {
                    break;
                }
            }
            if (!headerWritten) {
                // 无数据时也要产出一份带表头的空表，否则 finish() 出来的是没有 sheet 的坏文件
                writer.write(Collections.emptyList(), sheet);
            }
        } finally {
            writer.finish();
        }
    }
}
