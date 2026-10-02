create table app_setting (
    setting_key   varchar(100) primary key,
    setting_value text,
    updated_at    timestamptz not null default now()
);

create table permission (
    code         varchar(60) primary key,
    label        varchar(160) not null,
    category     varchar(60)  not null,
    group_scoped boolean      not null default false,
    sort_order   integer      not null default 0
);

create table app_role (
    id          bigserial primary key,
    name        varchar(60) not null,
    description varchar(255),
    system_role boolean     not null default false,
    created_at  timestamptz not null default now()
);
create unique index ux_app_role_name on app_role (lower(name));

create table app_role_permission (
    role_id         bigint      not null references app_role (id) on delete cascade,
    permission_code varchar(60) not null references permission (code) on delete cascade,
    primary key (role_id, permission_code)
);

create table app_user (
    id                   bigserial primary key,
    username             varchar(80)  not null,
    password_hash        varchar(120) not null,
    first_name           varchar(120),
    last_name            varchar(120),
    email                varchar(200),
    enabled              boolean      not null default true,
    must_change_password boolean      not null default false,
    last_login_at        timestamptz,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now()
);
create unique index ux_app_user_username on app_user (lower(username));

create table app_user_role (
    user_id bigint not null references app_user (id) on delete cascade,
    role_id bigint not null references app_role (id) on delete cascade,
    primary key (user_id, role_id)
);

create table api_group (
    id            bigserial primary key,
    parent_id     bigint references api_group (id) on delete cascade,
    name          varchar(160)  not null,
    slug          varchar(180)  not null,
    description   text,
    documentation text,
    path          varchar(1200) not null,
    depth         integer       not null default 0,
    sort_order    integer       not null default 0,
    created_at    timestamptz   not null default now(),
    updated_at    timestamptz   not null default now()
);
create unique index ux_api_group_path on api_group (path);
create unique index ux_api_group_sibling on api_group (coalesce(parent_id, -1), lower(slug));
create index ix_api_group_parent on api_group (parent_id);

create table user_group_permission (
    id              bigserial primary key,
    user_id         bigint      not null references app_user (id) on delete cascade,
    group_id        bigint      not null references api_group (id) on delete cascade,
    permission_code varchar(60) not null references permission (code) on delete cascade,
    constraint ux_user_group_permission unique (user_id, group_id, permission_code)
);

create table api_service (
    id           bigserial primary key,
    group_id     bigint       not null references api_group (id) on delete cascade,
    name         varchar(200) not null,
    slug         varchar(220) not null,
    description  text,
    version      varchar(60),
    service_type varchar(20)  not null,
    status       varchar(20)  not null default 'ACTIVE',
    imported_at  timestamptz,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);
create unique index ux_api_service_slug on api_service (group_id, lower(slug));
create index ix_api_service_group on api_service (group_id);
create index ix_api_service_name on api_service (lower(name));

create table tag (
    id         bigserial primary key,
    name       varchar(60) not null,
    color      varchar(20),
    created_at timestamptz not null default now()
);
create unique index ux_tag_name on tag (lower(name));

create table api_service_tag (
    service_id bigint not null references api_service (id) on delete cascade,
    tag_id     bigint not null references tag (id) on delete cascade,
    primary key (service_id, tag_id)
);

create table spec_version (
    id             bigserial primary key,
    service_id     bigint      not null references api_service (id) on delete cascade,
    revision       integer     not null,
    version_label  varchar(80),
    spec_format    varchar(20) not null,
    source_type    varchar(20) not null,
    source_url     varchar(2000),
    file_name      varchar(255),
    content_type   varchar(120),
    size_bytes     bigint      not null default 0,
    checksum       varchar(64),
    raw_content    text        not null,
    title          varchar(300),
    description    text,
    is_current     boolean     not null default false,
    endpoint_count integer     not null default 0,
    model_count    integer     not null default 0,
    imported_at    timestamptz not null default now(),
    imported_by    bigint references app_user (id) on delete set null,
    imported_by_username varchar(80),
    notes          text,
    constraint ux_spec_version_revision unique (service_id, revision)
);
create index ix_spec_version_service on spec_version (service_id);
create index ix_spec_version_imported on spec_version (imported_at desc);

create table api_endpoint (
    id                  bigserial primary key,
    spec_version_id     bigint        not null references spec_version (id) on delete cascade,
    service_id          bigint        not null references api_service (id) on delete cascade,
    http_method         varchar(12)   not null,
    path                varchar(1000) not null,
    operation_id        varchar(255),
    summary             varchar(1000),
    description         text,
    tags                varchar(500),
    deprecated          boolean       not null default false,
    parameters_json     text,
    request_body_json   text,
    responses_json      text,
    security_json       text,
    servers_json        text,
    consumes            varchar(500),
    produces            varchar(500),
    grpc_service        varchar(255),
    grpc_method         varchar(255),
    grpc_request_type   varchar(255),
    grpc_response_type  varchar(255),
    grpc_streaming      varchar(30),
    sort_order          integer       not null default 0
);
create index ix_api_endpoint_spec on api_endpoint (spec_version_id);
create index ix_api_endpoint_service on api_endpoint (service_id);
create index ix_api_endpoint_path on api_endpoint (lower(path));

create table api_model (
    id              bigserial primary key,
    spec_version_id bigint       not null references spec_version (id) on delete cascade,
    service_id      bigint       not null references api_service (id) on delete cascade,
    name            varchar(255) not null,
    kind            varchar(30)  not null default 'SCHEMA',
    description     text,
    schema_json     text,
    sort_order      integer      not null default 0
);
create index ix_api_model_spec on api_model (spec_version_id);
create index ix_api_model_name on api_model (lower(name));

