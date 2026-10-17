package com.ruoyi.web.controller.officecare;

import java.io.InputStream;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.officecare.domain.OcDocument;
import com.ruoyi.officecare.domain.OcDocumentVersion;
import com.ruoyi.officecare.domain.vo.DocumentDownload;
import com.ruoyi.officecare.service.IOcDocumentIndexingService;
import com.ruoyi.officecare.service.IOcDocumentProcessingService;
import com.ruoyi.officecare.service.IOcDocumentService;

/**
 * OfficeCare知识文档管理。
 *
 * @author officecare
 */
@RestController
@RequestMapping("/officecare/document")
public class OcDocumentController extends BaseController {
    private final IOcDocumentService documentService;

    private final IOcDocumentProcessingService processingService;

    private final IOcDocumentIndexingService indexingService;

    public OcDocumentController(IOcDocumentService documentService,
                                IOcDocumentProcessingService processingService,
                                IOcDocumentIndexingService indexingService) {
        this.documentService = documentService;
        this.processingService = processingService;
        this.indexingService = indexingService;
    }

    /**
     * 分页查询文档
     * @param document
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:list')")
    @GetMapping("/list")
    public TableDataInfo list(OcDocument document) {
        startPage();
        List<OcDocument> list = documentService.selectDocumentList(document);
        return getDataTable(list);
    }

    /**
     * 获取文档详情
     * @param documentId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:query')")
    @GetMapping("/{documentId}")
    public AjaxResult getInfo(@PathVariable Long documentId) {
        return success(documentService.selectDocumentById(documentId));
    }

    /**
     * 获取历史版本
     * @param documentId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:query')")
    @GetMapping("/{documentId}/versions")
    public AjaxResult versions(@PathVariable Long documentId) {
        return success(documentService.selectVersionList(documentId));
    }

    /**
     * 获取指定版本的解析任务
     * @param documentVersionId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:query')")
    @GetMapping("/versions/{documentVersionId}/task")
    public AjaxResult processingTask(@PathVariable Long documentVersionId) {
        return success(processingService.selectTaskByVersionId(documentVersionId));
    }

    /**
     * 启动或重试指定版本的解析任务
     * @param documentVersionId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:parse')")
    @Log(title = "OfficeCare文档解析", businessType = BusinessType.UPDATE)
    @PostMapping("/versions/{documentVersionId}/parse")
    public AjaxResult parseVersion(@PathVariable Long documentVersionId) {
        return success(processingService.submitVersion(documentVersionId, getUsername()));
    }

    /**
     * 获取指定版本的向量索引任务
     * @param documentVersionId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:query')")
    @GetMapping("/versions/{documentVersionId}/index-task")
    public AjaxResult indexTask(@PathVariable Long documentVersionId) {
        return success(indexingService.selectTaskByVersionId(documentVersionId));
    }

    /**
     * 启动或重试指定版本的向量索引任务
     * @param documentVersionId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:index')")
    @Log(title = "OfficeCare文档索引", businessType = BusinessType.UPDATE)
    @PostMapping("/versions/{documentVersionId}/index")
    public AjaxResult indexVersion(@PathVariable Long documentVersionId) {
        return success(indexingService.submitVersion(documentVersionId, getUsername()));
    }

    /**
     * 上传新文档
     * @param knowledgeBaseId
     * @param documentTitle
     * @param remark
     * @param file
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:upload')")
    @Log(title = "OfficeCare知识文档", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam Long knowledgeBaseId,
                             @RequestParam(required = false) String documentTitle,
                             @RequestParam(required = false) String remark,
                             @RequestParam("file") MultipartFile file) {
        return success(documentService.uploadDocument(knowledgeBaseId, documentTitle,
                remark, file, getUsername()));
    }

    /**
     * 上传新版本
     * @param documentId
     * @param file
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:upload')")
    @Log(title = "OfficeCare文档新版本", businessType = BusinessType.INSERT)
    @PostMapping("/{documentId}/versions")
    public AjaxResult uploadVersion(@PathVariable Long documentId,
                                    @RequestParam("file") MultipartFile file) {
        return success(documentService.uploadNewVersion(documentId, file, getUsername()));
    }

    /**
     * 修改文档信息
     * @param document
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:edit')")
    @Log(title = "OfficeCare知识文档", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OcDocument document) {
        document.setUpdateBy(getUsername());
        return toAjax(documentService.updateDocument(document));
    }

    /**
     * 删除文档
     * @param documentIds
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:remove')")
    @Log(title = "OfficeCare知识文档", businessType = BusinessType.DELETE)
    @DeleteMapping("/{documentIds}")
    public AjaxResult remove(@PathVariable Long[] documentIds) {
        return toAjax(documentService.deleteDocumentByIds(documentIds, getUsername()));
    }

    /**
     * 下载指定版本
     * @param documentVersionId
     * @param response
     * @throws Exception
     */
    @PreAuthorize("@ss.hasPermi('officecare:document:download')")
    @Log(title = "OfficeCare文档下载", businessType = BusinessType.EXPORT)
    @GetMapping("/versions/{documentVersionId}/download")
    public void download(@PathVariable Long documentVersionId, HttpServletResponse response)
            throws Exception {
        DocumentDownload download = documentService.openDocumentVersion(documentVersionId);
        OcDocumentVersion version = download.getVersion();
        response.setContentType(StringUtils.defaultIfEmpty(version.getContentType(),
                MediaType.APPLICATION_OCTET_STREAM_VALUE));
        response.setContentLengthLong(version.getFileSize());
        FileUtils.setAttachmentResponseHeader(response, version.getOriginalFileName());
        try (InputStream inputStream = download.getInputStream()) {
            inputStream.transferTo(response.getOutputStream());
        }
    }
}
