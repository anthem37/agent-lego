-- 允许同一 provider + model_key 下保存多条「模型配置实例」（参数/密钥/baseUrl 可不同）。
-- 增加人类可读名称与备注，便于列表与智能体绑定时区分。

alter table if exists platform_models
    add column if not exists name text;

alter table if exists platform_models
    add column if not exists description text null;

-- 历史数据：用「提供方/模型标识/编号前缀」生成默认名称，避免全部叫「未命名」
update platform_models
set name = provider || ' / ' || model_key || ' / ' || left(id, 8)
where name is null
   or trim(name) = '';

alter table platform_models
    alter column name set not null;

-- 去掉 (provider, model_key) 唯一约束，否则无法创建多条同模型配置
drop index if exists ux_platform_models_provider_model_key;
