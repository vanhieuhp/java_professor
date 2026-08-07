alter table customer_scan_queue drop column status;
alter table customer_scan_queue add column status varchar (20) default 'PENDING' not null;