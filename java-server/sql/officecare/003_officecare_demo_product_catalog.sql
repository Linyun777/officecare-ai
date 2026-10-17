-- OfficeCare AI 单设备 MVP 演示数据
-- 前置条件：已执行 002_officecare_product_catalog.sql。
-- 作用：创建 Brother 品牌和 DCP-L2550DW 产品型号演示数据。
-- 特性：脚本可重复执行，不覆盖已经存在的品牌或型号数据。

-- ----------------------------
-- 1、Brother品牌
-- ----------------------------
insert into oc_brand
    (brand_code, brand_name, description, sort_order, status, del_flag,
     create_by, create_time, remark)
select
    'BROTHER',
    '兄弟（Brother）',
    '办公打印、扫描与复印设备品牌',
    1,
    '0',
    '0',
    'admin',
    sysdate(),
    'OfficeCare单设备MVP演示数据'
where not exists (
    select 1
    from oc_brand
    where brand_code = 'BROTHER'
);

set @oc_demo_brother_brand_id := (
    select brand_id
    from oc_brand
    where brand_code = 'BROTHER'
      and del_flag = '0'
    order by brand_id
    limit 1
);

-- ----------------------------
-- 2、DCP-L2550DW产品型号
-- ----------------------------
insert into oc_product_model
    (brand_id, model_code, model_name, series_name, description, sort_order,
     status, del_flag, create_by, create_time, remark)
select
    @oc_demo_brother_brand_id,
    'DCP-L2550DW',
    'DCP-L2550DW 黑白激光多功能一体机',
    'DCP',
    '支持打印、复印、扫描、自动双面和网络连接的黑白激光多功能一体机',
    1,
    '0',
    '0',
    'admin',
    sysdate(),
    'OfficeCare首个RAG知识库目标型号'
where @oc_demo_brother_brand_id is not null
  and not exists (
      select 1
      from oc_product_model
      where brand_id = @oc_demo_brother_brand_id
        and model_code = 'DCP-L2550DW'
  );

-- ----------------------------
-- 3、执行结果检查
-- ----------------------------
select
    b.brand_id,
    b.brand_code,
    b.brand_name,
    b.status as brand_status,
    m.model_id,
    m.model_code,
    m.model_name,
    m.series_name,
    m.status as model_status
from oc_brand b
left join oc_product_model m
    on m.brand_id = b.brand_id
   and m.del_flag = '0'
where b.brand_code = 'BROTHER'
  and b.del_flag = '0';
