-- ============================================================================
-- BeyondTheApi Software Engineering Handbook — Module 4 Code Examples
-- Database Engineering, SQL, Transactions & Query Optimization
-- Deterministic SQL Demonstrations & Verification Scenarios
-- ============================================================================

-- Enable foreign key constraint enforcement in SQLite / Standard RDBMS
PRAGMA foreign_keys = ON;

-- ----------------------------------------------------------------------------
-- 1. Schema DDL: Entities, Integrity Constraints, and Data Domains
-- ----------------------------------------------------------------------------

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS inventory;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE products (
    product_id INTEGER PRIMARY KEY AUTOINCREMENT,
    sku TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
    stock_quantity INTEGER NOT NULL CHECK (stock_quantity >= 0),
    category TEXT NOT NULL
);

CREATE TABLE orders (
    order_id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
    order_status TEXT NOT NULL CHECK (order_status IN ('PENDING', 'PAID', 'SHIPPED', 'CANCELLED')),
    shipping_cents INTEGER NOT NULL DEFAULT 500,
    created_at TEXT NOT NULL DEFAULT (CURRENT_TIMESTAMP)
);

CREATE TABLE order_items (
    item_id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0)
);

CREATE TABLE employees (
    employee_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    manager_id INTEGER REFERENCES employees(employee_id),
    department TEXT NOT NULL,
    salary INTEGER NOT NULL
);

-- ----------------------------------------------------------------------------
-- 2. Seed Data Population
-- ----------------------------------------------------------------------------

INSERT INTO customers (customer_id, email, full_name) VALUES
    (1, 'alice@example.com', 'Alice Smith'),
    (2, 'bob@example.com', 'Bob Jones'),
    (3, 'charlie@example.com', 'Charlie Brown'),
    (4, 'david@example.com', 'David Miller'); -- Customer with 0 orders

INSERT INTO products (product_id, sku, name, price_cents, stock_quantity, category) VALUES
    (101, 'KB-MECH-01', 'Mechanical Keyboard', 12000, 50, 'Hardware'),
    (102, 'MO-WIRE-02', 'Wireless Mouse', 4500, 100, 'Hardware'),
    (103, 'MN-4K-03', '4K Ultra Monitor', 45000, 15, 'Hardware'),
    (104, 'SW-IDE-04', 'Cloud IDE Subscription', 1500, 9999, 'Software'),
    (105, 'SW-VPN-05', 'Enterprise VPN License', 2500, 500, 'Software');

INSERT INTO orders (order_id, customer_id, order_status, shipping_cents, created_at) VALUES
    (1001, 1, 'PAID', 600, '2026-03-01 10:00:00'),
    (1002, 1, 'PAID', 0, '2026-03-02 11:30:00'),
    (1003, 2, 'SHIPPED', 800, '2026-03-03 14:15:00'),
    (1004, 3, 'PENDING', 500, '2026-03-04 09:00:00');

-- Order 1001 has 3 items (causes fan-out in naive join)
INSERT INTO order_items (item_id, order_id, product_id, quantity, unit_price_cents) VALUES
    (1, 1001, 101, 1, 12000),
    (2, 1001, 102, 2, 4500),
    (3, 1001, 104, 1, 1500),
    (4, 1002, 103, 1, 45000),
    (5, 1003, 101, 2, 12000),
    (6, 1003, 102, 1, 4500);

-- Employees hierarchy for recursive CTE and window functions
INSERT INTO employees (employee_id, name, manager_id, department, salary) VALUES
    (1, 'CEO Eleanor', NULL, 'Executive', 250000),
    (2, 'VP Marcus', 1, 'Engineering', 180000),
    (3, 'Lead Sophia', 2, 'Engineering', 140000),
    (4, 'Dev Liam', 3, 'Engineering', 110000),
    (5, 'Dev Emma', 3, 'Engineering', 115000),
    (6, 'VP Oliver', 1, 'Sales', 160000),
    (7, 'Rep Ava', 6, 'Sales', 90000),
    (8, 'Rep Noah', 6, 'Sales', 95000);

-- ============================================================================
-- 3. Core Demonstration Scenarios
-- ============================================================================

-- SCENARIO A: Join Fan-Out and Duplicate Multiplication
-- NAIVE JOIN: Joining orders and order_items duplicates order-level attributes.
-- If someone computes SUM(orders.shipping_cents), Order 1001's shipping (600) is counted 3 times!
-- CORRECT REASONING: Aggregate at the item grain first, or separate order-level from item-level metrics.

-- SCENARIO B: Three-Valued Logic and NULL Pitfalls
-- Truth tables: TRUE AND NULL -> NULL; FALSE AND NULL -> FALSE; TRUE OR NULL -> TRUE.
-- WHERE clause only retains rows where predicate evaluates to TRUE (not FALSE, not NULL/UNKNOWN).
-- 'NOT IN' with NULL in subquery returns EMPTY set because x != NULL evaluates to UNKNOWN!

-- SCENARIO C: Window Functions and Partitioning
-- DENSE_RANK() vs RANK() vs ROW_NUMBER() across department salaries.
-- Cumulative running total of employee salaries within each department.

-- SCENARIO D: Recursive CTE — Hierarchical Tree Traversal
-- Traverses organizational reporting chain from CEO down to leaf contributors, computing depth level.

-- SCENARIO E: Atomic Inventory Reservation
-- Demonstrates guarded atomic update to prevent lost updates without distributed locks:
-- UPDATE products SET stock_quantity = stock_quantity - :qty WHERE product_id = :id AND stock_quantity >= :qty;

-- ============================================================================
