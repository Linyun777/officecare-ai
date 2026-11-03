-- M2-2: isolated build candidates. This does not change the published knowledge.
create table if not exists oc_document_index_generation
(
    generation_id       varchar(32)  not null comment 'Java生成的不可变代次ID',
    document_id         bigint       not null,
    document_version_id bigint       not null,
    knowledge_base_id   bigint       not null,
    brand_id            bigint       not null,
    model_id            bigint       not null,
    parse_id            varchar(64)  not null comment '保存的解析JSON字节摘要',
    parsed_object_key   varchar(500) not null,
    profile             varchar(32)  not null comment 'legacy或structured',
    status              varchar(16)  not null comment 'BUILDING/READY/FAILED/DELETED',
    manifest_json       json         null comment '构建身份、空间、参数和数量',
    error_message       varchar(500) null,
    create_by           varchar(64)  not null default '',
    create_time         datetime     not null default current_timestamp,
    update_time         datetime     null,
    primary key (generation_id),
    key idx_oc_generation_version (document_version_id, create_time),
    key idx_oc_generation_document (document_id)
) engine=InnoDB comment='文档不可变索引代次';
