CREATE SCHEMA IF NOT EXISTS user_schema;
CREATE SCHEMA IF NOT EXISTS customer_schema;
CREATE SCHEMA IF NOT EXISTS product_schema;
CREATE SCHEMA IF NOT EXISTS order_schema;
CREATE SCHEMA IF NOT EXISTS delivery_schema;
CREATE SCHEMA IF NOT EXISTS payment_schema;

CREATE TABLE user_schema.tb_user
(
    id       uuid         not null,
    active   boolean      not null,
    email    varchar(255) not null unique,
    password varchar(255) not null,
    primary key (id)
);

CREATE TABLE user_schema.tb_user_roles
(
    user_id uuid not null,
    role    varchar(255) check (role in ('ADMIN', 'CUSTOMER')),
    constraint FK_user_roles foreign key (user_id) references user_schema.tb_user
);

CREATE TABLE customer_schema.tb_customer
(
    id      uuid         not null,
    user_id uuid         not null unique,
    active  boolean      not null,
    name    varchar(255) not null,
    email   varchar(255) not null unique,
    phone   varchar(255) not null,
    primary key (id)
);

CREATE TABLE product_schema.tb_product
(
    id              uuid           not null,
    version         bigint,
    sku             varchar(255)   not null unique,
    name            varchar(255)   not null,
    description     TEXT           not null,
    price           numeric(38, 2) not null,
    available_stock integer        not null,
    product_status  varchar(255)   not null,
    primary key (id)
);

CREATE TABLE order_schema.tb_order
(
    id              uuid                        not null,
    customer_id     uuid                        not null,
    customer_name   varchar(255)                not null,
    customer_email  varchar(255)                not null,
    total_price     numeric(38, 2)              not null,
    order_status    varchar(255)                not null,
    purchase_moment timestamp(6) with time zone not null,
    primary key (id)
);

CREATE TABLE order_schema.tb_order_item
(
    id           uuid           not null,
    order_id     uuid           not null,
    product_id   uuid           not null,
    product_name varchar(255)   not null,
    quantity     integer        not null,
    price        numeric(38, 2) not null,
    primary key (id),
    constraint FK_order_item_order foreign key (order_id) references order_schema.tb_order
);

CREATE TABLE payment_schema.tb_payment
(
    id             uuid                        not null,
    order_id       uuid                        not null,
    amount         numeric(38, 2)              not null,
    payment_status varchar(255)                not null,
    created_at     timestamp(6) with time zone not null,
    updated_at     timestamp(6) with time zone not null,
    primary key (id)
);

CREATE TABLE delivery_schema.tb_delivery
(
    id              uuid                        not null,
    order_id        uuid                        not null,
    tracking_code   varchar(255),
    delivery_status varchar(255)                not null,
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null,
    primary key (id)
);