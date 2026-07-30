# Lộ Trình Học SQL Nâng Cao — Index, Query Optimization & Performance
> **Dành cho:** Backend engineers viết SQL hàng ngày nhưng chưa hiểu tại sao query chậm, chưa đọc được EXPLAIN PLAN, và chưa tự tin xử lý performance issues trong production.
---
## Bạn đang ở đây
- Viết SQL hàng ngày nhưng không biết tại sao query chậm
- Chưa biết cách đọc EXPLAIN / EXPLAIN ANALYZE
- Chưa hiểu index thực sự hoạt động như thế nào bên dưới
---
## Module 01 — Index & B+Tree Fundamentals
> Hiểu disk I/O, B+Tree, và cơ chế Index Lookup. Đọc EXPLAIN Plan, thiết kế Composite Index đúng thứ tự.
### Index và B+Tree: từ Full Table Scan đến B+Tree
- **Full Table Scan vs Index Scan**: tại sao database đọc từng row khi không có index, và index giúp gì
- **Disk I/O là bottleneck thực sự**: random I/O vs sequential I/O, tại sao số lần đọc disk quan trọng hơn CPU
- **Cấu trúc B+Tree**:
  - Internal nodes chứa key để navigate
  - Leaf nodes chứa data (hoặc pointer đến data)
  - Leaf nodes được liên kết thành linked list → range scan hiệu quả
  - Chiều cao của cây quyết định số lần I/O
### Cái giá của Index: 3 bước Index Lookup
Mỗi index lookup trải qua 3 bước:
1. **Tree traversal** — đi từ root đến leaf node phù hợp (O(log n))
2. **Leaf node chain scan** — scan các leaf nodes liên tiếp (với range query)
3. **Table access** — với non-covering index, phải quay lại table để fetch row đầy đủ (random I/O)
> **Khi nào index phản tác dụng**: selectivity thấp (ví dụ cột `status` chỉ có 3 giá trị), cardinality thấp, hoặc query cần fetch quá nhiều rows → optimizer có thể chọn Full Table Scan thay vì index scan.
### Equality & EXPLAIN Plan: Primary Key Lookup
- **Primary Key Lookup** là trường hợp lý tưởng: B+Tree lookup duy nhất, không cần table access
- **Cách đọc EXPLAIN Plan cơ bản**:
  ```sql
  EXPLAIN SELECT * FROM orders WHERE id = 123;
  -- Seq Scan vs Index Scan vs Index Only Scan
  -- cost=0.00..8.27 rows=1 width=100
  -- actual time=0.05..0.06 rows=1 loops=1
  ```
  - `Seq Scan`: Full Table Scan — red flag
  - `Index Scan`: dùng index nhưng vẫn phải access table
  - `Index Only Scan`: covering index — lý tưởng nhất
  - `cost`: estimated cost (startup..total)
  - `actual time`: thời gian thực tế (ms)
### Composite Index & Thứ tự cột: Leftmost Prefix Rule
- **Leftmost Prefix Rule**: composite index `(a, b, c)` chỉ có thể được dùng nếu query filter bắt đầu từ `a`
  ```sql
  -- Index (last_name, first_name, dob)
  WHERE last_name = 'Nguyen'                    -- ✅ dùng được
  WHERE last_name = 'Nguyen' AND first_name = 'Van' -- ✅ dùng được
  WHERE first_name = 'Van'                      -- ❌ không dùng được
  ```
- **Thiết kế composite index đúng thứ tự**:
  - Equality columns trước, range columns sau
  - High-selectivity columns trước
  - Columns xuất hiện trong ORDER BY / GROUP BY nên được đưa vào index để tránh filesort
