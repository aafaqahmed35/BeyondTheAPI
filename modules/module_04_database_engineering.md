# Module 4 — Database Engineering, SQL, Transactions & Query Optimization

> **Authoritative Curriculum Guide**  
> **Target Runtimes:** PostgreSQL 16 (Primary RDBMS Standard), MySQL 8.0 (InnoDB), Standard SQL (ISO/IEC 9075:2023), SQLite 3.x (Local Verification)  
> **Audience:** Backend Software Engineers, System Architects, High-Bar Technical Interviewees  

---

## Executive Overview & Architectural Foundations

Every production backend service is fundamentally a state-management engine. Code written in Java, Go, Python, or TypeScript is ephemeral: processes crash, containers are rescheduled, nodes restart, and deployments cycle constantly. The relational database management system (RDBMS) is the durable authority that preserves the enterprise’s transactional state across failure domains.

Software engineering interviews at top technology companies (FAANG and Tier-1 engineering firms) evaluate candidates far beyond whether they can write a basic `SELECT` or `JOIN`. They rigorously probe:
1. **Physical Storage & Execution Plans:** How the database reads pages from disk, navigates B+ tree nodes, allocates memory buffers, and executes nested loops, hash joins, or merge joins.
2. **Concurrency & Anomaly Isolation:** What occurs when hundreds of concurrent application threads attempt to update the same row, how Multi-Version Concurrency Control (MVCC) resolves visibility, and why traditional isolation levels manifest different anomalies across PostgreSQL and MySQL.
3. **Index Selectivity & Sargability:** Why adding an index does not guarantee its use, how composite index leftmost prefixing works, and why wrapping a column in a function eliminates index usage.
4. **Architectural Boundaries:** How connection pools (e.g., HikariCP) prevent server thread exhaustion, why holding an open transaction across a third-party HTTP call causes database collapse, and how to execute zero-downtime schema migrations via the Expand-and-Contract pattern.

This module provides a coherent mental model of database engineering, connecting formal relational theory with the low-level physical mechanics of modern database engines.

---

## Table of Contents

