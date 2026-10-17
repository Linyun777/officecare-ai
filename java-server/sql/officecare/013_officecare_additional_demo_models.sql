-- OfficeCare AI 多型号RAG演示数据
-- 前置条件：已执行002、003、004脚本。
-- 作用：新增Brother DCP-L2535DW、MFC-7895DW产品型号及各自独立知识库。
-- 特性：脚本可重复执行；不覆盖既有型号或知识库；不创建文档、版本、解析任务和向量索引。

-- ----------------------------
-- 1、定位Brother品牌
-- ----------------------------
set @oc_brother_brand_id := (
    select brand_id
    from oc_brand
    where brand_code = 'BROTHER'
      and del_flag = '0'
    order by brand_id
    limit 1
);

-- ----------------------------
-- 2、DCP-L2535DW产品型号
-- ----------------------------
insert into oc_product_model
    (brand_id, model_code, model_name, series_name, description, sort_order,
     status, del_flag, create_by, create_time, remark)
select
    @oc_brother_brand_id,
    'DCP-L2535DW',
    'DCP-L2535DW 黑白激光多功能一体机',
    'DCP',
    '支持打印、复印和扫描；用于验证同一DCP系列不同型号的知识隔离',
    2,
    '0',
    '0',
    'admin',
    sysdate(),
    'OfficeCare多型号RAG演示数据'
where @oc_brother_brand_id is not null
  and not exists (
      select 1
      from oc_product_model
      where brand_id = @oc_brother_brand_id
        and model_code = 'DCP-L2535DW'
  );

-- ----------------------------
-- 3、MFC-7895DW产品型号
-- ----------------------------
insert into oc_product_model
    (brand_id, model_code, model_name, series_name, description, sort_order,
     status, del_flag, create_by, create_time, remark)
select
    @oc_brother_brand_id,
    'MFC-7895DW',
    'MFC-7895DW 黑白激光多功能一体机',
    'MFC',
    '支持打印、复印、扫描和传真；用于验证DCP与MFC系列的知识和能力隔离',
    3,
    '0',
    '0',
    'admin',
    sysdate(),
    'OfficeCare多型号RAG演示数据'
where @oc_brother_brand_id is not null
  and not exists (
      select 1
      from oc_product_model
      where brand_id = @oc_brother_brand_id
        and model_code = 'MFC-7895DW'
  );

-- ----------------------------
-- 4、定位新增型号
-- ----------------------------
set @oc_dcp_l2535dw_model_id := (
    select model_id
    from oc_product_model
    where brand_id = @oc_brother_brand_id
      and model_code = 'DCP-L2535DW'
      and del_flag = '0'
    order by model_id
    limit 1
);

set @oc_mfc_7895dw_model_id := (
    select model_id
    from oc_product_model
    where brand_id = @oc_brother_brand_id
      and model_code = 'MFC-7895DW'
      and del_flag = '0'
    order by model_id
    limit 1
);

-- ----------------------------
-- 5、DCP-L2535DW独立知识库
-- ----------------------------
insert into oc_knowledge_base
    (knowledge_base_code, knowledge_base_name, brand_id, model_id, description,
     sort_order, status, del_flag, create_by, create_time, remark)
select
    'BROTHER_DCP_L2535DW',
    'Brother DCP-L2535DW知识库',
    @oc_brother_brand_id,
    @oc_dcp_l2535dw_model_id,
    '用于保存DCP-L2535DW官方手册、FAQ和故障处理资料',
    2,
    '0',
    '0',
    'admin',
    sysdate(),
    'OfficeCare多型号RAG演示知识库'
where @oc_brother_brand_id is not null
  and @oc_dcp_l2535dw_model_id is not null
  and not exists (
      select 1
      from oc_knowledge_base
      where knowledge_base_code = 'BROTHER_DCP_L2535DW'
  );

-- ----------------------------
-- 6、MFC-7895DW独立知识库
-- ----------------------------
insert into oc_knowledge_base
    (knowledge_base_code, knowledge_base_name, brand_id, model_id, description,
     sort_order, status, del_flag, create_by, create_time, remark)
select
    'BROTHER_MFC_7895DW',
    'Brother MFC-7895DW知识库',
    @oc_brother_brand_id,
    @oc_mfc_7895dw_model_id,
    '用于保存MFC-7895DW官方手册、FAQ和故障处理资料',
    3,
    '0',
    '0',
    'admin',
    sysdate(),
    'OfficeCare多型号RAG演示知识库'
where @oc_brother_brand_id is not null
  and @oc_mfc_7895dw_model_id is not null
  and not exists (
      select 1
      from oc_knowledge_base
      where knowledge_base_code = 'BROTHER_MFC_7895DW'
  );

-- ----------------------------
-- 7、执行结果检查
-- ----------------------------
select
    b.brand_id,
    b.brand_code,
    b.brand_name,
    m.model_id,
    m.model_code,
    m.model_name,
    m.series_name,
    m.status as model_status,
    kb.knowledge_base_id,
    kb.knowledge_base_code,
    kb.knowledge_base_name,
    kb.status as knowledge_base_status
from oc_brand b
join oc_product_model m
    on m.brand_id = b.brand_id
   and m.del_flag = '0'
left join oc_knowledge_base kb
    on kb.brand_id = b.brand_id
   and kb.model_id = m.model_id
   and kb.del_flag = '0'
where b.brand_code = 'BROTHER'
  and b.del_flag = '0'
  and m.model_code in ('DCP-L2535DW', 'MFC-7895DW')
order by m.sort_order, kb.knowledge_base_id;
