-- create-db.sql

-- temperature

drop table temperature_measurements;

create table temperature_measurements (id int, date timestamp with time zone, device_id varchar(30), temperature_millic int);

drop sequence temperature_measurements_seq;

create sequence temperature_measurements_seq;

alter table temperature_measurements alter column id set default nextval('temperature_measurements_seq');

-- pressure

drop table pressure_measurements;

create table pressure_measurements (id int, date timestamp with time zone, device_id varchar(30), pressure_pa int);

drop sequence pressure_measurements_seq;

create sequence pressure_measurements_seq;

alter table pressure_measurements alter column id set default nextval('pressure_measurements_seq');