---
## Module 02 — Query Optimization
> Nhận diện 6 Red Flags trong Execution Plan. Xử lý Function phá Index, Plan Caching, Range Queries, NULL trap.
### 3 chiến thuật Scan: Bitmap Scan, EXPLAIN ANALYZE, 6 Red Flags
**Bitmap Scan** — kỹ thuật kết hợp nhiều index:
- PostgreSQL build một bitmap các row IDs thỏa điều kiện từ từng index riêng lẻ
- Sau đó AND/OR các bitmap lại → fetch rows theo sequential order → giảm random I/O
**EXPLAIN ANALYZE** — đọc actual execution:

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM payments WHERE user_id = 42 AND status = 'PENDING';
```

**6 Red Flags trong Execution Plan**:
1. `Seq Scan` trên bảng lớn
2. `Hash Join` trên bảng không có index join column
3. `Sort` không được eliminated bởi index
4. `Nested Loop` với outer table lớn và inner table không có index
5. `rows=1` actual nhưng `rows=10000` estimated → bad statistics → chạy `ANALYZE`
6. `Buffers: hit=0 read=...` lớn → cache miss → I/O bound query
### Khi Function phá Index: SARGable, Expression Index, Over-indexing
**SARGable (Search ARGument ABLE)** — query có thể dùng index:

```sql
-- ❌ Non-SARGable: function phá index
WHERE YEAR(created_at) = 2024
WHERE LOWER(email) = 'test@example.com'
WHERE amount + 100 > 500
-- ✅ SARGable: rewrite để index được dùng
WHERE created_at >= '2024-01-01' AND created_at < '2025-01-01'
WHERE email = 'test@example.com'  -- hoặc dùng expression index
WHERE amount > 400
```

**Expression Index** — giải pháp khi phải dùng function:

```sql
CREATE INDEX idx_email_lower ON users (LOWER(email));
-- Sau đó query: WHERE LOWER(email) = 'test@example.com' sẽ dùng index
```

**Over-indexing**: mỗi index làm chậm INSERT/UPDATE/DELETE vì database phải maintain index → chỉ tạo index khi có query cụ thể cần nó.
### Plan Caching & SQL Injection: Query Pipeline, Custom vs Generic Plan
- **Generic Plan**: PostgreSQL cache execution plan, thay thế parameters bằng placeholder → reuse cho mọi giá trị
- **Custom Plan**: PostgreSQL tạo plan mới cho mỗi bộ parameters → tốt hơn khi data distribution skewed
- PostgreSQL tự động chọn generic vs custom sau 5 lần execute (so sánh estimated cost)
- **SQL Injection**: parameterized queries/prepared statements không chỉ an toàn mà còn enable plan caching
### Range, LIKE & Bitmap Combine: Range Scan, Equality First, text_pattern_ops

```sql
-- Equality first, range last trong composite index
-- Index: (status, created_at) — ĐÚNG cho query này:
WHERE status = 'ACTIVE' AND created_at > '2024-01-01'
-- Index: (created_at, status) — SAI: range trước → status không dùng được index
```

**LIKE và index**:

```sql
WHERE name LIKE 'Nguyen%'   -- ✅ prefix search, dùng được B+Tree index
WHERE name LIKE '%Nguyen'   -- ❌ suffix search, không dùng được
-- Dùng text_pattern_ops cho LIKE với locale-aware collation (PostgreSQL):
CREATE INDEX idx_name_pattern ON users (name text_pattern_ops);
```

### Partial Index & Bẫy NULL: giảm index size, NOT IN + NULL trap
**Partial Index** — index chỉ một subset của rows:

```sql
-- Chỉ index các payment đang PENDING (chiếm 1% data)
CREATE INDEX idx_payments_pending ON payments (created_at)
WHERE status = 'PENDING';
-- Index size giảm 99%, query với WHERE status = 'PENDING' nhanh hơn nhiều
```

**NULL trap với NOT IN**:

```sql
-- ❌ Nguy hiểm: nếu subquery trả về bất kỳ NULL nào, kết quả là empty set
SELECT * FROM orders WHERE user_id NOT IN (SELECT user_id FROM blacklist);
-- ✅ An toàn: dùng NOT EXISTS
SELECT * FROM orders o
WHERE NOT EXISTS (SELECT 1 FROM blacklist b WHERE b.user_id = o.user_id);
```

NULL không bao giờ bằng bất cứ thứ gì, kể cả NULL → `NULL NOT IN (1, 2, NULL)` trả về `UNKNOWN` → row bị loại.
---
## Module 03 — Join & Clustering
> Hiểu Nested Loop, Hash Join, Sort Merge và khi nào dùng loại nào. Index-Only Scan, Clustering Data để giảm disk I/O.
### Join: Nested Loop & Index Join
**Nested Loop Join**:

```
For each row in outer table:
    For each row in inner table matching condition:
        emit result row
