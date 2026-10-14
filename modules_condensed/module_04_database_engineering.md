# Module 04: Database Engineering and SQL

## 1. Relational Foundations & Three-Valued Logic

Relational databases structure data into mathematical relations (tables) composed of tuples (rows) and attributes (columns), constrained by relational algebra.

### 1.1 Three-Valued Logic (3VL) & The NULL Trap
SQL uses Three-Valued Logic: boolean evaluations produce `TRUE`, `FALSE`, or `UNKNOWN`.
- Any equality or inequality comparison with `NULL` yields `UNKNOWN`: `NULL = NULL \implies \text{UNKNOWN}`.
- To test for missing data, use `IS NULL` or `IS NOT NULL`.
- **The NOT IN Subquery Trap:**
  ```sql
  -- If subquery returns ANY row with a NULL value, NOT IN evaluates to UNKNOWN for all rows!
  SELECT * FROM users WHERE id NOT IN (SELECT supervisor_id FROM departments);
  -- ALWAYS use NOT EXISTS to avoid the 3VL NULL trap:
  SELECT u.* FROM users u WHERE NOT EXISTS (
      SELECT 1 FROM departments d WHERE d.supervisor_id = u.id
  );
  ```

---

## 2. SQL Fundamentals & Logical Query Processing

SQL is declarative: engineers declare *what* data to retrieve; the relational query optimizer determines *how* to access physical storage.

### 2.1 Logical Execution Order
SQL clauses execute in an order fundamentally distinct from their written lexical order:
1. `FROM`: Identify and join base tables.
2. `ON`: Evaluate join conditions.
3. `JOIN`: Merge matched tuples (and unmatched tuples for outer joins).
4. `WHERE`: Filter intermediate rows before aggregation.
5. `GROUP BY`: Partition rows into aggregate groups.
6. `HAVING`: Filter aggregated groups.
7. `SELECT`: Evaluate projections, scalar expressions, and aliases.
8. `DISTINCT`: Eliminate duplicate projected rows.
9. `ORDER BY`: Sort the final result set.
10. `LIMIT / OFFSET`: Slice output window.

*Rule:* You cannot use a column alias defined in `SELECT` inside a `WHERE` clause because `WHERE` executes prior to `SELECT`.

### 2.2 Physical Join Algorithms
The optimizer translates logical joins into one of three physical algorithms:
1. **Nested Loop Join:** Iterates outer table and executes index lookups on the inner table for each row. Optimal when outer table is small and inner table has a high-selectivity index ($O(N \log M)$).
2. **Hash Join:** Builds an in-memory hash table on the smaller table's join keys, then scans the larger table probing the hash table. Executes in $O(N + M)$ time. Optimal for large, unsorted, non-indexed equijoins.
3. **Sort-Merge Join:** Sorts both tables by join keys (or leverages existing index order) and scans them simultaneously in lockstep. Optimal when inputs are already sorted or for range join predicates.

---

## 3. Data Modeling & Normalization

Normalization decomposes relations to eliminate update, insertion, and deletion anomalies while preserving functional dependencies:
- **1NF (First Normal Form):** Attributes are atomic; no repeating groups or arrays.
- **2NF (Second Normal Form):** In 1NF, and every non-key attribute is fully functionally dependent on the entire primary key (no partial key dependencies).
- **3NF (Third Normal Form):** In 2NF, and no non-key attribute is transitively dependent on the primary key (no $X \to Y \to Z$).
- **BCNF (Boyce-Codd Normal Form):** For every functional dependency $X \to Y$, $X$ must be a superkey.
- **Denormalization:** Deliberately introducing redundancy to eliminate expensive multi-table joins on read-heavy systems, trading write latency and storage for read speed.

---

## 4. Advanced SQL & Window Functions

Window functions perform calculations across related row partitions without collapsing individual rows into aggregate groups.

```sql
SELECT 
    employee_id, department_id, salary,
    -- Rank employees by salary within their department
    DENSE_RANK() OVER (
        PARTITION BY department_id 
        ORDER BY salary DESC
    ) as dept_salary_rank,
    -- Moving average of salary over 3-row sliding window
    AVG(salary) OVER (
        PARTITION BY department_id 
        ORDER BY hire_date 
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) as moving_avg_salary,
    -- Lag: Previous employee's salary in hire order
    LAG(salary, 1) OVER (
        PARTITION BY department_id 
        ORDER BY hire_date
    ) as prev_hire_salary
FROM employees;
```

---

## 5. Indexing Mechanics & Physical Storage

