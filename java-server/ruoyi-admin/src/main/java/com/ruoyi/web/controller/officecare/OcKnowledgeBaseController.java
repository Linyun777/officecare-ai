package com.ruoyi.web.controller.officecare;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.officecare.domain.OcKnowledgeBase;
import com.ruoyi.officecare.service.IOcKnowledgeBaseService;

/**
 * OfficeCare知识库管理。
 *
 * @author officecare
 */
@RestController
@RequestMapping("/officecare/knowledge-base")
public class OcKnowledgeBaseController extends BaseController {
    private final IOcKnowledgeBaseService knowledgeBaseService;


    public OcKnowledgeBaseController(IOcKnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }


    /**
     * 分页查询知识库
     * @param knowledgeBase
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:knowledge-base:list')")
    @GetMapping("/list")
    public TableDataInfo list(OcKnowledgeBase knowledgeBase) {
        startPage();
        List<OcKnowledgeBase> list = knowledgeBaseService.selectKnowledgeBaseList(knowledgeBase);
        return getDataTable(list);
    }

    /**
     * 获取可选知识库
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:knowledge-base:list')")
    @GetMapping("/options")
    public AjaxResult options() {
        return success(knowledgeBaseService.selectKnowledgeBaseOptions());
    }

    /**
     * 获取知识库详情
     * @param knowledgeBaseId
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:knowledge-base:query')")
    @GetMapping("/{knowledgeBaseId}")
    public AjaxResult getInfo(@PathVariable Long knowledgeBaseId) {
        return success(knowledgeBaseService.selectKnowledgeBaseById(knowledgeBaseId));
    }

    /**
     * 新增知识库
     * @param knowledgeBase
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:knowledge-base:add')")
    @Log(title = "OfficeCare知识库", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody OcKnowledgeBase knowledgeBase) {
        knowledgeBase.setCreateBy(getUsername());
        return toAjax(knowledgeBaseService.insertKnowledgeBase(knowledgeBase));
    }

    /**
     * 修改知识库
     * @param knowledgeBase
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:knowledge-base:edit')")
    @Log(title = "OfficeCare知识库", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody OcKnowledgeBase knowledgeBase) {
        knowledgeBase.setUpdateBy(getUsername());
        return toAjax(knowledgeBaseService.updateKnowledgeBase(knowledgeBase));
    }

    /**
     * 删除知识库
     * @param knowledgeBaseIds
     * @return
     */
    @PreAuthorize("@ss.hasPermi('officecare:knowledge-base:remove')")
    @Log(title = "OfficeCare知识库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{knowledgeBaseIds}")
    public AjaxResult remove(@PathVariable Long[] knowledgeBaseIds) {
        return toAjax(knowledgeBaseService.deleteKnowledgeBaseByIds(
                knowledgeBaseIds, getUsername()));
    }
}