- [Section 1 — Relational Database Foundations: Mathematical Model, Schemas, Keys, and Three-Valued Logic](#section-1--relational-database-foundations-mathematical-model-schemas-keys-and-three-valued-logic)
- [Section 2 — SQL Fundamentals: Declarative Syntax, Logical Query Processing, and Filtering](#section-2--sql-fundamentals-declarative-syntax-logical-query-processing-and-filtering)
- [Section 3 — Joins and Set Operations: Mechanics, Predicates, Semi/Anti-Joins, and Cardinality Traps](#section-3--joins-and-set-operations-mechanics-predicates-semianti-joins-and-cardinality-traps)
- [Section 4 — Data Modeling and Normalization: Dependencies, Normal Forms, and Schema Evolution](#section-4--data-modeling-and-normalization-dependencies-normal-forms-and-schema-evolution)
- [Section 5 — SQL Query Reasoning: Aggregations, Window Functions, CTEs, and Complex Patterns](#section-5--sql-query-reasoning-aggregations-window-functions-ctes-and-complex-patterns)
- [Section 6 — Indexing and Physical Storage: B+ Trees, Index Topologies, Selectivity, and Write Amplification](#section-6--indexing-and-physical-storage-b-trees-index-topologies-selectivity-and-write-amplification)
- [Section 7 — Query Optimization and Execution Plans: Cost Model, Scans, Join Algorithms, and Sargability](#section-7--query-optimization-and-execution-plans-cost-model-scans-join-algorithms-and-sargability)
- [Section 8 — Transactions and the ACID Paradigm: State Boundaries, Durability, and Invariant Preservation](#section-8--transactions-and-the-acid-paradigm-state-boundaries-durability-and-invariant-preservation)
- [Section 9 — Isolation Levels and Concurrency Anomalies: Dirty Reads, Phantom Reads, Lost Updates, and Write Skew](#section-9--isolation-levels-and-concurrency-anomalies-dirty-reads-phantom-reads-lost-updates-and-write-skew)
- [Section 10 — Locking, MVCC, and Database Engine Internals: Multi-Version Snapshots, Locks, and Vacuuming](#section-10--locking-mvcc-and-database-engine-internals-multi-version-snapshots-locks-and-vacuuming)
- [Section 11 — Constraints, Invariant Enforcement, and Safe Schema Migrations: Expand-and-Contract Architecture](#section-11--constraints-invariant-enforcement-and-safe-schema-migrations-expand-and-contract-architecture)
- [Section 12 — PostgreSQL vs. MySQL: Engine Differences, Dialect Nuances, and Production Tradeoffs](#section-12--postgresql-vs-mysql-engine-differences-dialect-nuances-and-production-tradeoffs)
- [Section 13 — Database Security, Reliability, and Recovery: SQL Injection, Parameterization, WAL, and PITR](#section-13--database-security-reliability-and-recovery-sql-injection-parameterization-wal-and-pitr)
- [Section 14 — Backend Application Access: Connection Pooling, HikariCP, ORM Tradeoffs, and N+1 Queries](#section-14--backend-application-access-connection-pooling-hikaricp-orm-tradeoffs-and-n1-queries)
- [Section 15 — High-Value Database Interview Reasoning: Question Bank and Deep Failure Analysis](#section-15--high-value-database-interview-reasoning-question-bank-and-deep-failure-analysis)
- [Section 16 — Module 4 Cheatsheet: Database Engineering & SQL](#section-16--module-4-cheatsheet-database-engineering--sql)

---

## Section 1 — Relational Database Foundations: Mathematical Model, Schemas, Keys, and Three-Valued Logic

### 1.1 The Relational Model: Relations, Tuples, and Attributes

In 1970, Edgar F. Codd introduced the relational model to decouple the logical representation of data from its physical storage layout on disk (a property known as **Logical Data Independence**). 

Prior to the relational model, hierarchical and network databases (such as IMS and CODASYL) required application programs to navigate physical storage pointers manually. If a database administrator altered an on-disk pointer chain or added an index, application code broke immediately.

Mathematically, a **relation** is a subset of the Cartesian product of a list of domains:
$$R \subseteq D_1 \times D_2 \times \dots \times D_n$$

In database engineering terminology:
- A **Relation** corresponds to a **Table**.
- A **Tuple** corresponds to an unordered **Row** (record).
- An **Attribute** corresponds to a typed **Column** (field).
- A **Domain** corresponds to a **Data Type** (e.g., 32-bit signed integer, UTF-8 text, timestamp with timezone).

```
+-----------------------------------------------------------------------+
| RELATION (Table: users)                                               |
+-----------------------------------------------------------------------+
| user_id (Domain: INT) | email (Domain: TEXT) | created_at (TIMESTAMP)|
+-----------------------------------------------------------------------+
| 1                     | alice@example.com    | 2026-03-01 10:00:00   | <- Tuple (Row)
| 2                     | bob@example.com      | 2026-03-01 10:05:00   | <- Tuple (Row)
+-----------------------------------------------------------------------+
        ^
    Attribute (Column)
```

#### Set Semantics vs. Bag (Multiset) Semantics
Pure relational algebra operates strictly on **sets**: sets contain no duplicate elements, and rows have no inherent order. 

Commercial SQL databases operate on **bags** (multisets): duplicate rows are permitted unless explicitly prevented by a `PRIMARY KEY` or `UNIQUE` constraint, and operations such as `SELECT` return duplicates unless modified by `DISTINCT`.

---

### 1.2 Key Topologies and Integrity Constraints

Data integrity is the non-negotiable invariant enforced by the RDBMS engine. Keys are the foundational primitives of relational identity and referential integrity:

1. **Super Key:** Any set of one or more attributes that uniquely identifies a tuple within a relation. If $\{user\_id\}$ is unique, then $\{user\_id, email\}$ is also a super key.
2. **Candidate Key:** A minimal super key—meaning no proper subset of the attributes can uniquely identify the tuple. Removing any attribute destroys uniqueness.
3. **Primary Key ($PK$):** The specific candidate key chosen by the database architect as the primary identifier of tuples in the table. By definition, a primary key attribute must be strictly `UNIQUE` and `NOT NULL`.
4. **Alternate Key:** Any candidate key that was not chosen as the primary key. These are enforced in SQL using `UNIQUE` constraints (e.g., `email` in a `users` table where `user_id` is the primary key).
5. **Composite Key:** A primary or candidate key composed of two or more attributes (e.g., $\{order\_id, line\_number\}$ in an `order_items` table).
6. **Foreign Key ($FK$):** An attribute or set of attributes in a referencing relation that references a candidate key (typically the primary key) of a referenced relation.

```
+-------------------+             +-----------------------+
| customers         |             | orders                |
+-------------------+             +-----------------------+
| customer_id (PK)  | <---------- | order_id (PK)         |
| email (AK, UNIQUE)|      |      | customer_id (FK)      |
| full_name         |      |      | order_total_cents     |
+-------------------+      |      +-----------------------+
                           |
                           +--- Referential Integrity Invariant:
                                Every order.customer_id MUST match an existing
                                customers.customer_id (or be NULL if allowed).
```

#### Natural Keys vs. Surrogate Keys

| Dimension | Natural Key | Surrogate Key |
| :--- | :--- | :--- |
| **Definition** | An attribute that already exists in the real-world business domain (e.g., Social Security Number, National ID, ISBN, Vehicle Identification Number). | An artificially generated, synthetic value created solely for identification (e.g., auto-incrementing integer, UUIDv4, UUIDv7). |
| **Immutability** | **Low.** Business rules change. People change names; governments reissue tax identifiers; companies rebrand domain names. | **Absolute.** Has zero external business meaning, meaning it never needs to mutate. |
| **Storage & Index Size** | Typically wider strings (e.g., 20-character alphanumerics), consuming significant B+ tree index space. | Compact fixed-width integers (4 bytes for `INT`, 8 bytes for `BIGINT`) or 16 bytes for UUIDs. |
| **Interview Recommendation** | Use natural keys as **Alternate Keys** with a `UNIQUE` constraint, but anchor primary relationships to a **Surrogate Key** (`BIGINT` identity or sequential UUIDv7). |

---

### 1.3 Entity and Referential Integrity Rules

The relational engine guarantees system-wide validity through two structural rules:

1. **Entity Integrity:** No primary key attribute may accept a `NULL` value. Every tuple must have a distinct, non-null identity.
2. **Referential Integrity:** A foreign key value must either:
   - Match an existing primary/candidate key value in the referenced relation, or
   - Be `NULL` (if the business domain permits an optional relationship, such as an employee without a manager).

#### Referential Actions on Deletion or Update
When a referenced row in a parent table is deleted or updated, the foreign key constraint enforces one of several declared actions:

- `ON DELETE RESTRICT` / `ON DELETE NO ACTION`: Aborts the deletion and raises an error if child rows reference the parent. (Default safety choice).
- `ON DELETE CASCADE`: Automatically deletes all child rows referencing the deleted parent. **Warning:** High-risk in production; deleting a parent can unintentionally delete millions of rows and trigger lock escalation.
- `ON DELETE SET NULL`: Sets the foreign key column in all referencing child rows to `NULL` (requires the child column to be nullable).
- `ON DELETE SET DEFAULT`: Sets the child foreign key column to its declared default value.

---

### 1.4 NULL Semantics and Kleene’s Three-Valued Logic (3VL)

One of the most dangerous interview traps in database engineering is assuming that SQL operates on binary Boolean logic (`TRUE` and `FALSE`). 

SQL operates on **Three-Valued Logic (3VL)**:
$$\text{Logic States} \in \{\text{TRUE}, \text{FALSE}, \text{UNKNOWN}\}$$

`NULL` does not represent zero, an empty string `""`, or a blank space. `NULL` represents **missing, unknown, or inapplicable information**.

#### The Identity Fallacy
Because `NULL` represents an unknown value, two unknown values cannot be asserted as equal:
```sql
-- Evaluates to UNKNOWN (NULL), NEVER TRUE!
SELECT NULL = NULL;     -- Result: NULL (UNKNOWN)
SELECT NULL <> NULL;    -- Result: NULL (UNKNOWN)
SELECT 100 + NULL;      -- Result: NULL (UNKNOWN)
```

To test for the presence or absence of `NULL`, SQL provides the unary operators `IS NULL` and `IS NOT NULL`.

#### Kleene's 3VL Truth Tables

```
AND Truth Table:               OR Truth Table:                NOT Truth Table:
+---------+-------+-------+    +---------+-------+-------+    +---------+-------+
| AND     | TRUE  | FALSE |    | OR      | TRUE  | FALSE |    | Input   | Output|
+---------+-------+-------+    +---------+-------+-------+    +---------+-------+
| TRUE    | TRUE  | FALSE |    | TRUE    | TRUE  | TRUE  |    | TRUE    | FALSE |
| FALSE   | FALSE | FALSE |    | FALSE   | TRUE  | FALSE |    | FALSE   | TRUE  |
| UNKNOWN | UNK   | FALSE |    | UNKNOWN | TRUE  | UNK   |    | UNKNOWN | UNK   |
+---------+-------+-------+    +---------+-------+-------+    +---------+-------+
```

#### Crucial Production Invariant: The `WHERE` Filter Clause
A SQL query's `WHERE` filter clause only retains tuples for which the predicate evaluates strictly to `TRUE`.
If a predicate evaluates to `FALSE` or `UNKNOWN`, the row is discarded!

```sql
-- Assume table employees has rows where salary IS NULL.
SELECT * FROM employees WHERE salary > 50000;
-- Rows with salary IS NULL evaluate to: (NULL > 50000) -> UNKNOWN.
-- Result: These rows are DISCARDED.

SELECT * FROM employees WHERE salary <= 50000;
-- Rows with salary IS NULL evaluate to: (NULL <= 50000) -> UNKNOWN.
-- Result: These rows are ALSO DISCARDED!
-- The union of both queries fails to return the full table!
```

---

## Section 2 — SQL Fundamentals: Declarative Syntax, Logical Query Processing, and Filtering

### 2.1 Declarative Syntax vs. Imperative Execution

In imperative languages (Java, C++, Go), the developer explicitly specifies **how** to compute a result: allocate an array, loop through indices, check a condition, and push into a list.

SQL is a **declarative** language. The developer specifies **what** data is required:
```sql
SELECT c.full_name, COUNT(o.order_id)
FROM customers c
JOIN orders o ON c.customer_id = o.customer_id
WHERE o.created_at >= '2026-01-01'
GROUP BY c.full_name
HAVING COUNT(o.order_id) > 5;
```

The database query engine parses this declaration, applies relational algebra transformations, consults table statistics, evaluates available indexes, and generates an imperative physical execution plan (e.g., scan index, construct hash table, probe keys).

---

### 2.2 Logical Query Processing Order

To reason about SQL correctly and avoid subtle bugs, an engineer must distinguish the **lexical syntax order** (how a query is written) from the **logical processing order** (how the query engine evaluates the query conceptually).

```
Lexical Order (Written):        Logical Execution Order (Conceptual):
1. SELECT                       1. FROM         (Identify sources & table references)
2. FROM                         2. ON           (Evaluate join predicates)
3. JOIN                         3. JOIN         (Materialize joined stream)
4. WHERE                        4. WHERE        (Filter individual rows)
5. GROUP BY                     5. GROUP BY     (Aggregate rows into groups)
6. HAVING                       6. HAVING       (Filter aggregated groups)
7. WINDOW                       7. SELECT       (Compute expressions, projections, aliases)
8. ORDER BY                     8. DISTINCT     (Deduplicate result set)
9. LIMIT / OFFSET               9. ORDER BY     (Sort the projected rows)
                               10. LIMIT/OFFSET (Slice final window of rows)
```

#### Why Logical Order Matters in Interviews
1. **Column Aliases in `WHERE` Clause:** You cannot reference a column alias defined in the `SELECT` list inside the `WHERE` clause:
   ```sql
   -- SYNTAX ERROR in standard SQL:
   SELECT price_cents * quantity AS total_cost
   FROM order_items
   WHERE total_cost > 10000; 
   -- Explanation: WHERE executes in Step 4. SELECT (and its alias total_cost)
   -- is not evaluated until Step 7!
   ```
2. **`WHERE` vs. `HAVING`:**
   - `WHERE` executes in Step 4: it filters **individual candidate rows** before aggregation occurs. It cannot contain aggregate functions (`SUM`, `COUNT`, `AVG`).
   - `HAVING` executes in Step 6: it filters **collapsed groups** after the `GROUP BY` clause has completed.

---

### 2.3 Filtering, Expressions, and Operator Precedence

#### Operator Precedence
SQL evaluates logical operators in the following strict order:
1. Mathematical operators (`*`, `/`, `+`, `-`)
2. Comparison operators (`=`, `<>`, `<`, `>`, `LIKE`, `IN`, `IS NULL`)
3. `NOT`
4. `AND`
5. `OR`

```sql
-- INTERVIEW TRAP:
SELECT * FROM users 
WHERE status = 'ACTIVE' OR status = 'PENDING' AND balance > 0;

-- Because AND has higher precedence than OR, this evaluates logically as:
-- status = 'ACTIVE' OR (status = 'PENDING' AND balance > 0)
-- An ACTIVE user with balance = -500 WILL BE RETURNED!
-- Always use explicit parentheses:
WHERE (status = 'ACTIVE' OR status = 'PENDING') AND balance > 0;
```

#### `CASE` Expressions: Simple vs. Searched
The SQL `CASE` expression is a scalar expression that returns a value based on conditional evaluation. It is fully deterministic and executes top-to-bottom, returning the first branch that evaluates to `TRUE`.

```sql
-- Searched CASE Expression (Most flexible, supports complex boolean logic):
SELECT order_id, total_cents,
    CASE 
        WHEN total_cents >= 100000 THEN 'TIER_1_VIP'
        WHEN total_cents >= 25000  THEN 'TIER_2_PREMIUM'
        ELSE 'TIER_3_STANDARD'
    END AS customer_tier
FROM orders;
```

---

## Section 3 — Joins and Set Operations: Mechanics, Predicates, Semi/Anti-Joins, and Cardinality Traps

### 3.1 The Relational Join Spectrum

Joins combine attributes from two relations by evaluating join predicates across candidate row pairs.

```
+-----------------------------------------------------------------------------+
| RELATIONAL JOIN VISUALIZATION                                               |
+-----------------------------------------------------------------------------+
| Table A: {1, 2, 3}                     Table B: {2, 3, 4}                   |
|                                                                             |
| INNER JOIN:      [ 2, 3 ]              (Intersection where predicate holds) |
| LEFT JOIN:       [ 1, 2, 3 ]           (All of A, matching B, or NULL)      |
| RIGHT JOIN:      [ 2, 3, 4 ]           (All of B, matching A, or NULL)      |
| FULL OUTER JOIN: [ 1, 2, 3, 4 ]        (All rows from both; NULL for non-match)|
| CROSS JOIN:      A x B (9 rows)        (Cartesian product of all pairs)     |
+-----------------------------------------------------------------------------+
```

#### Multi-Table Joins and Associativity
Inner joins are associative: $(A \bowtie B) \bowtie C \equiv A \bowtie (B \bowtie C)$. The query optimizer is free to reorder inner joins to evaluate the most selective join first.

Outer joins are **not associative**: $(A \ \text{LEFT JOIN} \ B) \ \text{LEFT JOIN} \ C \not\equiv A \ \text{LEFT JOIN} \ (B \ \text{LEFT JOIN} \ C)$. Reordering outer joins alters the cardinality and nullability of the output stream.

---

### 3.2 Join Predicates vs. Filtering Predicates: The Outer Join `ON` vs. `WHERE` Trap

One of the most frequent junior engineering errors is converting an intended `LEFT JOIN` into an accidental `INNER JOIN` by placing a filter on the right table inside the `WHERE` clause.

```sql
-- SCENARIO: Retrieve ALL customers, and their orders placed in 2026 if any.

-- INCORRECT: Accidental Inner Join Conversion
SELECT c.customer_id, c.full_name, o.order_id
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE o.created_at >= '2026-01-01';

-- WHY IT FAILS:
-- If Customer 4 has NO orders, the LEFT JOIN generates a tuple:
-- (4, 'David Miller', NULL)
-- Then, the WHERE clause evaluates: NULL >= '2026-01-01' -> UNKNOWN!
-- The row is discarded, eliminating David Miller from the output!

-- CORRECT: Filter the right table in the ON clause
SELECT c.customer_id, c.full_name, o.order_id
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id 
                  AND o.created_at >= '2026-01-01';
-- The ON clause determines which orders match. If no orders match,
-- Customer 4 is preserved with NULL in order columns.
```

---

### 3.3 Join Fan-Out and Duplicate Multiplication

When joining tables across a one-to-many ($1:N$) or many-to-many ($N:M$) relationship, row duplication is an inherent mathematical consequence of relational product formation.

```sql
-- Table: orders (order_id, customer_id, shipping_cents)
-- Order 1001 has shipping_cents = 600.
-- Table: order_items (item_id, order_id, product_id, price)
-- Order 1001 contains 3 distinct items.

-- DANGEROUS NAIVE QUERY:
SELECT o.customer_id, SUM(o.shipping_cents) AS total_shipping
FROM orders o
JOIN order_items oi ON o.order_id = oi.order_id
GROUP BY o.customer_id;
```

**The Fan-Out Bug:** Because Order 1001 has 3 order items, the `JOIN` produces 3 rows for Order 1001. When `SUM(o.shipping_cents)` is evaluated, the 600-cent shipping fee is added **three times** ($1800$ cents)!

```
+-------------------------------------------------------------------------+
| FAN-OUT DEMONSTRATION                                                   |
+-------------------------------------------------------------------------+
| orders: (order_id: 1001, shipping: 600)                                |
| order_items: item 1, item 2, item 3                                     |
|                                                                         |
| Joined Rows:                                                            |
| Row 1: order 1001 | shipping 600 | item 1                               |
| Row 2: order 1001 | shipping 600 | item 2                               |
| Row 3: order 1001 | shipping 600 | item 3                               |
|                                                                         |
| SUM(shipping) across joined rows = 600 + 600 + 600 = 1800 (CORRUPTED!)  |
+-------------------------------------------------------------------------+
```

#### The Solution: Aggregate at the Correct Grain
Never aggregate across multiple cardinalities in a single flat join. Aggregate child items in a separate subquery or CTE first, then join at the $1:1$ grain:

```sql
SELECT o.customer_id, 
       SUM(o.shipping_cents) AS total_shipping,
       SUM(item_metrics.total_item_revenue) AS total_revenue
FROM orders o
JOIN (
    SELECT order_id, SUM(quantity * unit_price_cents) AS total_item_revenue
    FROM order_items
    GROUP BY order_id
) item_metrics ON o.order_id = item_metrics.order_id
GROUP BY o.customer_id;
```

---

### 3.4 Semi-Joins and Anti-Joins: `EXISTS` vs. `IN` with `NULL` Pitfall

- **Semi-Join:** Returns rows from the first table that have at least one matching row in the second table, without duplicating rows from the first table if multiple matches exist.
- **Anti-Join:** Returns rows from the first table that have **no** matching rows in the second table.

#### The Fatal `NOT IN` with `NULL` Trap
Suppose we want to find customers who have never placed an order:

```sql
-- DANGEROUS:
SELECT * FROM customers 
WHERE customer_id NOT IN (SELECT customer_id FROM orders);
```
**The Trap:** If the `orders` table contains even a **single row** where `customer_id IS NULL`:
1. The subquery produces the set: $\{1001, 1002, \dots, \text{NULL}\}$.
2. The `NOT IN` evaluates as:
   $$\text{customer\_id} \neq 1001 \ \text{AND} \ \text{customer\_id} \neq 1002 \ \text{AND} \ \dots \ \text{AND} \ \text{customer\_id} \neq \text{NULL}$$
3. For any customer, the comparison with `NULL` evaluates to `UNKNOWN`:
   $$\text{TRUE} \ \text{AND} \ \text{TRUE} \ \text{AND} \ \text{UNKNOWN} \implies \text{UNKNOWN}$$
4. Since `WHERE` requires `TRUE`, **the query returns exactly zero rows**, completely breaking application logic!

#### The Robust Production Standard: `NOT EXISTS`
Always use `NOT EXISTS` for anti-joins:
```sql
SELECT c.*
FROM customers c
WHERE NOT EXISTS (
    SELECT 1 
    FROM orders o 
    WHERE o.customer_id = c.customer_id
);
```
`NOT EXISTS` operates on row presence. If an order with `customer_id IS NULL` exists, it evaluates `o.customer_id = c.customer_id` as `UNKNOWN`, which is not a match, preserving correct anti-join semantics.

---

## Section 4 — Data Modeling and Normalization: Dependencies, Normal Forms, and Schema Evolution

### 4.1 Functional Dependencies and Normalization Theory

A **Functional Dependency** ($X \to Y$) exists in a relation $R$ if, for any two tuples $t_1$ and $t_2$, whenever $t_1[X] = t_2[X]$, it must be that $t_1[Y] = t_2[Y]$. Attribute set $X$ uniquely determines attribute set $Y$.

Normalization is the formal mathematical decomposition of relations to minimize redundancy and eliminate **update, insertion, and deletion anomalies**.

```
+--------------------------------------------------------------------------+
| THE THREE CLASSIC MODIFICATION ANOMALIES                                 |
+--------------------------------------------------------------------------+
| Relation: emp_dept(emp_id, emp_name, dept_id, dept_name, dept_budget)    |
|                                                                          |
| 1. INSERTION ANOMALY: Cannot create a new Department without inserting a |
|    dummy employee, because emp_id is the primary key.                    |
|                                                                          |
| 2. DELETION ANOMALY: If the last employee in 'Marketing' leaves, deleting |
|    that employee row erases the department's name and budget entirely!   |
|                                                                          |
| 3. UPDATE ANOMALY: If 'Engineering' increases its budget, we must update  |
|    every single employee row assigned to Engineering. If any row is      |
|    missed, the database enters an inconsistent, corrupt state.           |
+--------------------------------------------------------------------------+
```

---

### 4.2 Progressive Normal Forms: 1NF through BCNF

```
                                  +------------+
                                  |    BCNF    | Strict Candidate Key Determinacy
                                  +------------+
                                        ^
                                  +------------+
                                  |    3NF     | No Transitive Dependencies
                                  +------------+
                                        ^
                                  +------------+
                                  |    2NF     | No Partial Key Dependencies
                                  +------------+
                                        ^
                                  +------------+
                                  |    1NF     | Atomic Attributes, No Repeating Groups
                                  +------------+
```

#### First Normal Form (1NF)
- **Rule:** Every column must contain atomic (indivisible) scalar values. No repeating groups, arrays, or comma-separated lists stored in a single column.
- **Violation:** Storing `'555-1234, 555-5678'` in a `phone_numbers` column.
- **Correction:** Decompose into a separate `customer_phone_numbers` table with one phone number per row.

#### Second Normal Form (2NF)
- **Rule:** The relation must be in 1NF, and **every non-key attribute must be fully functionally dependent on the entire primary key** (no partial key dependencies).
- **Context:** Relevant only when a table has a **composite primary key**.
- **Violation:** Table `order_items(order_id, product_id, quantity, product_name)`. Primary Key is $\{order\_id, product\_id\}$. But $product\_id \to product\_name$. The product name depends on only part of the primary key!
- **Correction:** Remove `product_name` to a separate `products(product_id, product_name)` table.

#### Third Normal Form (3NF)
- **Rule:** The relation must be in 2NF, and **no non-key attribute may be transitively dependent on the primary key** (no non-key attribute determines another non-key attribute: $X \to Y \to Z$).
- **Violation:** Table `employees(emp_id, name, dept_id, dept_name)`. Primary Key is $emp\_id$. Here $emp\_id \to dept\_id$, and $dept\_id \to dept\_name$. Thus $emp\_id \to dept\_name$ is a transitive dependency!
- **Correction:** Separate into `employees(emp_id, name, dept_id)` and `departments(dept_id, dept_name)`.

#### Boyce-Codd Normal Form (BCNF)
- **Rule:** A relation is in BCNF if and only if, for every functional dependency $X \to Y$, **$X$ is a super key**.
- **Context:** Addresses rare scenarios where a table is in 3NF but has multiple overlapping candidate keys.

---

### 4.3 Practical E-Commerce Data Model

Here is an interview-grade, 3NF relational data model for an enterprise ordering system:

```sql
CREATE TABLE customers (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    category_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE products (
    product_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id INT NOT NULL REFERENCES categories(category_id) ON DELETE RESTRICT,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    price_cents INT NOT NULL CHECK (price_cents >= 0),
    stock_quantity INT NOT NULL CHECK (stock_quantity >= 0)
);

CREATE TABLE orders (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
    order_status VARCHAR(32) NOT NULL CHECK (order_status IN ('PENDING', 'PAID', 'SHIPPED', 'CANCELLED')),
    shipping_cents INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(product_id) ON DELETE RESTRICT,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price_cents INT NOT NULL CHECK (unit_price_cents >= 0),
    CONSTRAINT uq_order_product UNIQUE (order_id, product_id)
);
```

#### Denormalization Tradeoffs
In high-throughput read workloads, strict 3NF can incur costly multi-table joins. Engineers deliberately denormalize (e.g., storing `cached_customer_name` or `historical_product_name` directly in `order_items`).
- **Benefit:** Eliminates joins; provides an immutable snapshot of historical data at transaction time (even if the product name changes tomorrow, the receipt remains accurate).
- **Cost:** Redundant storage; requires application-level coordination or triggers if mutated data must propagate.

---

## Section 5 — SQL Query Reasoning: Aggregations, Window Functions, CTEs, and Complex Patterns

### 5.1 Window Functions vs. Standard Aggregation

A standard `GROUP BY` collapses multiple rows into a single summary row per group.
A **Window Function** performs calculation across an ordered partition of rows while **retaining individual row identities**.

```
Standard GROUP BY (Collapses Rows):
Row 1 (Eng, 100k) \
Row 2 (Eng, 120k)  --> Group: (Eng, Avg: 110k) [Individual identity lost!]

Window Function OVER(PARTITION BY ...) (Preserves Rows):
Row 1 (Eng, 100k) --> Output: (Eng, 100k, Dept_Avg: 110k, Rank: 2)
Row 2 (Eng, 120k) --> Output: (Eng, 120k, Dept_Avg: 110k, Rank: 1)
```

#### Ranking Mechanics: `ROW_NUMBER()` vs. `RANK()` vs. `DENSE_RANK()`

```sql
-- Assume salaries: [100k, 100k, 90k, 80k]
SELECT salary,
       ROW_NUMBER() OVER (ORDER BY salary DESC) AS row_num,
       RANK()       OVER (ORDER BY salary DESC) AS rnk,
       DENSE_RANK() OVER (ORDER BY salary DESC) AS dense_rnk
FROM employees;
```

```
+--------+---------+-----+-----------+---------------------------------------+
| Salary | row_num | rnk | dense_rnk | Explanation                           |
+--------+---------+-----+-----------+---------------------------------------+
| 100k   | 1       | 1   | 1         | Ties share rank 1                     |
| 100k   | 2       | 1   | 1         | Ties share rank 1                     |
| 90k    | 3       | 3   | 2         | RANK() skips to 3; DENSE_RANK() is 2  |
| 80k    | 4       | 4   | 3         | Consecutive integer ranking preserved |
+--------+---------+-----+-----------+---------------------------------------+
```

---

### 5.2 Classic Problem 1: Top-$N$ Records Per Group

**Problem:** Find the top 2 highest-paid employees in each department.

```sql
WITH RankedEmployees AS (
    SELECT employee_id, name, department_id, salary,
           DENSE_RANK() OVER (
               PARTITION BY department_id 
               ORDER BY salary DESC
           ) AS dept_salary_rank
    FROM employees
)
SELECT department_id, name, salary, dept_salary_rank
FROM RankedEmployees
WHERE dept_salary_rank <= 2
ORDER BY department_id, dept_salary_rank;
```

---

### 5.3 Classic Problem 2: Latest-Record-Per-Entity Pattern

**Problem:** Retrieve the latest status update for every device in an IoT fleet.

#### Method A: Window Function with Filter
```sql
WITH RankedLogs AS (
    SELECT device_id, status, battery_pct, logged_at,
           ROW_NUMBER() OVER (
               PARTITION BY device_id 
               ORDER BY logged_at DESC
           ) AS seq
    FROM device_telemetry
)
SELECT device_id, status, battery_pct, logged_at
FROM RankedLogs
WHERE seq = 1;
```

#### Method B: PostgreSQL `DISTINCT ON` (Idiomatic, High Performance)
```sql
-- Requires index on (device_id, logged_at DESC)
SELECT DISTINCT ON (device_id) device_id, status, battery_pct, logged_at
FROM device_telemetry
ORDER BY device_id, logged_at DESC;
```

---

### 5.4 Recursive Common Table Expressions (Hierarchical Traversal)

A Recursive CTE consists of two parts joined by `UNION ALL`:
1. **Anchor Member:** The non-recursive query establishing the base result set.
2. **Recursive Member:** A query referencing the CTE itself, executing iteratively until it produces an empty set.

```sql
-- Traverse reporting hierarchy from CEO down to leaf contributors:
WITH RECURSIVE OrgChart AS (
    -- Anchor Member: Root nodes (no manager)
    SELECT employee_id, name, manager_id, 0 AS depth_level
    FROM employees
    WHERE manager_id IS NULL

    UNION ALL

    -- Recursive Member: Join employees to their managers in OrgChart
    SELECT e.employee_id, e.name, e.manager_id, oc.depth_level + 1
    FROM employees e
    JOIN OrgChart oc ON e.manager_id = oc.employee_id
)
SELECT employee_id, name, manager_id, depth_level
FROM OrgChart
ORDER BY depth_level ASC, employee_id ASC;
```

---

### 5.5 Pagination at Scale: `OFFSET` Degradation vs. Keyset Pagination

#### The `OFFSET` Performance Cliff ($O(N)$ Scanning)
```sql
SELECT * FROM orders ORDER BY created_at DESC LIMIT 20 OFFSET 500000;
```
**Why it collapses:** The database must read, sort, and iterate through **$500,020$ physical index entries or rows**, discarding the first $500,000$ to return just $20$ rows! As the user scrolls deeper, query latency degrades linearly.

#### The Production Solution: Keyset (Cursor-Based) Pagination ($O(1)$ Seek)
Instead of counting rows, navigate directly to the physical boundary of the last seen tuple:

```sql
-- Assume last seen item had: created_at = '2026-03-01 10:14:22', order_id = 984521
-- Index: (created_at DESC, order_id DESC)
SELECT * 
FROM orders
WHERE (created_at, order_id) < ('2026-03-01 10:14:22', 984521)
ORDER BY created_at DESC, order_id DESC
LIMIT 20;
```
**The Mechanics:** The B+ tree executes an exact logarithmic seek ($O(\log N)$) directly to the coordinate `('2026-03-01 10:14:22', 984521)` and scans forward by exactly $20$ leaf records. Page $1$ and page $10,000$ execute in identical sub-millisecond time.

---

## Section 6 — Indexing and Physical Storage: B+ Trees, Index Topologies, Selectivity, and Write Amplification

### 6.1 The Physical Storage Hierarchy: Pages, Buffers, and Rows

Relational engines do not read or write individual rows to disk. Storage I/O operates at the granularity of a **Page** (or Block):
- **PostgreSQL Default Page Size:** 8 KB (`8192` bytes)
- **MySQL InnoDB Default Page Size:** 16 KB (`16384` bytes)

```
+--------------------------------------------------------------------------+
| DATABASE BUFFER POOL & DISK PAGE ARCHITECTURE                            |
+--------------------------------------------------------------------------+
|  RAM: Buffer Pool (LRU Cache of 8KB/16KB Database Pages)                 |
|  [ Page 101 ] [ Page 102 ] [ Page 504 ] [ Page 505 ]                     |
|        ^                                                                 |
|        | (Page Fault: Read entire page from disk if not in cache)        |
|        v                                                                 |
|  DISK STORAGE: Data Files                                                |
|  +--------------------------------------------------------------------+  |
|  | Page Header | Line Pointers / Item IDs | Free Space | Row Data ... |  |
|  +--------------------------------------------------------------------+  |
+--------------------------------------------------------------------------+
```

When a query requests a single row, the database fetches the **entire 8KB/16KB page** containing that row into the RAM Buffer Pool.

---

### 6.2 B+ Tree Index Internals

Almost all relational indexes default to the **B+ Tree** structure. 

```
                                  [ K=50 ]                       Root Node (RAM/Disk)
                                 /        \
                    [ K=20 | K=35 ]      [ K=65 | K=80 ]         Internal Nodes
                   /       |       \    /       |       \
               [Leaves] [Leaves]  ... [Leaves] [Leaves]  ...     Leaf Nodes (Page Level)
               +----+   +----+        +----+   +----+
               |1..9|-->|20..|------->|50..|-->|65..|--> (Bidirectional Linked List)
               +----+   +----+        +----+   +----+
```

#### Why B+ Trees instead of Binary Search Trees (BST) or B-Trees?
1. **High Fan-Out and Shallow Depth:** A single 8KB page can store hundreds of key-pointer pairs. A B+ Tree with 3 or 4 levels can index hundreds of millions of rows. Traversing 3 levels requires at most 3 page reads.
2. **Internal Nodes Store Only Keys:** Internal router nodes do not store actual row data or row pointers, maximizing the number of routing keys per page.
3. **Sequential Leaf Linked List:** Leaf nodes are chained together in a doubly linked list. Range queries (e.g., `WHERE age BETWEEN 20 AND 30`) simply execute a binary search to find the leaf containing `20`, then follow forward pointers sequentially without re-traversing the tree!

---

### 6.3 Clustered Index vs. Heap with Secondary Indexes

The fundamental architectural difference between MySQL InnoDB and PostgreSQL lies in their primary data organization:

```
MySQL InnoDB: Clustered Index Model      PostgreSQL: Heap + Secondary Index Model
+-----------------------------------+    +--------------------------------------+
| PRIMARY KEY B+ TREE               |    | HEAP FILE (Unordered 8KB Pages)      |
| Leaf Nodes = The Actual Row Data  |    | Page 1: [Row A][Row B][Row C]        |
| (Rows are sorted physically by PK)|    | Page 2: [Row D][Row E]               |
+-----------------------------------+    +--------------------------------------+
                 ^                                          ^
                 | (Stores PK Value)                        | (Stores Physical TID: Page#, Offset)
+-----------------------------------+    +--------------------------------------+
| SECONDARY INDEX B+ TREE           |    | SECONDARY INDEX B+ TREE              |
| Leaf Nodes = (Secondary Key + PK) |    | Leaf Nodes = (Index Key + TID)       |
+-----------------------------------+    +--------------------------------------+
```

| Engine | Primary Storage Architecture | Secondary Index Structure | Trade-off |
| :--- | :--- | :--- | :--- |
| **MySQL (InnoDB)** | **Clustered Table:** The table *is* the primary key B+ tree. Leaf pages store full row payloads. | Leaf nodes store the **Primary Key value**. | Secondary index lookups require a **secondary lookup (Double Traverse)** to navigate the clustered PK tree unless covered. |
| **PostgreSQL** | **Heap Table:** Rows are written wherever free space exists in 8KB heap pages. | Leaf nodes store a **Tuple ID (TID)** pointing to `(Page_Number, Offset)`. | Secondary lookups directly access the heap page via TID. However, row updates in the heap can invalidate TIDs, causing write amplification (mitigated by HOT: Heap-Only Tuples). |

---

### 6.4 Composite Indexes and the Leftmost Prefix Rule

A composite index is defined over multiple columns:
```sql
CREATE INDEX idx_users_country_city_age ON users(country, city, age);
```

The B+ tree sorts data lexicographically: first by `country`, then within equal `country` values by `city`, then within equal `country` and `city` by `age`.

```
Index Sorting Order:
('CAN', 'Toronto', 25)
('CAN', 'Toronto', 30)
('CAN', 'Vancouver', 22)
('USA', 'Austin', 28)
('USA', 'Austin', 35)
('USA', 'Boston', 21)
```

#### The Leftmost Prefix Rule
- `WHERE country = 'USA' AND city = 'Austin'` $\to$ **Index Range Scan** (Optimal).
- `WHERE country = 'USA'` $\to$ **Index Range Scan** (Optimal).
- `WHERE country = 'USA' AND age = 28` $\to$ **Partial Index Use:** Uses index to filter on `country`; `age` cannot be used for B+ tree seeking because `city` was skipped.
- `WHERE city = 'Austin'` $\to$ **Cannot Use Index for Seeking!** (The index is sorted by country first; 'Austin' is scattered across different countries).

---

### 6.5 Index Selectivity and Covering Indexes

#### Selectivity Metric
$$\text{Selectivity} = \frac{\text{Cardinality (Unique Values)}}{\text{Total Rows in Table}}$$
- High selectivity ($\to 1.0$, e.g., `email`, `user_id`): Indexes are extremely effective.
- Low selectivity ($\to 0.0$, e.g., `gender`, `is_active`): Indexes are rarely effective. If 90% of rows have `is_active = TRUE`, scanning the index and then making random heap I/O calls for 90% of pages is vastly slower than a single sequential table scan.

#### Covering Indexes and Index-Only Scans
An **Index-Only Scan** occurs when every column requested by the query (`SELECT`, `WHERE`, `ORDER BY`) is stored directly within the index leaves. The database never touches the table heap/clustered table!

```sql
-- Query:
SELECT customer_id, created_at FROM orders WHERE customer_id = 45;

-- Covering Index in PostgreSQL:
CREATE INDEX idx_orders_cust_covering ON orders(customer_id) INCLUDE (created_at);
-- In MySQL:
CREATE INDEX idx_orders_cust_covering ON orders(customer_id, created_at);
```
**Advantage:** Eliminates table page fetches entirely, turning random disk reads into a compact sequential index read.

---

### 6.6 The Cost of Indexing: Write Amplification

Every index added to a table is a tax on write performance:
- An `INSERT` must append the row to the heap/clustered tree AND insert key nodes into **every secondary B+ tree**.
- A `DELETE` must remove entries from all secondary B+ trees.
- An `UPDATE` that modifies an indexed column must remove the old key and insert the new key into that B+ tree, potentially triggering costly **B+ tree page splits**.

---

## Section 7 — Query Optimization and Execution Plans: Cost Model, Scans, Join Algorithms, and Sargability

### 7.1 Query Processing Engine Lifecycle

```
[SQL Query Text] 
       |
       v
+--------------+
| Parser       | -> Syntactic validation, Abstract Syntax Tree (AST)
+--------------+
       |
       v
+--------------+
| Analyzer     | -> Semantic checks (resolve column names, schema catalogs, permissions)
+--------------+
       |
       v
+--------------+
| Optimizer    | -> Relational algebra rewrites, cost estimation across plans
+--------------+
       | (Selects lowest-cost physical plan)
       v
+--------------+
| Executor     | -> Executes plan operators (Seq Scan, Hash Join, Index Scan)
+--------------+
```

---

### 7.2 Interpreting `EXPLAIN` and `EXPLAIN ANALYZE`

- `EXPLAIN`: Outputs the query optimizer's **estimated** execution plan and costs based on static table statistics without running the query.
- `EXPLAIN ANALYZE`: **Executes the query in real-time**, outputting both the optimizer’s estimates AND the actual execution timing, buffer page hits, and row counts.

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM orders WHERE customer_id = 1001;
```

```
Sample Execution Plan Output:
Index Scan using idx_orders_customer on orders  
  (cost=0.42..8.44 rows=4 width=48) 
  (actual time=0.034..0.042 rows=3 loops=1)
  Index Cond: (customer_id = 1001)
  Buffers: shared hit=4
Planning Time: 0.112 ms
Execution Time: 0.065 ms
```

#### Decoding Plan Metrics:
- `cost=0.42..8.44`: Arbitrary unit cost model. `0.42` is startup cost (time to fetch first row); `8.44` is total cost to complete operator.
- `rows=4`: Estimated rows expected by optimizer.
- `actual rows=3`: Real rows emitted during execution. **Crucial Diagnostic:** If estimated rows differs by orders of magnitude from actual rows (e.g., estimated 1 row, actual 500,000 rows), table statistics are stale, causing the optimizer to choose poor join strategies!
- `Buffers: shared hit=4`: 4 database pages were read directly from RAM buffer pool (0 disk reads).

---

### 7.3 Physical Access Paths: Scans and Joins

#### Scan Operators
1. **Sequential Scan (Table Scan):** Reads all pages of the table sequentially from start to finish. Optimal for small tables or when reading $> 20\%$ of all rows.
2. **Index Scan:** Traverses the B+ tree to find matching leaf nodes, then makes random I/O reads into the heap/clustered table to retrieve remaining columns.
3. **Index-Only Scan:** Traverses B+ tree and reads data strictly from leaf nodes without touching heap pages.
4. **Bitmap Index Scan (PostgreSQL):** Scans the index, constructs an in-memory bitmap of matching page numbers in physical disk order, and reads heap pages sequentially. Converts random I/O into sequential I/O.

#### Physical Join Operators

```
1. Nested Loop Join:              2. Hash Join:                     3. Merge Join:
For each outer row:               Build hash table on inner table   Requires both inputs sorted.
  Scan inner index/table            For each outer row:               Walk both streams like
(Best for small inputs / indexes)     Probe hash table                  merge step of Mergesort.
```

| Join Algorithm | Preconditions | Time Complexity | Memory Profile | Optimal Workload |
| :--- | :--- | :--- | :--- | :--- |
| **Nested Loop** | One small relation, or index on inner join column. | $O(N \times M)$ naive, $O(N \log M)$ indexed | Minimal ($O(1)$) | OLTP queries returning small row counts. |
| **Hash Join** | Equi-joins (`=`), unindexed relations. | $O(N + M)$ | Heavy ($O(M)$ for in-memory hash table) | Analytical queries, large unindexed joins. |
| **Merge Join** | Equi-joins (`=`), inputs sorted on join key. | $O(N + M)$ | Low ($O(1)$) | Very large datasets where indexes provide presorted input. |

---

### 7.4 Sargability: Writing Index-Friendly Predicates

An expression is **Sargable** (**S**earch **Arg**ument **Able**) if the query optimizer can utilize a B+ tree index range seek rather than falling back to an unindexed scan.

```sql
-- NON-SARGABLE (Index Disabled): Wrapping column in a function
SELECT * FROM users WHERE YEAR(created_at) = 2026;
-- The database must invoke YEAR() on every single row in the table!
-- Execution Plan: Sequential Table Scan.

-- SARGABLE (Index Enabled): Isolating column on one side of operator
SELECT * FROM users 
WHERE created_at >= '2026-01-01 00:00:00' 
  AND created_at <  '2027-01-01 00:00:00';
-- Execution Plan: Index Range Scan on created_at.

-- NON-SARGABLE: Leading wildcard in LIKE query
SELECT * FROM customers WHERE email LIKE '%@gmail.com';
-- B+ tree is sorted from left to right; cannot seek by suffix!

-- SARGABLE: Trailing wildcard
SELECT * FROM customers WHERE email LIKE 'alice%';
-- B+ tree seeks directly to 'alice' and scans forward.
```

---

## Section 8 — Transactions and the ACID Paradigm: State Boundaries, Durability, and Invariant Preservation

### 8.1 The ACID Guarantees Defined Rigorously

A **transaction** is a logical unit of work that transitions a database from one consistent state to another consistent state.

```
                    +------------------------------------+
                    |        TRANSACTION (ACID)          |
                    +------------------------------------+
                   /          |                |          \
                  v           v                v           v
           [Atomicity]  [Consistency]    [Isolation]  [Durability]
```

- **Atomicity:** All statements within the transaction boundary complete successfully, or all changes are completely rolled back. Partial state mutation is impossible.
- **Consistency:** The transaction moves the database from one valid state satisfying all declared schema constraints, foreign keys, and invariants to another valid state.
- **Isolation:** The concurrent execution of multiple transactions results in a system state that is equivalent to a state achieved if the transactions executed serially without interference.
- **Durability:** Once a transaction commits, its modifications are permanently recorded in non-volatile storage (via Write-Ahead Logging) and will survive a catastrophic server power failure.

---

### 8.2 Savepoints and Partial Rollbacks

A **Savepoint** defines a checkpoint within the scope of an ongoing transaction. If a subsequent statement fails, the application can roll back to the savepoint without aborting the entire transaction.

```sql
BEGIN;

INSERT INTO audit_log(event) VALUES ('CHECKOUT_STARTED');

SAVEPOINT payment_attempt;

-- Attempt credit card charge debit:
INSERT INTO payments(order_id, amount_cents) VALUES (101, 5000);

-- If payment gateway rejects transaction:
ROLLBACK TO SAVEPOINT payment_attempt;

-- Order can be flagged as payment_failed, but audit_log remains!
UPDATE orders SET status = 'PAYMENT_FAILED' WHERE order_id = 101;

COMMIT;
```

---

### 8.3 Write-Ahead Logging (WAL) and Durability Mechanics

How does an RDBMS guarantee durability without forcing slow, random disk writes on every 8KB page modification?

#### The WAL Architecture
1. Before any data page in the RAM buffer pool is modified, the database appends a sequential record of the change to the **Write-Ahead Log** (WAL in PostgreSQL, Redo Log in MySQL InnoDB).
2. Sequential disk writes to WAL are orders of magnitude faster than random I/O writes across 8KB data pages.
3. When the client executes `COMMIT`, the database issues a physical `fsync()` to flush the sequential WAL buffer to disk.
4. **Crash Recovery:** If power fails immediately after commit, the dirty 8KB data pages in RAM are lost. Upon restart, the database engine reads the WAL sequentially and **replays** the committed changes forward (**REDO**), ensuring zero committed data loss!

```
+--------------------------------------------------------------------------+
| WRITE-AHEAD LOGGING (WAL) LIFECYCLE                                      |
+--------------------------------------------------------------------------+
| 1. Transaction executes UPDATE ...                                       |
| 2. Engine generates WAL record: [LSN: 5042, Page: 88, OldVal, NewVal]    |
| 3. Engine mutates 8KB page in RAM Buffer Pool (Page marked 'DIRTY')       |
| 4. COMMIT issued -> Flush WAL Buffer to disk via fsync()                 |
| 5. Dirty page written to disk asynchronously later during CHECKPOINT      |
+--------------------------------------------------------------------------+
```

---

## Section 9 — Isolation Levels and Concurrency Anomalies: Dirty Reads, Phantom Reads, Lost Updates, and Write Skew

### 9.1 Concurrency Phenomena & Anomalies

When transactions execute concurrently, uncoordinated reads and writes manifest distinct failure modes:

1. **Dirty Read ($G_1$):** Transaction $A$ mutates a row but has not committed. Transaction $B$ reads the uncommitted row. Transaction $A$ rolls back. Transaction $B$ is now operating on phantom data that never existed permanently.
2. **Non-Repeatable Read (Fuzzy Read):** Transaction $A$ reads a row. Transaction $B$ updates that row and commits. Transaction $A$ reads the same row again and observes mutated values.
3. **Phantom Read ($A3$):** Transaction $A$ reads a set of rows satisfying a search predicate (`WHERE status = 'ACTIVE'`). Transaction $B$ inserts a **new** row matching that predicate and commits. Transaction $A$ re-executes the query and observes a new "phantom" row.
4. **Lost Update ($P4$):** Transaction $A$ and Transaction $B$ concurrently read `stock = 10`. Both calculate `stock - 1 = 9` in application memory. Both execute `UPDATE stock = 9`. Two items were sold, but stock decremented by only 1!
5. **Write Skew:** Transaction $A$ and Transaction $B$ read overlapping datasets, verify a multi-row invariant, and write to disjoint rows, violating the invariant.

---

### 9.2 The ANSI SQL-92 Standard Isolation Levels

```
+-----------------------------------------------------------------------------------+
| ANSI SQL-92 ISOLATION LEVEL ANOMALY MATRIX                                        |
+--------------------+------------------+---------------------+---------------------+
| Isolation Level    | Dirty Read       | Non-Repeatable Read | Phantom Read        |
+--------------------+------------------+---------------------+---------------------+
| READ UNCOMMITTED   | Permitted        | Permitted           | Permitted           |
| READ COMMITTED     | Prevented        | Permitted           | Permitted           |
| REPEATABLE READ    | Prevented        | Prevented           | Permitted (ANSI)    |
| SERIALIZABLE       | Prevented        | Prevented           | Prevented           |
+--------------------+------------------+---------------------+---------------------+
```

#### The Critical Interview Qualification: Engine Reality vs. ANSI Standard
1. **PostgreSQL Does Not Support Dirty Reads:** In PostgreSQL, declaring `READ UNCOMMITTED` automatically maps internally to `READ COMMITTED` because MVCC never displays uncommitted tuples.
2. **PostgreSQL Repeatable Read Prevents Phantoms:** In PostgreSQL, `REPEATABLE READ` uses Snapshot Isolation, which **prevents phantom reads**!
3. **MySQL InnoDB Repeatable Read Prevents Phantoms:** MySQL InnoDB uses Next-Key Locking in `REPEATABLE READ`, which prevents phantom reads during locking queries (`SELECT FOR UPDATE`).

---

### 9.3 Write Skew and the Failure of Snapshot Isolation

**The Classic On-Call Doctor Scenario:**
- **Invariant:** A hospital must have at least one doctor on call at all times.
- **Current State:** Both Doctor Alice and Doctor Bob are on call ($2$ doctors active).
- **Concurrent Actions:** Alice and Bob both attempt to take leave simultaneously under `REPEATABLE READ` (Snapshot Isolation).

```
Transaction A (Alice)                        Transaction B (Bob)
---------------------                        -------------------
BEGIN; (Snapshot established)                BEGIN; (Snapshot established)
SELECT COUNT(*) FROM doctors                 SELECT COUNT(*) FROM doctors 
WHERE on_call = TRUE;                        WHERE on_call = TRUE;
-- Returns 2 (Invariant holds!)              -- Returns 2 (Invariant holds!)

UPDATE doctors SET on_call = FALSE           UPDATE doctors SET on_call = FALSE 
WHERE name = 'Alice';                        WHERE name = 'Bob';

COMMIT;                                      COMMIT;
```

**The Disaster:** Both transactions commit successfully! Neither transaction modified the row the other modified (disjoint updates), so no lock collision occurred. 
Result: Zero doctors on call! The multi-row invariant was completely destroyed!

#### Resolution: Serializable Snapshot Isolation (SSI) or Explicit Row Locks
```sql
-- Solution in PostgreSQL:
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
-- Transaction B will fail with serialization_failure (SQLSTATE 40001) upon commit!
```

---

### 9.4 Optimistic vs. Pessimistic Concurrency Control

#### Pessimistic Locking (`SELECT ... FOR UPDATE`)
Acquires exclusive row-level locks upfront to prevent concurrent modification:
```sql
BEGIN;
SELECT stock_quantity 
FROM products 
WHERE product_id = 101 
FOR UPDATE;

-- Row is now locked. Any concurrent transaction attempting 
-- FOR UPDATE or UPDATE on row 101 will block until this transaction commits.

UPDATE products 
SET stock_quantity = stock_quantity - 1 
WHERE product_id = 101;

COMMIT;
```
- **When to Use:** High contention workloads where collision probability is high.
- **Risk:** Bottlenecks, deadlocks, reduced system throughput.

#### Optimistic Concurrency Control (Version Check)
Does not acquire database locks during read. Checks a version token during update:
```sql
-- 1. Application reads row:
SELECT product_id, stock_quantity, version 
FROM products 
WHERE product_id = 101;
-- Returns: version = 4, stock = 10

-- 2. Application executes atomic update with version guard:
UPDATE products 
SET stock_quantity = stock_quantity - 1, 
    version = version + 1
WHERE product_id = 101 AND version = 4;

-- 3. Check rowcount:
-- If rowcount == 1: SUCCESS.
-- If rowcount == 0: RACE DETECTED! Another transaction updated version.
--                   Abort and retry from step 1 in application code.
```
- **When to Use:** Low to medium contention; web request-response cycles where holding database locks across network boundaries is unacceptable.

---

## Section 10 — Locking, MVCC, and Database Engine Internals: Multi-Version Snapshots, Locks, and Vacuuming

### 10.1 Multi-Version Concurrency Control (MVCC) Mechanics

Traditional lock-based databases used two-phase locking (2PL) for all queries, meaning reads blocked writes and writes blocked reads.

Modern RDBMS engines use **MVCC**: **Readers never block writers, and writers never block readers.**

#### How MVCC Works in PostgreSQL (Tuple Headers)
Every physical row stored on a PostgreSQL heap page contains metadata headers:
- `xmin`: The transaction ID ($XID$) that inserted this tuple.
- `xmax`: The transaction ID ($XID$) that deleted or updated this tuple (0 if not deleted/updated).

```
+--------------------------------------------------------------------------+
| POSTGRESQL MVCC TUPLE MUTATION MECHANICS                                 |
+--------------------------------------------------------------------------+
| 1. Initial State: Transaction 100 inserts Alice                          |
|    Heap Tuple: [xmin: 100, xmax: 0,   id: 1, name: 'Alice']              |
|                                                                          |
| 2. Transaction 200 updates Alice to 'Alicia':                            |
|    - Engine DOES NOT overwrite existing tuple!                           |
|    - Old Tuple: [xmin: 100, xmax: 200, id: 1, name: 'Alice']  (DEAD)     |
|    - New Tuple: [xmin: 200, xmax: 0,   id: 1, name: 'Alicia'] (LIVE)     |
+--------------------------------------------------------------------------+
```

When Transaction 250 reads the table:
- It checks its active transaction snapshot.
- If Transaction 200 is committed, it sees the new tuple (`Alicia`).
- If Transaction 200 is still active or aborted, it sees the old tuple (`Alice`).

---

### 10.2 Table Bloat and the PostgreSQL `VACUUM` Engine

Because updates and deletes append new tuple versions rather than overwriting existing records, old tuples remain physically stored on 8KB heap pages as **dead tuples**.

#### What Happens Without Vacuuming?
1. **Table Bloat:** Dead tuples consume disk space; sequential scans slow down because they must read thousands of dead pages.
2. **Transaction ID Wraparound:** PostgreSQL transaction IDs are 32-bit integers ($2^{32} \approx 4.2$ billion). If transaction IDs exceed this limit without vacuuming freezing old XIDs, the database enters emergency read-only mode to prevent catastrophic data corruption!

#### Autovacuum Worker Operations:
- Reclaims dead tuple space for reuse within the same 8KB pages.
- Updates the **Visibility Map** (enabling Index-Only scans).
- Freezes aged XIDs to prevent wraparound.
- Updates table statistics for the query optimizer (`ANALYZE`).

---

### 10.3 Lock Topologies: Shared vs. Exclusive, Table vs. Row

```
Lock Hierarchy:
Table-Level Locks:   AccessShare -> RowShare -> Shared -> Exclusive -> AccessExclusive
                            \            \          \          \               \
Row-Level Locks:         FOR SHARE      FOR UPDATE     UPDATE     DELETE      DDL (DROP, ALTER)
```

- **Shared Lock ($S$):** Allows concurrent readers. Multiple transactions can hold shared locks on the same row.
- **Exclusive Lock ($X$):** Acquired during `UPDATE`, `DELETE`, or `FOR UPDATE`. Only one transaction may hold an exclusive lock on a row.

#### Deadlocks and Resolution
A **Deadlock** occurs when two or more transactions hold locks that the other transactions need, creating a cyclic dependency graph:
- Transaction $A$ locks Row 1; requests lock on Row 2.
- Transaction $B$ locks Row 2; requests lock on Row 1.

The database engine runs an internal background **Deadlock Detection** thread. When a cycle is detected in the wait-for graph, the engine forcibly terminates one transaction with an error (e.g., `deadlock detected`), releasing its locks and allowing the other transaction to proceed.
- **Mitigation:** Always acquire locks in a consistent, globally deterministic order (e.g., sort IDs in application code before locking: `ORDER BY id`).

---

## Section 11 — Constraints, Invariant Enforcement, and Safe Schema Migrations: Expand-and-Contract Architecture

### 11.1 Declarative Invariants: Why Application Validation Is Insufficient

Junior engineers frequently claim: *"We don't need database constraints because our backend service validates everything in Java/TypeScript."*

**Why Application Validation Fails:**
1. **Concurrent Race Conditions:** Two concurrent HTTP requests check `if (count < 10)` simultaneously; both pass; both insert, resulting in 11 items. Only a database `CHECK` or `UNIQUE` constraint prevents this.
2. **Multiple Access Points:** Microservices, batch ETL jobs, analytics scripts, and administrative fixes bypass application logic. The database is the single source of truth.

```sql
ALTER TABLE accounts 
    ADD CONSTRAINT chk_balance_non_negative CHECK (balance_cents >= 0);

ALTER TABLE users 
    ADD CONSTRAINT chk_email_format CHECK (email LIKE '%@%.%');
```

---

### 11.2 The Expand-and-Contract Pattern (Zero-Downtime Migrations)

In production systems running continuous delivery (CD), database migrations must never lock tables or break running instances of older application code.

**Scenario:** Rename column `phone_number` to `contact_number`.

```
Phase 1: EXPAND                         Phase 2: DUAL WRITE
Add new column without removing old.    App writes to BOTH old and new columns.
Reads continue on old column.           Backfill historical data asynchronously.
           |                                       |
           v                                       v
Phase 3: MIGRATE READS                  Phase 4: CONTRACT
App switches reads to new column.       Drop old column safely after confirming
Verify zero traffic on old column.      no running service references it.
```

#### Safe Index Creation
In PostgreSQL, `CREATE INDEX` acquires an `ACCESS EXCLUSIVE` lock on the table, blocking all concurrent `SELECT`, `INSERT`, `UPDATE`, and `DELETE` queries!
- **Safe Production Execution:**
  ```sql
  CREATE INDEX CONCURRENTLY idx_users_email ON users(email);
  ```
  `CONCURRENTLY` performs two table scans without holding exclusive locks, allowing normal production traffic to continue unhindered.

---

## Section 12 — PostgreSQL vs. MySQL: Engine Differences, Dialect Nuances, and Production Tradeoffs

```
+-----------------------------------------------------------------------------------------+
| POSTGRESQL 16 VS. MYSQL 8.0 ARCHITECTURAL COMPARISON                                    |
+----------------------+----------------------------------+-------------------------------+
| Dimension            | PostgreSQL 16                    | MySQL 8.0 (InnoDB)            |
+----------------------+----------------------------------+-------------------------------+
| Concurrency Model    | Process-per-connection (fork)    | Thread-per-connection         |
| Primary Storage      | Heap Table + MVCC Append Only    | Clustered Index (B+ Tree)     |
| MVCC Implementation  | Tuple headers (xmin, xmax), VAC  | Undo Log rollback segments    |
| Secondary Index      | Points to physical TID (Heap)    | Points to Primary Key value   |
| Upsert Syntax        | ON CONFLICT (col) DO UPDATE ...  | ON DUPLICATE KEY UPDATE ...   |
| Returning Data       | INSERT ... RETURNING *           | Not supported in MySQL 8.0    |
| JSON Capabilities    | Advanced JSONB (Binary, GIN idx) | JSON datatype (Virtual cols)  |
| Partial Indexes      | Supported (WHERE clause)         | Not supported natively        |
| DDL Transactions     | Fully Transactional (Can ROLLBACK| Non-transactional (Implicit   |
|                      | CREATE TABLE, ALTER TABLE)       | commit on DDL)                |
+----------------------+----------------------------------+-------------------------------+
```

---

## Section 13 — Database Security, Reliability, and Recovery: SQL Injection, Parameterization, WAL, and PITR

### 13.1 SQL Injection and Parameterized Queries

SQL Injection occurs when untrusted user input is concatenated directly into a SQL query string, allowing attacker input to break out of data literals and alter the query's abstract syntax tree.

```sql
-- VULNERABLE CODE (STRING CONCATENATION):
String query = "SELECT * FROM users WHERE email = '" + userInput + "'";

-- If userInput is:  admin@example.com' OR '1'='1
-- Resulting Query:
SELECT * FROM users WHERE email = 'admin@example.com' OR '1'='1';
-- Returns all users in the database!
```

#### The Mechanical Defense: Prepared Statements
```java
String sql = "SELECT * FROM users WHERE email = ?";
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setString(1, userInput);
```
**Why it is invulnerable:** The database engine parses and compiles the SQL query structure **before** the parameter is sent. The parameter is transmitted out-of-band as literal binary data; it can never be parsed as SQL syntax.

---

### 13.2 High Availability: Replication vs. Backups

```
+-----------------------------------------------------------------------------+
| REPLICATION                               BACKUP                            |
+-----------------------------------------------------------------------------+
| Purpose: High Availability & Read Scaling | Purpose: Disaster Recovery      |
| Continuously streams WAL to Standby nodes | Periodic snapshots (pg_dump,    |
| Handles physical server hardware failure  | pg_basebackup) stored off-site  |
|                                           |                                 |
| FATAL TRAP: If a developer runs:          | Protects against human error    |
| DROP TABLE users;                         | and database corruption.        |
| Replication instantly executes DROP TABLE | Can restore to state 5 seconds  |
| across all replica nodes in 10ms!         | before the drop occurred!       |
+-----------------------------------------------------------------------------+
```

#### Point-in-Time Recovery (PITR)
PITR combines a base physical backup with archived WAL logs. To recover from a catastrophic drop table executed at `14:02:15`:
1. Restore base backup taken at `00:00:00`.
2. Replay archived WAL logs up to `recovery_target_time = '14:02:14'`.
3. Stop replay immediately before the destructive command!

---

## Section 14 — Backend Application Access: Connection Pooling, HikariCP, ORM Tradeoffs, and N+1 Queries

### 14.1 Database Connection Pooling and HikariCP Mechanics

Establishing an RDBMS connection is computationally expensive: it involves TCP 3-way handshake, TLS negotiation, authentication, memory buffer allocation, and process/thread creation.

A **Connection Pool** maintains a warm pool of pre-established, authenticated database connections, lending them to worker threads on demand.

```
[HTTP Request 1] \
[HTTP Request 2]  --> [ HikariCP Connection Pool (10 Warm Connections) ] --> [ Database ]
[HTTP Request 3] /
```

#### Sizing Formula for Connection Pools
Engineers frequently configure connection pools to 100 or 200 connections, crashing their database. The optimal pool size is derived from disk I/O and CPU core limits (the PostgreSQL/Hikari formula):

$$\text{Pool Size} = (\text{Core Count} \times 2) + \text{Effective Spindle Count}$$

A modern 16-core database server with SSD storage handles peak throughput with a pool size of **30 to 40 connections**. Exceeding this causes excessive CPU context switching and lock contention.

---

### 14.2 The Critical Anti-Pattern: Network I/O Inside Transactions

```java
// CATASTROPHIC ANTI-PATTERN IN BACKEND SERVICES:
@Transactional
public void processOrder(OrderRequest request) {
    // 1. Database connection acquired; row locked!
    Order order = orderRepository.save(new Order(request));

    // 2. SLOW THIRD-PARTY NETWORK CALL (Stripe, Twilio, Shipping API):
    PaymentResult result = paymentGateway.chargeCard(request.getCardToken()); 
    // Takes 2 to 5 seconds!

    // 3. Database update:
    order.setStatus(result.isSuccess() ? PAID : FAILED);
    orderRepository.save(order);
} // Transaction commits; connection released.
```

**Why the Database Collapses:**
- While `paymentGateway.chargeCard()` waits on external network I/O, the database connection is held open, and database row locks remain active.
- If 50 concurrent requests arrive, all 50 pooled connections are exhausted waiting on Stripe!
- The entire backend application freezes, rejecting all other user traffic.
- **Rule:** Never execute remote HTTP calls, disk I/O, or CPU-intensive tasks inside an open database transaction.

---

### 14.3 ORMs vs. Raw SQL & The $N+1$ Query Problem

The $N+1$ query problem occurs when an Object-Relational Mapping (ORM) framework executes one initial query to fetch parent records, followed by $N$ separate queries to fetch associated child records:

```sql
-- 1 Query to fetch 100 orders:
SELECT * FROM orders LIMIT 100;

-- Followed by 100 separate queries for each customer (N queries):
SELECT * FROM customers WHERE customer_id = 1;
SELECT * FROM customers WHERE customer_id = 2;
...
SELECT * FROM customers WHERE customer_id = 100;
-- Total: 101 network round trips!
```

#### Mitigation in JPA / Hibernate: `JOIN FETCH`
```sql
SELECT o FROM Order o JOIN FETCH o.customer WHERE o.status = 'PAID';
-- Emits a single SQL join query, eliminating the 100 round trips!
```

---

## Section 15 — High-Value Database Interview Reasoning: Question Bank and Deep Failure Analysis

### Question 1: Why does `SELECT * FROM table WHERE column NOT IN (SELECT other_column FROM other_table)` return 0 rows when the subquery contains a `NULL`?
- **Governing Rule:** SQL evaluates expressions using Three-Valued Logic (3VL).
- **Reasoning:** `val NOT IN (1, 2, NULL)` evaluates logically as `val <> 1 AND val <> 2 AND val <> NULL`. Any comparison with `NULL` yields `UNKNOWN`. Because `TRUE AND UNKNOWN` yields `UNKNOWN`, the entire `WHERE` clause evaluates to `UNKNOWN`. Since `WHERE` only retains rows evaluating strictly to `TRUE`, all rows are discarded.
- **Incorrect Answer Trap:** Claiming that `NULL` values are automatically ignored by `IN` and `NOT IN` clauses.
- **Production Correction:** Use `NOT EXISTS`, which evaluates set existence safely.

---

### Question 2: In a table with composite index `(A, B, C)`, will `WHERE B = 10 AND C = 20` utilize the index?
- **Governing Rule:** B+ Tree composite indexes are sorted according to the Leftmost Prefix rule.
- **Reasoning:** The index tree is physically sorted primarily by `A`. Within equal values of `A`, it is sorted by `B`. Without a predicate restricting `A`, the database engine cannot navigate the root and internal branch nodes to seek `B = 10`. The index cannot be used for a direct range seek (though a modern engine might perform an Index Full Scan if the index is smaller than the table).
- **Incorrect Answer Trap:** Claiming that the index is used normally because all queried columns are part of the index.

---

### Question 3: How does PostgreSQL prevent phantom reads under `REPEATABLE READ` if the ANSI SQL-92 standard states they are permitted?
- **Governing Rule:** PostgreSQL implements `REPEATABLE READ` using Multi-Version Concurrency Control (MVCC) Snapshot Isolation rather than lock-based 2PL.
- **Reasoning:** Under PostgreSQL's `REPEATABLE READ`, a transaction acquires a single snapshot of the database at the start of its first query. All subsequent queries see only data committed before that snapshot was established. Even if concurrent transactions insert and commit new rows, those new rows have `xmin` transaction IDs that are invisible to the established snapshot.
- **Incorrect Answer Trap:** Assuming PostgreSQL uses range locks or next-key locks like MySQL InnoDB.

---

### Question 4: What is the difference between `EXPLAIN` and `EXPLAIN ANALYZE`?
- **Governing Rule:** Query optimization vs. query execution.
- **Reasoning:** `EXPLAIN` runs only the parser and cost-based optimizer, returning cost estimates without executing the query. `EXPLAIN ANALYZE` actually executes the query against real data, measuring true runtime, buffer page hits, and emitting exact row counts.
- **Critical Production Warning:** Running `EXPLAIN ANALYZE DELETE FROM users WHERE ...` **will delete the rows**! Wrap destructive queries in `BEGIN; ... ROLLBACK;` when profiling!

---

### Question 5: Why is `OFFSET 1000000 LIMIT 10` slow, and how do you optimize it?
- **Governing Rule:** Offset pagination requires physical traversal of discarded tuples ($O(N)$ scanning).
- **Reasoning:** The engine must read, process, and discard 1,000,000 rows before emitting the requested 10 rows.
- **Optimization:** Implement Keyset Pagination (Cursor-based): `WHERE id > :last_seen_id ORDER BY id ASC LIMIT 10`, which executes a direct $O(\log N)$ B+ tree index seek.

---

### Question 6: What is Write Skew, and why does Snapshot Isolation fail to prevent it?
- **Governing Rule:** Snapshot Isolation prevents conflicting writes to the *same* row, but does not prevent concurrent transactions from writing to *different* rows based on mutually dependent read state.
- **Reasoning:** Two transactions read overlapping data, verify a global invariant, and make disjoint updates that individually look valid but collectively break the invariant. Because neither transaction modified the other's row, no write-write conflict is triggered.
- **Resolution:** Elevate to true `SERIALIZABLE` isolation or lock the read dataset explicitly with `SELECT ... FOR UPDATE`.

---

## Section 16 — Module 4 Cheatsheet: Database Engineering & SQL

### 1. Logical Query Processing Order
$$\text{FROM} \to \text{ON} \to \text{JOIN} \to \text{WHERE} \to \text{GROUP BY} \to \text{HAVING} \to \text{SELECT} \to \text{DISTINCT} \to \text{ORDER BY} \to \text{LIMIT}$$

---

### 2. Relational Key Decision Matrix

| Key Type | Uniqueness | Nullable? | Minimal? | Primary Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Key** | Enforced | Strictly No | Yes | Primary internal identity of the entity. |
| **Candidate Key** | Enforced | Strictly No | Yes | Alternate valid primary identifier. |
| **Alternate Key** | Enforced | Allowed (once) | Yes | Secondary business constraint (`UNIQUE`). |
| **Foreign Key** | Matches Referenced PK | Yes (if optional) | N/A | Enforces referential integrity across tables. |
| **Surrogate Key** | Enforced | Strictly No | Yes | Synthetic key (`BIGINT`, UUID) with no business meaning. |

---

### 3. Normalization Rules Summary

- **1NF:** Scalar, atomic attributes; no repeating groups or arrays.
- **2NF:** 1NF + No partial dependencies on composite primary keys.
- **3NF:** 2NF + No transitive dependencies ($A \to B \to C$).
- **BCNF:** For every functional dependency $X \to Y$, $X$ must be a super key.

---

### 4. ACID Properties & Concurrency Anomalies

```
+------------------------------------------------------------------------------------+
| CONCURRENCY ANOMALIES & ISOLATION PREVENTIONS                                      |
+--------------------+------------+-------------------+--------------+---------------+
| Isolation Level    | Dirty Read | Non-Repeatable Rd | Phantom Read | Write Skew    |
+--------------------+------------+-------------------+--------------+---------------+
| Read Uncommitted   | YES        | YES               | YES          | YES           |
| Read Committed     | NO         | YES               | YES          | YES           |
| Repeatable Read    | NO         | NO                | NO (PG/MySQL)| YES           |
| Serializable       | NO         | NO                | NO           | NO            |
+--------------------+------------+-------------------+--------------+---------------+
```

---

### 5. Indexing Decision Checklist
1. **Leftmost Prefix:** Composite index `(A, B, C)` can serve `(A)`, `(A, B)`, and `(A, B, C)`, but NOT `(B)` or `(C)`.
2. **Selectivity Rule:** Index high-cardinality columns. If a value matches $> 20\%$ of rows, sequential scan is faster.
3. **Sargability Rule:** Never wrap indexed columns in functions (`WHERE date_trunc(...) = ...` $\to$ Broken!).
4. **Covering Index:** Add `INCLUDE` columns to secondary indexes to allow Index-Only scans without heap reads.
5. **Zero-Downtime Rule:** Always use `CREATE INDEX CONCURRENTLY` in PostgreSQL to avoid blocking write traffic.

---

### 6. Architectural Rules of Thumb
- **HikariCP Sizing:** `Pool Size = (CPU Cores * 2) + Disk Count`. More connections cause context-switching collapse.
- **Transaction Scope:** Never execute third-party HTTP calls or slow file I/O inside an open database transaction.
- **Anti-Join Standard:** Avoid `NOT IN` with subqueries; always use `NOT EXISTS`.
- **Pagination Standard:** Use Keyset pagination (`WHERE id > :last_id LIMIT N`) instead of `OFFSET` at scale.
