alter table customer_scan_queue drop constraint ck_queue_status;
alter table customer_scan_queue add constraint ck_queue_status check (status in ('PENDING', 'PROCESSED', 'FAILED'));