### 5.1 B+ Tree Index Architecture
Modern relational storage engines (InnoDB, Postgres) store indices as balanced B+ Trees:
- **Internal Nodes:** Store search keys and page pointers to direct traversals. Contain zero data records.
- **Leaf Nodes:** Store all actual keys, tuple references (or data rows), and are connected via a doubly linked list to provide $O(1)$ sequential range scans.
- **Height Formula:** $h \approx \lceil \log_B(N) \rceil$, where $B$ is the fanout (typically 100–500 keys per 16KB page). A 3-level B+ Tree indexes tens of millions of records with at most 3 page reads.

### 5.2 Clustered vs. Secondary Indexes
- **Clustered Index (Primary Key in MySQL InnoDB):** The physical table *is* the index. Leaf nodes contain the complete row data. Physical rows are stored in PK sorted order.
- **Secondary Index:** Leaf nodes contain the indexed key and the corresponding primary key value.
- **Double-Lookup Overhead:** Querying via a secondary index reads the secondary index B+ Tree to find the PK, then performs a second B+ Tree lookup on the clustered index to fetch columns (**Bookmark Lookup**).
- **Covering Index:** An index containing all columns requested by a query:
  ```sql
  -- Index on (tenant_id, created_at, status)
  SELECT status FROM orders WHERE tenant_id = 42 AND created_at >= '2024-01-01';
  ```
  The query is satisfied entirely within the index leaf nodes (**Index-Only Scan**), completely bypassing clustered table heap reads.

### 5.3 Leftmost Prefix Rule & Sargability
- **Composite Index Leftmost Prefix Rule:** An index on `(A, B, C)` can serve queries filtering on `(A)`, `(A, B)`, or `(A, B, C)`. It **cannot** serve queries filtering only on `(B)` or `(C)`.
- **Sargability (Search Argument Able):** A predicate is sargable if the storage engine can utilize an index seek:
  - *Non-Sargable:* `WHERE YEAR(created_at) = 2024` (invokes function on column, forces full table scan).
  - *Sargable:* `WHERE created_at >= '2024-01-01' AND created_at < '2025-01-01'`.

---

## 6. Transactions, ACID & Write-Ahead Logging

### 6.1 ACID Guarantees
- **Atomicity:** All operations within a transaction succeed, or the entire transaction is rolled back.
- **Consistency:** The database transitions from one valid state to another, preserving all explicit schema constraints, foreign keys, and invariants.
- **Isolation:** Concurrent transactions execute without cross-transaction anomalies, behaving as if executed serially.
- **Durability:** Once committed, transaction modifications survive hardware crashes, power loss, or OS panics.

### 6.2 Write-Ahead Logging (WAL) & Crash Recovery
Modifying data pages in memory (Buffer Pool) before writing them to disk enables high throughput, but introduces crash risks. **WAL** guarantees durability:
1. Every state mutation appends a sequential log record to the WAL buffer.
2. The transaction commit command blocks until the WAL buffer is flushed to physical disk via `fsync()`.
3. Modified data pages remain "dirty" in RAM and are flushed to data files asynchronously during background **Checkpoints**.
4. **Crash Recovery (ARIES):** On reboot, the engine scans the WAL from the last checkpoint:
   - *Analysis:* Identifies active transactions and dirty pages at time of crash.
   - *Redo:* Replays all logged changes to bring data pages to the exact state before failure.
   - *Undo:* Rolls back changes made by uncommitted transactions.

---

## 7. Isolation Levels, Anomalies & MVCC

### 7.1 ANSI Isolation Levels vs. Anomalies Matrix

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read | Write Skew |
| :--- | :---: | :---: | :---: | :---: |
| **Read Uncommitted** | Possible | Possible | Possible | Possible |
| **Read Committed** | Prevented | Possible | Possible | Possible |
| **Repeatable Read** | Prevented | Prevented | Prevented (InnoDB) | Possible |
| **Serializable** | Prevented | Prevented | Prevented | Prevented |

- **Dirty Read:** Transaction $T_1$ reads modifications made by $T_2$ before $T_2$ commits. If $T_2$ aborts, $T_1$ acted on phantom data.
- **Non-Repeatable Read:** $T_1$ reads a row; $T_2$ updates that row and commits; $T_1$ re-reads the row and observes altered values.
- **Phantom Read:** $T_1$ reads a range of rows matching a predicate; $T_2$ inserts a new row matching that predicate and commits; $T_1$ re-queries and sees the new "phantom" row.
- **Write Skew (Snapshot Isolation anomaly):** $T_1$ and $T_2$ concurrently read overlapping data, verify an invariant (e.g., "at least one doctor on call"), and make disjoint updates (e.g., $T_1$ takes off, $T_2$ takes off). Both commit under Repeatable Read, violating the global invariant. Prevented only by **Serializable** isolation or explicit `SELECT ... FOR UPDATE`.

