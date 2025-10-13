package com.spring.ai.app.rag;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.UUID;

/**
 * 按方案B导入：Excel（标准问题、相似问题、默认回答）→ 每个问题变体一条文档
 * 字段：
 * - question_text: 变体文本
 * - canonical_id: 标准问题的稳定ID
 * - is_canonical: 是否标准问题
 * - answer: 默认回答
 */
@SpringBootApplication
public class RagEsApplication {
    private static final Logger logger = LoggerFactory.getLogger(RagEsApplication.class);
    // 使用 DataFormatter 更稳健地读取各种单元格类型（文本/数字/日期/公式）
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    public static void main(String[] args) {
        try {
            SpringApplication.run(RagEsApplication.class, args);
        } catch (Exception e) {
            logger.error("Application failed to start", e);
            throw e;
        }
    }

    // @Bean
    // CommandLineRunner importQaFromExcel(VectorStore vectorStore,
    //                                     @Value("${app.qa.excel-path:}") String excelFsPath,
    //                                     @Value("classpath:rag/问答知识库.xlsx") Resource excelResource) {
    //     return args -> {
    //         InputStream is = null;
    //         if (excelFsPath != null && !excelFsPath.isBlank()) {
    //             File file = new File(excelFsPath);
    //             if (file.exists()) {
    //                 is = new FileInputStream(file);
    //                 logger.info("从文件系统路径导入 Excel QA：{}", excelFsPath);
    //             } else {
    //                 logger.warn("文件系统路径不存在：{}，将尝试从 classpath 导入。", excelFsPath);
    //             }
    //         } else {
    //             logger.info("未配置 app.qa.excel-path，将尝试从 classpath 导入。");
    //         }
    //         if (is == null) {
    //             if (excelResource == null || !excelResource.exists()) {
    //                 logger.warn("Classpath 资源不存在：rag/问答知识库.xlsx，跳过导入。");
    //                 return;
    //             }
    //             is = excelResource.getInputStream();
    //             logger.info("从 classpath 导入 Excel QA：{}", excelResource);
    //         }

    //         try (InputStream autoClose = is; Workbook wb = WorkbookFactory.create(autoClose)) {
    //             Sheet sheet = wb.getSheetAt(0);
    //             if (sheet == null) {
    //                 logger.warn("Excel 首个 Sheet 为空，跳过导入。");
    //                 return;
    //             }

    //             // 表头映射：自动识别列索引，兼容不同命名
    //             Row headerRow = sheet.getRow(sheet.getFirstRowNum());
    //             int colStandard = findHeaderIndex(headerRow, Arrays.asList("标准问题","标准","canonical","canonical_question"));
    //             int colSimilar = findHeaderIndex(headerRow, Arrays.asList("相似问题","相似","similar","similar_question"));
    //             int colAnswer = findHeaderIndex(headerRow, Arrays.asList("默认回答","默认答案","回答","答案","answer","default_answer"));
    //             if (colStandard < 0 || colSimilar < 0 || colAnswer < 0) {
    //                 logger.warn("未识别到完整表头，使用默认列顺序：标准=0，相似=1，回答=2");
    //                 colStandard = (colStandard < 0) ? 0 : colStandard;
    //                 colSimilar = (colSimilar < 0) ? 1 : colSimilar;
    //                 colAnswer = (colAnswer < 0) ? 2 : colAnswer;
    //             }
    //             logger.info("表头列映射：标准问题={}, 相似问题={}, 默认回答={}", colStandard, colSimilar, colAnswer);

    //             // 去重：避免对同一标准问题重复写入 canonical 文档
    //             Set<String> canonicalSeen = new HashSet<>();

    //             // 统计
    //             int writtenBatches = 0;
    //             int nonEmptyRows = 0;
    //             int skippedBothEmpty = 0;
    //             int canonicalDocsCount = 0;
    //             int variantDocsCount = 0;

    //             for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
    //                 Row row = sheet.getRow(r);
    //                 if (row == null) continue;

    //                 String standard = getStringCell(row, colStandard);
    //                 String similar = getStringCell(row, colSimilar);
    //                 String answer = getStringCell(row, colAnswer);

    //                 if (r >= 93 && r <= 98) {
    //                     logger.info("Excel 行 {} 解析: 标准='{}', 相似='{}', 回答='{}'", r, standard, similar, answer);
    //                 }