create table contact (
    id         bigserial primary key,
    group_id   bigint references api_group (id) on delete cascade,
    service_id bigint references api_service (id) on delete cascade,
    name       varchar(160) not null,
    email      varchar(200),
    team       varchar(160),
    notes      text,
    created_at timestamptz  not null default now(),
    constraint ck_contact_target check (
        (case when group_id is null then 0 else 1 end)
      + (case when service_id is null then 0 else 1 end) = 1)
);
create index ix_contact_group on contact (group_id);
create index ix_contact_service on contact (service_id);

create table service_environment (
    id                bigserial primary key,
    service_id        bigint      not null references api_service (id) on delete cascade,
    name              varchar(30) not null,
    base_url          varchar(1000),
    health_check_url  varchar(1000),
    spec_url          varchar(1000),
    health_status     varchar(20) not null default 'UNKNOWN',
    health_checked_at timestamptz,
    health_detail     varchar(500),
    sort_order        integer     not null default 0,
    constraint ux_service_environment unique (service_id, name)
);

create table external_link (
    id         bigserial primary key,
    service_id bigint        not null references api_service (id) on delete cascade,
    label      varchar(120)  not null,
    url        varchar(1000) not null,
    kind       varchar(40),
    sort_order integer       not null default 0
);
create index ix_external_link_service on external_link (service_id);

create table service_comment (
    id           bigserial primary key,
    service_id   bigint      not null references api_service (id) on delete cascade,
    endpoint_id  bigint references api_endpoint (id) on delete cascade,
    author_id    bigint references app_user (id) on delete set null,
    author_label varchar(160),
    body         text        not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz
);
create index ix_service_comment_service on service_comment (service_id);
create index ix_service_comment_endpoint on service_comment (endpoint_id);

create table favorite (
    user_id    bigint      not null references app_user (id) on delete cascade,
    service_id bigint      not null references api_service (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, service_id)
);

create table audit_log (
    id          bigserial primary key,
    created_at  timestamptz not null default now(),
    username    varchar(80),
    action      varchar(80) not null,
    entity_type varchar(60),
    entity_id   varchar(60),
    detail      text,
    ip_address  varchar(60),
    outcome     varchar(20) not null default 'OK'
);
create index ix_audit_log_created on audit_log (created_at desc);
create index ix_audit_log_username on audit_log (username);

insert into permission (code, label, category, group_scoped, sort_order) values
    ('CATALOG_VIEW',      'Visualizzazione catalogo', 'Catalogo',      true,  10),
    ('GROUP_CREATE',      'Creazione gruppi',         'Catalogo',      true,  20),
    ('GROUP_EDIT',        'Modifica gruppi',          'Catalogo',      true,  30),
    ('GROUP_DELETE',      'Eliminazione gruppi',      'Catalogo',      true,  40),
    ('SERVICE_CREATE',    'Creazione servizi',        'Servizi',       true,  50),
    ('SERVICE_EDIT',      'Modifica servizi',         'Servizi',       true,  60),
    ('SERVICE_DELETE',    'Eliminazione servizi',     'Servizi',       true,  70),
    ('IMPORT_SWAGGER',    'Import Swagger/OpenAPI',   'Import',        true,  80),
    ('IMPORT_GRPC',       'Import gRPC (proto)',      'Import',        true,  90),
    ('API_TEST',          'Test API',                 'Strumenti',     true, 100),
    ('SPEC_DOWNLOAD',     'Download specifiche',      'Strumenti',     true, 110),
    ('COMMENT_WRITE',     'Inserimento commenti',     'Collaborazione',true, 120),
    ('USER_MANAGE',       'Gestione utenti',          'Amministrazione', false, 130),
    ('PERMISSION_MANAGE', 'Gestione permessi',        'Amministrazione', false, 140),
    ('SETTINGS_MANAGE',   'Gestione impostazioni',    'Amministrazione', false, 150);

insert into app_role (name, description, system_role) values
    ('ADMIN',  'Accesso completo a catalogo e amministrazione', true),
    ('EDITOR', 'Gestisce gruppi, servizi e import',            true),
    ('VIEWER', 'Consultazione del catalogo',                   true);

insert into app_role_permission (role_id, permission_code)
select r.id, p.code from app_role r cross join permission p where r.name = 'ADMIN';

insert into app_role_permission (role_id, permission_code)
select r.id, p.code
from app_role r
         cross join permission p
where r.name = 'EDITOR'
  and p.code in ('CATALOG_VIEW', 'GROUP_CREATE', 'GROUP_EDIT', 'SERVICE_CREATE', 'SERVICE_EDIT',
                 'IMPORT_SWAGGER', 'IMPORT_GRPC', 'API_TEST', 'SPEC_DOWNLOAD', 'COMMENT_WRITE');

insert into app_role_permission (role_id, permission_code)
select r.id, p.code
from app_role r
         cross join permission p
where r.name = 'VIEWER'
  and p.code in ('CATALOG_VIEW', 'SPEC_DOWNLOAD', 'COMMENT_WRITE');

insert into app_setting (setting_key, setting_value) values
    ('setup.completed', 'false'),
    ('app.name', 'API Catalog'),
    ('app.logo.path', ''),
    ('app.base.url', 'http://localhost:8080'),
    ('upload.max.bytes', '10485760'),
    ('apitest.timeout.seconds', '30'),
    ('healthcheck.interval.minutes', '10');

insert into tag (name, color) values
    ('Internal', '#6c757d'),
    ('Public', '#0d6efd'),
    ('Deprecated', '#dc3545'),
    ('Experimental', '#fd7e14'),
    ('Partner', '#6f42c1'),
    ('PSD2', '#198754'),
    ('eIDAS', '#20c997');