### 7.2 Multi-Version Concurrency Control (MVCC)
MVCC allows readers to never block writers, and writers to never block readers:
- **PostgreSQL MVCC:** Every tuple stores header fields `xmin` (creating transaction ID) and `xmax` (deleting/updating transaction ID). Updates insert a new row version and set `xmax` on the old version.
  - *Table Bloat & VACUUM:* Dead row versions remain on disk until reclaimed by `VACUUM`.
- **MySQL InnoDB MVCC:** Updates modify the clustered index record in-place. Historical row versions are stored in **Undo Log** segments and reconstructed on demand. Purge threads reclaim undo logs once transactions finish.

---

## 8. Connection Pooling & Zero-Downtime Migrations

### 8.1 Connection Pool Sizing Physics
Database connections consume substantial OS and DB memory (thread stacks, buffer allocations, lock tables). Sizing follows the **HikariCP Formula**:
$$\text{Pool Size} = (\text{CPU Cores} \times 2) + \text{Effective Spindle Count}$$
A 16-core database server with SSD storage handles at most 32–40 concurrent queries. Allocating 1,000 application connections causes severe CPU thread context-switching thrashing and connection timeouts.

### 8.2 Zero-Downtime Expand-and-Contract Migrations
Renaming or modifying active production columns without downtime requires the **Expand-and-Contract (Parallel Run)** pattern:
```
Phase 1 (Expand): Add new nullable column `new_col` to table.
Phase 2 (Dual-Write): Application writes to BOTH `old_col` and `new_col`, reads from `old_col`.
Phase 3 (Backfill): Background batch job migrates historical data from `old_col` to `new_col`.
Phase 4 (Read Switch): Application reads from `new_col`, writes to both.
Phase 5 (Contract): Remove writes to `old_col`, drop `old_col` in database.
```

---

## 9. Failure Modes, Diagnostic Signals & Solutions

| Failure Mode | Diagnostic Symptom | Root Cause | Solution |
| :--- | :--- | :--- | :--- |
| **Connection Starvation** | `ConnectionTimeoutException: Cannot acquire connection within 30000ms`. | Long-running queries or remote network calls executed inside `@Transactional` holding connections open. | Isolate remote I/O outside transactions; tune connection pool size and query timeouts. |
| **Deadlock Abort** | `ERROR: deadlock detected; Process 1234 waits for ShareLock...` | Concurrent transactions acquire locks on multiple tables or rows in differing chronological order. | Enforce strict deterministic lock acquisition ordering across all application code. |
| **Table Bloat** | Database storage swells rapidly; queries slow down despite stable row counts. | Long-running open transactions blocking PostgreSQL `autovacuum` from collecting dead tuples. | Terminate idle transactions; tune autovacuum scale factors and vacuum cost limits. |
| **Unindexed Foreign Key Lock** | `UPDATE parent` locks entire child table, causing system-wide queuing. | In MySQL InnoDB, deleting/updating parent rows requires locking child rows. Missing index forces table scan. | Always create secondary B+ Tree indexes on foreign key columns. |
---

## 10. High-Bar Database Interview Scenarios

### Scenario 1: Preventing Lost Updates Under Read Committed Isolation
- **Interview Question:** "Two concurrent transactions execute a Read-Modify-Write cycle on an account balance under default Read Committed isolation. How do you prevent lost updates?"
- **Deep Technical Answer:**
  1. **Pessimistic Locking:** Acquire an exclusive row lock at read time:
     ```sql
     SELECT balance FROM accounts WHERE id = 123 FOR UPDATE;
     UPDATE accounts SET balance = balance - 50 WHERE id = 123;
     ```
     Blocks competing transactions until the first transaction commits.
  2. **Optimistic Locking:** Add an integer `version` column:
     ```sql
     UPDATE accounts SET balance = balance - 50, version = version + 1 
     WHERE id = 123 AND version = :read_version;
     ```
     If another transaction committed an update first, the row count returns `0`, signaling the application to abort and retry.