```

- **Tốt khi**: outer table nhỏ, inner table có index trên join column
- **Tệ khi**: cả hai table lớn, không có index → O(n×m)
**Index Join** (Nested Loop + Index):
- Outer loop: scan outer table
- Inner: dùng index lookup trên inner table
- Cần index trên join column của inner table
### Join: Hash Join & Sort Merge
**Hash Join**:
1. Build phase: hash toàn bộ smaller table vào hash table in-memory
2. Probe phase: scan larger table, lookup trong hash table
- **Tốt khi**: join condition là equality (`=`), không có index phù hợp
- **Vấn đề**: nếu hash table lớn hơn `work_mem` → spill to disk → chậm
**Sort Merge Join**:
1. Sort cả hai table theo join column
2. Merge giống merge sort
- **Tốt khi**: data đã được sorted (có index), range join, hoặc query đã có ORDER BY cùng column
### Clustering Data: Index Filter & Index-Only Scan
**Index Filter vs Index Condition**:
- **Index Condition** (Index Pushdown): filter áp dụng ngay khi đọc index → ít rows hơn được fetch về
- **Index Filter**: filter áp dụng sau khi đã fetch row từ table → lãng phí I/O
**Index-Only Scan** — trường hợp tối ưu nhất:

```sql
-- Covering index: index chứa đủ tất cả columns mà query cần
CREATE INDEX idx_covering ON orders (user_id, status, amount);
SELECT status, amount FROM orders WHERE user_id = 42;
-- Index-Only Scan: không cần truy cập table heap
```

### Clustering Data: Index Organized Tables
- **Heap Table** (default PostgreSQL): data được lưu theo insertion order, index chứa pointer (CTID) đến heap
- **Clustered Index** (MySQL InnoDB Primary Key, SQL Server): data được lưu theo thứ tự của index → range scan trên PK rất nhanh
- **PostgreSQL CLUSTER command**: sort lại heap theo một index cụ thể (one-time operation, không tự maintain)
  ```sql
  CLUSTER orders USING idx_orders_created_at;
  -- Sau đó range query trên created_at sẽ ít random I/O hơn
  ```
---
## Module 04 — Sorting, Pagination & DML
> Tối ưu ORDER BY, GROUP BY, Pagination với Index. Hiểu tác động của Index lên INSERT, UPDATE, DELETE.
### Sorting & Grouping với Index
**Avoid filesort với index**:

```sql
-- Index (created_at DESC) cho phép query này không cần sort
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10;
-- → Index Scan Backward: đọc ngược index, không cần filesort
```

**GROUP BY và index**:

```sql
-- Index (status, user_id) giúp GROUP BY status không cần filesort
SELECT status, COUNT(*) FROM orders GROUP BY status;
```

**Composite index cho sort**:
- Tất cả ORDER BY columns phải có trong index
- Tất cả phải cùng chiều (ASC/DESC) hoặc index được tạo với chiều phù hợp
### Partial Result, Top N, Pagination & Window Functions
**Top N với LIMIT — lý tưởng cho index**:

```sql
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10;
-- Chỉ cần đọc 10 rows đầu của index, không cần đọc toàn bộ
```

**Offset Pagination — vấn đề hiệu năng**:

```sql
-- ❌ OFFSET lớn: database phải đọc và bỏ qua N rows
SELECT * FROM orders ORDER BY id LIMIT 20 OFFSET 10000;
-- ✅ Keyset Pagination (Cursor-based): dùng WHERE thay OFFSET
SELECT * FROM orders WHERE id > :last_seen_id ORDER BY id LIMIT 20;
```

**Window Functions và index**:

```sql
-- Window functions thường cần sort — index có thể giúp eliminate sort
SELECT user_id, amount,
       ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC)
FROM payments;
-- Index (user_id, created_at DESC) giúp tránh filesort trong window
```

### Insert, Update, Delete: tác động của Index lên DML
Mỗi DML statement phải maintain **tất cả** indexes trên table:
| Operation | Index overhead |
|-----------|---------------|
| `INSERT` | Thêm entry vào mỗi index — O(log n) per index |
| `UPDATE` | Xóa entry cũ + thêm entry mới trong mỗi index bị ảnh hưởng |
| `DELETE` | Đánh dấu entry trong index là deleted (PostgreSQL: dead tuple) |
**PostgreSQL MVCC & index bloat**:
- UPDATE không sửa row in-place mà tạo row mới → dead tuple trong heap VÀ trong index
- `VACUUM` dọn dead tuples, `VACUUM ANALYZE` cập nhật statistics
- `CREATE INDEX CONCURRENTLY` trong production để không lock table
**Batch DML optimization**:

```sql
-- ❌ Chậm: trigger index maintenance mỗi row
INSERT INTO payments VALUES (...); -- 10,000 lần
-- ✅ Nhanh hơn: bulk insert, index maintenance 1 lần
INSERT INTO payments VALUES (...), (...), (...); -- multi-row insert
```

---
## Bạn sẽ ở đây
Sau khi hoàn thành lộ trình này:
- ✅ **Đọc bất kỳ EXPLAIN Plan nào** và hiểu database đang làm gì
- ✅ **Thiết kế index strategy** cho bất kỳ schema nào: composite index, partial index, covering index, expression index
- ✅ **Tự tin xử lý performance issues trong production**: nhận diện 6 red flags, rewrite non-SARGable query, fix pagination problem, tránh NULL trap
- ✅ **Hiểu trade-off**: mỗi index giúp SELECT nhưng làm chậm DML — biết khi nào nên tạo và khi nào nên bỏ
---
## Tài nguyên tham khảo
- [Use The Index, Luke](https://use-the-index-luke.com/) — sách miễn phí, best resource về index cho SQL
- [PostgreSQL EXPLAIN docs](https://www.postgresql.org/docs/current/sql-explain.html)
- [pganalyze EXPLAIN Visualizer](https://explain.dalibo.com/) — visualize EXPLAIN plan
- Alex Xu "System Design Interview" Vol 1 & 2 — cho context về database design trong distributed systems
