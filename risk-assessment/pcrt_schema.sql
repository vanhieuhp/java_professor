--
-- PostgreSQL database dump
--

\restrict kQZeWa75gc951HHSMfAfd80Ah9sUxfavzzJTRbh8FG6W7FmWy8hPVguOWbhonIa

-- Dumped from database version 16.14 (Debian 16.14-1.pgdg13+1)
-- Dumped by pg_dump version 16.14 (Debian 16.14-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: core; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA core;


SET default_table_access_method = heap;

--
-- Name: risk_update_log; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.risk_update_log (
    id bigint NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    cif character varying(50) NOT NULL,
    risk_level character varying(20) NOT NULL,
    risk_score smallint NOT NULL,
    reason character varying(255) NOT NULL,
    cif_locked boolean NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: risk_update_log_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

CREATE SEQUENCE core.risk_update_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: risk_update_log_id_seq; Type: SEQUENCE OWNED BY; Schema: core; Owner: -
--

ALTER SEQUENCE core.risk_update_log_id_seq OWNED BY core.risk_update_log.id;


--
-- Name: wallet_customer; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.wallet_customer (
    id bigint NOT NULL,
    cif character varying(50) NOT NULL,
    customer_type character varying(2) NOT NULL,
    status character varying(20) NOT NULL,
    full_name character varying(255) NOT NULL,
    dob date,
    phone character varying(30),
    id_number character varying(50),
    old_id_number character varying(50),
    country_code character varying(2),
    occupation_code character varying(50),
    position_code character varying(50),
    risk_score smallint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    update_time timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_core_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'APPROVED'::character varying, 'LOCKED'::character varying, 'CLOSED'::character varying])::text[]))),
    CONSTRAINT ck_core_type CHECK (((customer_type)::text = ANY ((ARRAY['CN'::character varying, 'TC'::character varying])::text[])))
);


--
-- Name: wallet_customer_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

CREATE SEQUENCE core.wallet_customer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: wallet_customer_id_seq; Type: SEQUENCE OWNED BY; Schema: core; Owner: -
--

ALTER SEQUENCE core.wallet_customer_id_seq OWNED BY core.wallet_customer.id;


