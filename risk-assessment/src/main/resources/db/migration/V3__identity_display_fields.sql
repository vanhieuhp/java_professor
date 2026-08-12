-- Ban chieu dang giu DUY NHAT gia tri da chuan hoa: du de SO KHOP, khong du de HIEN THI.
-- Bao cao screening doi ten/SDT/so GTTT "lay tu CSDL PCRT", ma full_name_norm cua
-- "Nguyen Van An" la "NGUYEN VAN AN" — mat dau tieng Viet. Cot chuan hoa la cach hieu du
-- lieu cua PCRT, khong phai du lieu goc; do la hai thu khac nhau nen tach ra hai cot.

ALTER TABLE pcrt_customer_identity
    ADD COLUMN full_name VARCHAR(320),
    ADD COLUMN phone     VARCHAR(50),
    ADD COLUMN id_number VARCHAR(50);

COMMENT ON COLUMN pcrt_customer_identity.full_name IS 'Ho ten NGUYEN VAN nhu Core luu — chi de hien thi, KHONG dung de so khop';
COMMENT ON COLUMN pcrt_customer_identity.phone IS 'So dien thoai nguyen van nhu Core luu — chi de hien thi';
COMMENT ON COLUMN pcrt_customer_identity.id_number IS 'So GTTT nguyen van nhu Core luu — chi de hien thi';

-- Dong cu mang NULL cho toi lan dong bo ke tiep. De NULL chu khong chep tu cot chuan hoa:
-- chep sang se tao ra mot cai ten khong dau nhin y het du lieu that, va khong con cach nao
-- phan biet "chua dong bo" voi "Core luu nhu vay". Chay full sync de nap lai.