    //                 // 若标准与相似均为空，但回答不为空，则允许按回答进行写入（避免遗漏仅有答案的条目）
    //                 if (isBlank(standard) && isBlank(similar) && isBlank(answer)) {
    //                     if (r >= 93 && r <= 98) {
    //                         logger.info("Excel 行 {} 跳过：标准、相似、回答均为空", r);
    //                     }
    //                     skippedBothEmpty++;
    //                     continue;
    //                 }
    //                 nonEmptyRows++;

    //                 // 生成稳定ID：优先标准，其次相似，最后回答
    //                 String canonicalId = stableId(!isBlank(standard) ? standard : (!isBlank(similar) ? similar : answer));

    //                 List<Document> docs = new ArrayList<>(2);

    //                 // 标准问题文档（仅首次写入）
    //                 if (!isBlank(standard) && canonicalSeen.add(standard.trim())) {
    //                     String content = buildQaContent(standard, answer);
    //                     Document canonicalDoc = new Document(content);
    //                     canonicalDoc.getMetadata().put("question_text", standard);
    //                     canonicalDoc.getMetadata().put("canonical_id", canonicalId);
    //                     canonicalDoc.getMetadata().put("is_canonical", true);
    //                     canonicalDoc.getMetadata().put("answer", answer);
    //                     docs.add(canonicalDoc);
    //                     canonicalDocsCount++;
    //                 }

    //                 // 相似问题文档（若存在）
    //                 if (!isBlank(similar)) {
    //                     String content = buildQaContent(similar, answer);
    //                     Document variantDoc = new Document(content);
    //                     variantDoc.getMetadata().put("question_text", similar);
    //                     variantDoc.getMetadata().put("canonical_id", canonicalId);
    //                     variantDoc.getMetadata().put("is_canonical", false);
    //                     variantDoc.getMetadata().put("answer", answer);
    //                     docs.add(variantDoc);
    //                     variantDocsCount++;
    //                 }

    //                 // 当标准与相似均为空但回答不为空时，按回答创建变体文档
    //                 if (isBlank(standard) && isBlank(similar) && !isBlank(answer)) {
    //                     String qFromAnswer = answer.length() > 40 ? answer.substring(0, 40) + "..." : answer;
    //                     String content = buildQaContent(qFromAnswer, answer);
    //                     Document variantDoc = new Document(content);
    //                     variantDoc.getMetadata().put("question_text", qFromAnswer);
    //                     variantDoc.getMetadata().put("canonical_id", canonicalId);
    //                     variantDoc.getMetadata().put("is_canonical", false);
    //                     variantDoc.getMetadata().put("answer", answer);
    //                     docs.add(variantDoc);
    //                     if (r >= 93 && r <= 98) {
    //                         logger.info("Excel 行 {} 回答回退写入：question_text='{}'", r, qFromAnswer);
    //                     }
    //                     variantDocsCount++;
    //                 }

    //                 if (!docs.isEmpty()) {
    //                     vectorStore.write(docs);
    //                     if (r >= 93 && r <= 98) {
    //                         logger.info("Excel 行 {} 写入：文档数={}", r, docs.size());
    //                     }
    //                     writtenBatches++;
    //                 }
    //             }
    //             logger.info("Excel QA 导入完成，共写入批次：{}（每批最多2条文档）", writtenBatches);
    //             logger.info("Excel 统计：工作表=\"{}\"，总行数(含表头)={}, 数据行={}, 写入批次={}, 标准文档={}, 相似文档={}, 空行过滤={}", sheet.getSheetName(), sheet.getLastRowNum() + 1, nonEmptyRows, writtenBatches, canonicalDocsCount, variantDocsCount, skippedBothEmpty);
    //         } catch (Exception e) {
    //             logger.error("导入 Excel 失败", e);
    //         }
    //     };
    // }

    private static String buildQaContent(String question, String answer) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(question)) {
            sb.append("Q: ").append(question.trim());
        }
        if (!isBlank(answer)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("A: ").append(answer.trim());
        }
        return sb.toString();
    }

    private static String getStringCell(Row row, int idx) {
        Cell cell = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        String v = DATA_FORMATTER.formatCellValue(cell);
        return v != null ? v.trim() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String stableId(String text) {
        if (text == null) return UUID.randomUUID().toString();
        return Integer.toHexString(text.trim().hashCode());
    }

    // 根据表头内容识别列索引，兼容常见中英文命名
    private static int findHeaderIndex(Row headerRow, List<String> candidates) {
        if (headerRow == null) return -1;
        short last = headerRow.getLastCellNum();
        for (int i = 0; i < last; i++) {
            String h = getStringCell(headerRow, i);
            if (isBlank(h)) continue;
            String hl = h.trim().toLowerCase();
            for (String c : candidates) {
                if (hl.equals(c.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }
}