--
-- Name: customer_risk_result; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_risk_result (
    id bigint NOT NULL,
    cif character varying(50) NOT NULL,
    scan_batch_id uuid NOT NULL,
    scan_queue_id bigint NOT NULL,
    trigger_type character varying(4) NOT NULL,
    risk_level character varying(20) NOT NULL,
    risk_score smallint NOT NULL,
    reason character varying(255) NOT NULL,
    category_code character varying(50) NOT NULL,
    entry_id bigint,
    matched_fields character varying(200),
    lock_cif_required boolean DEFAULT false NOT NULL,
    is_latest boolean DEFAULT true NOT NULL,
    core_send_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    core_sent_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    attempt_count smallint DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    last_error character varying(500),
    CONSTRAINT ck_result_level CHECK (((risk_level)::text = ANY ((ARRAY['CAO'::character varying, 'TRUNG_BINH'::character varying])::text[]))),
    CONSTRAINT ck_result_send CHECK (((core_send_status)::text = ANY ((ARRAY['PENDING'::character varying, 'SENT'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_result_sent_at CHECK ((((core_send_status)::text = 'SENT'::text) = (core_sent_at IS NOT NULL))),
    CONSTRAINT ck_result_trigger CHECK (((trigger_type)::text = ANY ((ARRAY['T1'::character varying, 'T1R'::character varying, 'T2'::character varying, 'T3A'::character varying, 'T3B'::character varying])::text[])))
);


--
-- Name: COLUMN customer_risk_result.next_attempt_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_risk_result.next_attempt_at IS 'Thời điểm sớm nhất được thử gửi lại. Backoff giữa các LẦN CHẠY JOB — khác với retry trong một lần gọi.';


--
-- Name: customer_risk_result_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_risk_result_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_risk_result_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_risk_result_id_seq OWNED BY public.customer_risk_result.id;


--
-- Name: customer_scan_queue; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_scan_queue (
    id bigint NOT NULL,
    scan_batch_id uuid NOT NULL,
    trigger_type character varying(4) NOT NULL,
    cif character varying(50) NOT NULL,
    full_name character varying(320),
    full_name_norm character varying(320),
    dob date,
    phone character varying(50),
    phone_norm character varying(15),
    id_number character varying(50),
    id_number_norm character varying(50),
    old_id_number character varying(50),
    old_id_number_norm character varying(50),
    country_code character varying(2),
    occupation_code character varying(100),
    position_code character varying(100),
    core_risk_score smallint,
    enqueued_at timestamp with time zone DEFAULT now() NOT NULL,
    processed_at timestamp with time zone,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL
);


--
-- Name: COLUMN customer_scan_queue.old_id_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_scan_queue.old_id_number IS 'Số GTTT cũ (CMND 9 số trước khi đổi CCCD). Optional ở TH2 — có dùng để so khớp DS đen hay không: Q3';


--
-- Name: COLUMN customer_scan_queue.occupation_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_scan_queue.occupation_code IS 'Nghề nghiệp. Core thật trả về CHỮ TỰ DO chứ không phải mã, nên tiêu chí K3 chỉ khớp được khi có bảng ánh xạ chữ → mã. Chưa có bảng đó.';


--
-- Name: COLUMN customer_scan_queue.position_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_scan_queue.position_code IS 'Chức vụ. Cùng vấn đề chữ-tự-do như occupation_code.';


--
-- Name: COLUMN customer_scan_queue.core_risk_score; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.customer_scan_queue.core_risk_score IS 'KHÔNG CÒN ĐƯỢC ĐIỀN: Core thật không có cột điểm rủi ro nào. Điều kiện "điểm khác 7" của TH3b nay lọc bằng customer_risk_result của chính PCRT.';


--
-- Name: customer_scan_queue_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_scan_queue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_scan_queue_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_scan_queue_id_seq OWNED BY public.customer_scan_queue.id;


--
-- Name: pcrt_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pcrt_config (
    config_key character varying(100) NOT NULL,
    config_value character varying(255) NOT NULL,
    description character varying(500),
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: pcrt_core_event_inbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pcrt_core_event_inbox (
    id bigint NOT NULL,
    event_type character varying(24) NOT NULL,
    event_id character varying(64) NOT NULL,
    cif character varying(50) NOT NULL,
    change_type character varying(20) NOT NULL,
    source character varying(16) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamp with time zone DEFAULT now() NOT NULL,
    processed_at timestamp with time zone,
    process_error character varying(1000),
    CONSTRAINT ck_core_inbox_change CHECK (((change_type)::text = ANY ((ARRAY['CREATED'::character varying, 'UPDATED'::character varying, 'STATUS_CHANGED'::character varying, 'DELETED'::character varying])::text[]))),
    CONSTRAINT ck_core_inbox_source CHECK (((source)::text = ANY ((ARRAY['KAFKA'::character varying, 'REST'::character varying])::text[]))),
    CONSTRAINT ck_core_inbox_type CHECK (((event_type)::text = 'CUSTOMER_CHANGED'::text))
);


--
-- Name: pcrt_core_event_inbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.pcrt_core_event_inbox ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.pcrt_core_event_inbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: pcrt_customer_identity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pcrt_customer_identity (
    cif character varying(50) NOT NULL,
    scan_target boolean NOT NULL,
    full_name_norm character varying(320),
    dob date,
    phone_norm character varying(15),
    id_number_norm character varying(50),
    old_id_number_norm character varying(50),
    normalizer_version smallint DEFAULT 1 NOT NULL,
    core_updated_at timestamp with time zone NOT NULL,
    synced_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: COLUMN pcrt_customer_identity.core_updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pcrt_customer_identity.core_updated_at IS 'Mốc thứ tự, KHÔNG phải mốc kiểm toán. Ghi đè chỉ xảy ra khi mốc mới >= mốc đang có, nên một sự kiện TH2 tới muộn không thể đè lên dữ liệu mới hơn.';


--
-- Name: COLUMN pcrt_customer_identity.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.pcrt_customer_identity.deleted_at IS 'Bia mộ. NOT NULL = Core báo KH này không còn. Luôn đi kèm scan_target = false, nên 5 partial index (đều có vị từ scan_target) tự động loại dòng này ra.';


--
-- Name: scan_batch; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scan_batch (
    id uuid NOT NULL,
    trigger_type character varying(4) NOT NULL,
    status character varying(20) NOT NULL,
    enqueued_count integer DEFAULT 0 NOT NULL,
    processed_count integer DEFAULT 0 NOT NULL,
    matched_count integer DEFAULT 0 NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    finished_at timestamp with time zone,
    note character varying(500),
    CONSTRAINT ck_batch_status CHECK (((status)::text = ANY ((ARRAY['ENQUEUING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_batch_trigger CHECK (((trigger_type)::text = ANY ((ARRAY['T1'::character varying, 'T1R'::character varying, 'T2'::character varying, 'T3A'::character varying, 'T3B'::character varying])::text[])))
);


--
-- Name: watchlist_category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.watchlist_category (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    priority smallint NOT NULL,
    sub_order smallint DEFAULT 1 NOT NULL,
    match_type character varying(2) NOT NULL,
    risk_level character varying(20) NOT NULL,
    risk_score smallint NOT NULL,
    reason character varying(255) NOT NULL,
    is_blacklist boolean DEFAULT false NOT NULL,
    active boolean DEFAULT true NOT NULL,
    entries_changed_at timestamp with time zone,
    CONSTRAINT ck_cat_blacklist CHECK (((is_blacklist AND (priority = 0) AND (risk_score = 7) AND ((match_type)::text = 'K1'::text)) OR ((NOT is_blacklist) AND ((priority >= 1) AND (priority <= 9)) AND ((risk_score >= 2) AND (risk_score <= 5))))),
    CONSTRAINT ck_cat_match_type CHECK (((match_type)::text = ANY ((ARRAY['K1'::character varying, 'K2'::character varying, 'K3'::character varying, 'K4'::character varying])::text[]))),
    CONSTRAINT ck_cat_risk_level CHECK (((risk_level)::text = ANY ((ARRAY['CAO'::character varying, 'TRUNG_BINH'::character varying])::text[]))),
    CONSTRAINT ck_cat_score CHECK (((risk_score >= 2) AND (risk_score <= 7)))
);


--
-- Name: COLUMN watchlist_category.sub_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.watchlist_category.sub_order IS 'Thứ tự duyệt trong cùng priority — giải Q2 (A.8)';


--
-- Name: COLUMN watchlist_category.entries_changed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.watchlist_category.entries_changed_at IS 'Lần cuối DS này có bản ghi thay đổi. Dùng cho: (1) trigger TH1, (2) TH3-B1 chọn nhánh 3a/3b, (3) invalidate cache — giải Q6';


--
-- Name: watchlist_category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.watchlist_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: watchlist_category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.watchlist_category_id_seq OWNED BY public.watchlist_category.id;


--
-- Name: watchlist_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.watchlist_entry (
    id bigint NOT NULL,
    category_id bigint NOT NULL,
    match_type character varying(2) NOT NULL,
    full_name character varying(255),
    full_name_norm character varying(255),
    dob date,
    dob_year smallint,
    phone character varying(30),
    phone_norm character varying(15),
    id_number character varying(50),
    id_number_norm character varying(50),
    country_code character varying(2),
    occupation_code character varying(50),
    position_code character varying(50),
    source character varying(100),
    source_ref character varying(100),
    normalizer_version smallint DEFAULT 1 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_entry_norm CHECK ((((full_name IS NULL) = (full_name_norm IS NULL)) AND ((phone IS NULL) = (phone_norm IS NULL)) AND ((id_number IS NULL) = (id_number_norm IS NULL)))),
    CONSTRAINT ck_entry_shape CHECK (
CASE match_type
    WHEN 'K1'::text THEN ((country_code IS NULL) AND (occupation_code IS NULL) AND (position_code IS NULL) AND ((id_number_norm IS NOT NULL) OR (full_name_norm IS NOT NULL)))
    WHEN 'K2'::text THEN ((country_code IS NOT NULL) AND (full_name IS NULL) AND (id_number IS NULL) AND (phone IS NULL) AND (dob IS NULL) AND (dob_year IS NULL) AND (occupation_code IS NULL) AND (position_code IS NULL))
    WHEN 'K3'::text THEN ((occupation_code IS NOT NULL) AND (full_name IS NULL) AND (id_number IS NULL) AND (phone IS NULL) AND (dob IS NULL) AND (dob_year IS NULL) AND (country_code IS NULL) AND (position_code IS NULL))
    WHEN 'K4'::text THEN ((position_code IS NOT NULL) AND (full_name IS NULL) AND (id_number IS NULL) AND (phone IS NULL) AND (dob IS NULL) AND (dob_year IS NULL) AND (country_code IS NULL) AND (occupation_code IS NULL))
    ELSE false
END)
);


--
-- Name: COLUMN watchlist_entry.dob_year; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.watchlist_entry.dob_year IS 'Dùng khi DS mẫu chỉ có năm sinh, không có ngày/tháng (Phase 2 mục 2.1)';


--
-- Name: COLUMN watchlist_entry.source_ref; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.watchlist_entry.source_ref IS 'Mã bản ghi ở nguồn gốc (UN/FATF/...) — cần khi giải trình với cơ quan thanh tra';


--
-- Name: COLUMN watchlist_entry.normalizer_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.watchlist_entry.normalizer_version IS 'Đổi quy tắc chuẩn hóa thì mọi dòng cũ sai → dùng cột này để backfill';


--
-- Name: watchlist_entry_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.watchlist_entry_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: watchlist_entry_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.watchlist_entry_id_seq OWNED BY public.watchlist_entry.id;


--
-- Name: risk_update_log id; Type: DEFAULT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.risk_update_log ALTER COLUMN id SET DEFAULT nextval('core.risk_update_log_id_seq'::regclass);


--
-- Name: wallet_customer id; Type: DEFAULT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.wallet_customer ALTER COLUMN id SET DEFAULT nextval('core.wallet_customer_id_seq'::regclass);


--
-- Name: customer_risk_result id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_risk_result ALTER COLUMN id SET DEFAULT nextval('public.customer_risk_result_id_seq'::regclass);


--
-- Name: customer_scan_queue id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_scan_queue ALTER COLUMN id SET DEFAULT nextval('public.customer_scan_queue_id_seq'::regclass);


--
-- Name: watchlist_category id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watchlist_category ALTER COLUMN id SET DEFAULT nextval('public.watchlist_category_id_seq'::regclass);


--
-- Name: watchlist_entry id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watchlist_entry ALTER COLUMN id SET DEFAULT nextval('public.watchlist_entry_id_seq'::regclass);


--
-- Name: risk_update_log risk_update_log_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.risk_update_log
    ADD CONSTRAINT risk_update_log_pkey PRIMARY KEY (id);


--
-- Name: risk_update_log uq_core_idempotency; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.risk_update_log
    ADD CONSTRAINT uq_core_idempotency UNIQUE (idempotency_key);


--
-- Name: wallet_customer wallet_customer_cif_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.wallet_customer
    ADD CONSTRAINT wallet_customer_cif_key UNIQUE (cif);


--
-- Name: wallet_customer wallet_customer_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.wallet_customer
    ADD CONSTRAINT wallet_customer_pkey PRIMARY KEY (id);


--
-- Name: customer_risk_result customer_risk_result_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_risk_result
    ADD CONSTRAINT customer_risk_result_pkey PRIMARY KEY (id);


--
-- Name: customer_scan_queue customer_scan_queue_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_scan_queue
    ADD CONSTRAINT customer_scan_queue_pkey PRIMARY KEY (id);


--
-- Name: pcrt_config pcrt_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcrt_config
    ADD CONSTRAINT pcrt_config_pkey PRIMARY KEY (config_key);


--
-- Name: pcrt_core_event_inbox pcrt_core_event_inbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcrt_core_event_inbox
    ADD CONSTRAINT pcrt_core_event_inbox_pkey PRIMARY KEY (id);


--
-- Name: pcrt_customer_identity pcrt_customer_identity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcrt_customer_identity
    ADD CONSTRAINT pcrt_customer_identity_pkey PRIMARY KEY (cif);


--
-- Name: scan_batch scan_batch_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scan_batch
    ADD CONSTRAINT scan_batch_pkey PRIMARY KEY (id);


--
-- Name: watchlist_category uq_cat_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watchlist_category
    ADD CONSTRAINT uq_cat_code UNIQUE (code);


--
-- Name: watchlist_category uq_cat_id_match_type; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watchlist_category
    ADD CONSTRAINT uq_cat_id_match_type UNIQUE (id, match_type);


--
-- Name: watchlist_category uq_cat_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watchlist_category
    ADD CONSTRAINT uq_cat_order UNIQUE (priority, sub_order);


--
-- Name: pcrt_core_event_inbox uq_core_inbox_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pcrt_core_event_inbox
    ADD CONSTRAINT uq_core_inbox_event UNIQUE (event_type, event_id);


--
-- Name: watchlist_category watchlist_category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watchlist_category
    ADD CONSTRAINT watchlist_category_pkey PRIMARY KEY (id);


--
-- Name: watchlist_entry watchlist_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watchlist_entry
    ADD CONSTRAINT watchlist_entry_pkey PRIMARY KEY (id);


--
-- Name: idx_core_created_at; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_core_created_at ON core.wallet_customer USING btree (created_at);


--
-- Name: idx_core_log_cif; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_core_log_cif ON core.risk_update_log USING btree (cif, received_at DESC);


--
-- Name: idx_core_scan_target; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_core_scan_target ON core.wallet_customer USING btree (id) WHERE (((customer_type)::text = 'CN'::text) AND ((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'APPROVED'::character varying])::text[])));


--
-- Name: idx_core_update_time; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_core_update_time ON core.wallet_customer USING btree (update_time);


--
-- Name: idx_batch_unfinished; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_batch_unfinished ON public.scan_batch USING btree (started_at) WHERE ((status)::text = ANY ((ARRAY['ENQUEUING'::character varying, 'PROCESSING'::character varying])::text[]));


--
-- Name: idx_core_inbox_cif; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_core_inbox_cif ON public.pcrt_core_event_inbox USING btree (cif, received_at DESC);


--
-- Name: idx_core_inbox_unprocessed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_core_inbox_unprocessed ON public.pcrt_core_event_inbox USING btree (id) WHERE (processed_at IS NULL);


--
-- Name: idx_entry_by_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_entry_by_category ON public.watchlist_entry USING btree (category_id) WHERE active;


--
-- Name: idx_pci_core_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pci_core_updated ON public.pcrt_customer_identity USING btree (core_updated_at);


--
-- Name: idx_pci_dob; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pci_dob ON public.pcrt_customer_identity USING btree (dob) WHERE (scan_target AND (dob IS NOT NULL));


--
-- Name: idx_pci_full_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pci_full_name ON public.pcrt_customer_identity USING btree (full_name_norm) WHERE (scan_target AND (full_name_norm IS NOT NULL));


--
-- Name: idx_pci_id_number; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pci_id_number ON public.pcrt_customer_identity USING btree (id_number_norm) WHERE (scan_target AND (id_number_norm IS NOT NULL));


--
-- Name: idx_pci_old_id_number; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pci_old_id_number ON public.pcrt_customer_identity USING btree (old_id_number_norm) WHERE (scan_target AND (old_id_number_norm IS NOT NULL));


--
-- Name: idx_pci_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pci_phone ON public.pcrt_customer_identity USING btree (phone_norm) WHERE (scan_target AND (phone_norm IS NOT NULL));


--
-- Name: idx_queue_cif; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_queue_cif ON public.customer_scan_queue USING btree (cif, enqueued_at DESC);


--
-- Name: idx_result_batch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_result_batch ON public.customer_risk_result USING btree (scan_batch_id);


--
-- Name: idx_result_cif; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_result_cif ON public.customer_risk_result USING btree (cif, created_at DESC);


--
-- Name: idx_result_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_result_dispatch ON public.customer_risk_result USING btree (next_attempt_at NULLS FIRST, id) WHERE ((core_send_status)::text = ANY ((ARRAY['PENDING'::character varying, 'FAILED'::character varying])::text[]));


--
-- Name: uq_queue_batch_cif; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_queue_batch_cif ON public.customer_scan_queue USING btree (scan_batch_id, cif);


--
-- Name: uq_result_latest_per_cif; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_result_latest_per_cif ON public.customer_risk_result USING btree (cif) WHERE is_latest;


--
-- Name: watchlist_entry fk_entry_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watchlist_entry
    ADD CONSTRAINT fk_entry_category FOREIGN KEY (category_id, match_type) REFERENCES public.watchlist_category(id, match_type);


--
-- PostgreSQL database dump complete
--

\unrestrict kQZeWa75gc951HHSMfAfd80Ah9sUxfavzzJTRbh8FG6W7FmWy8hPVguOWbhonIa