### Scenario 2: Index Design for Multi-Column Filtering and Sorting
- **Interview Question:** "A query filters by tenant and status, then sorts by creation date: `SELECT * FROM orders WHERE tenant_id = 42 AND status = 'SHIPPED' ORDER BY created_at DESC LIMIT 20`. How do you design the optimal index?"
- **Deep Technical Answer:**
  1. **Optimal Index:** `CREATE INDEX idx_orders_composite ON orders (tenant_id, status, created_at DESC)`.
  2. **Execution Mechanics:**
     - The database performs an **Index Seek** on `(tenant_id = 42, status = 'SHIPPED')`.
     - Because `created_at DESC` is the final column in the composite index, matched records are already physically sorted on disk in descending order.
     - The engine reads the first 20 records directly and halts, achieving a zero-sort execution plan (**Eliminating the `filesort` / Sort operator**).

### Scenario 3: Investigating Table Bloat in PostgreSQL
- **Interview Question:** "A PostgreSQL database disk usage is swelling rapidly despite deleting millions of obsolete records. What is happening and how do you diagnose it?"
- **Deep Technical Answer:**
  1. **MVCC Dead Tuples:** In PostgreSQL, `DELETE` and `UPDATE` do not overwrite physical pages. They write an `xmax` transaction ID. Deleted tuples remain on disk until reclaimed by `VACUUM`.
  2. **Root Cause:** A long-running open transaction (e.g., an uncommitted read-only transaction or stalled backup) holds an open transaction snapshot. PostgreSQL `autovacuum` cannot remove dead tuples newer than the oldest active transaction!
  3. **Diagnostic:**
     ```sql
     SELECT pid, now() - xact_start AS duration, query 
     FROM pg_stat_activity WHERE backend_type = 'client backend' 
     ORDER BY duration DESC;
     ```
  4. Terminate the blocking PID to allow autovacuum to reclaim physical disk pages.
---

## 8. Deep-Dive Query Optimization & Engine Comparison

### 8.1 Reading `EXPLAIN (ANALYZE, BUFFERS)` Execution Plans
In PostgreSQL, `EXPLAIN (ANALYZE, BUFFERS)` executes the query, measures real execution time, and counts physical buffer hits/reads:
```
Seq Scan on orders (cost=0.00..1850.00 rows=1000 width=72) (actual time=0.015..12.350 rows=980 loops=1)
  Filter: (status = 'PENDING'::text)
  Rows Removed by Filter: 99020
  Buffers: shared hit=850 read=150
Planning Time: 0.125 ms
Execution Time: 12.450 ms
```
- **Cost Calculation Formula:**
  $$\text{Cost} = (N_{\text{pages}} \times 1.0) + (N_{\text{tuples}} \times 0.01) + (N_{\text{filter\_evals}} \times 0.0025)$$
- **Buffers Interpretation:**
  - `shared hit=850`: 850 pages ($850 \times 8\text{KB} = 6.8\text{MB}$) were read directly from the PostgreSQL shared buffer pool in RAM ($< 1$ µs per page).
  - `read=150`: 150 pages required OS filesystem cache reads or physical disk reads.
- **Scan Types:**
  - **Seq Scan:** Full physical table read. Optimal only when reading $> 20\%$ of all rows.
  - **Index Scan:** Traverses B+ Tree index, then performs random I/O reads on heap table pages for each matching row pointer.
  - **Bitmap Index Scan:** Scans B+ Tree, constructs an in-memory bitmap of matched page physical locations, sorts page IDs, and reads heap pages sequentially. Eliminates random I/O.
  - **Index Only Scan:** Satisfied entirely within the index leaf pages. Zero heap table page reads.

### 8.2 Architectural Comparison: PostgreSQL vs. MySQL InnoDB

| Dimension | PostgreSQL | MySQL (InnoDB) |
| :--- | :--- | :--- |
| **Primary Key Storage** | Heap storage; PK is a standard secondary index pointing to `ctid` (tuple ID). | Clustered index; physical table data is ordered by PK in B+ Tree. |
| **MVCC Implementation** | Tuple versioning in table pages (`xmin`, `xmax`). Requires `VACUUM`. | In-place overwrite with rollback pointers to Undo Log segments. |
| **Secondary Index Lookup**| Direct pointer (`ctid`) to heap page. Fast single-step lookup. | Points to PK value. Requires secondary lookup on PK clustered index. |
| **Concurrency Ceiling** | Process-per-connection model. High RAM per connection ($\sim 10$MB). | Thread-per-connection model. Lower RAM per connection ($\sim 2$MB). |
| **Extensibility & Data Types**| Native `JSONB`, `UUID`, Arrays, Range types, PostGIS, GiST/GIN. | Basic JSON support; limited advanced functional indexing. |
| **Index-Only Scans** | Requires Visibility Map check to confirm tuple is visible to all transactions. | Direct index-only scan without secondary visibility map checks. |
