#!/usr/bin/env python3
"""
BeyondTheApi Software Engineering Handbook — Module 4 Test Suite
Database Engineering, SQL, Transactions & Query Optimization

Deterministic verification of SQL mechanics:
- Foreign key constraint enforcement
- Join fan-out and duplicate multiplication
- Three-valued logic (NULL) and the NOT IN pitfall
- Window functions: DENSE_RANK, ROW_NUMBER, running aggregates
- Recursive Common Table Expressions (Hierarchical tree traversal)
- Transaction savepoints and rollback isolation
- Guarded atomic inventory reservation (concurrency invariant preservation)
- Conditional aggregation
"""

import sys
import sqlite3
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SQL_FILE = REPO_ROOT / "assets" / "code_examples" / "module_04_database_examples.sql"

def get_db_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys = ON;")
    with open(SQL_FILE, "r", encoding="utf-8") as f:
        conn.executescript(f.read())
    return conn

def test_foreign_key_enforcement():
    conn = get_db_connection()
    cur = conn.cursor()
    # Try inserting an order item referencing a non-existent product_id 999
    try:
        cur.execute(
            "INSERT INTO order_items (order_id, product_id, quantity, unit_price_cents) VALUES (1001, 999, 1, 5000);"
        )
        assert False, "Expected sqlite3.IntegrityError due to foreign key violation, but insert succeeded!"
    except sqlite3.IntegrityError:
        pass  # Successfully rejected invalid foreign key reference

    # Try deleting a customer that has active orders with ON DELETE RESTRICT
    try:
        cur.execute("DELETE FROM customers WHERE customer_id = 1;")
        assert False, "Expected sqlite3.IntegrityError due to ON DELETE RESTRICT on customer_id 1!"
    except sqlite3.IntegrityError:
        pass  # Successfully blocked deletion of referenced entity

    print("  [PASS] Foreign Key Integrity: Referential constraints and ON DELETE RESTRICT verified")

def test_join_duplicate_multiplication():
    conn = get_db_connection()
    cur = conn.cursor()

    # Naive join: Order 1001 has 3 items. Its shipping_cents (600) is duplicated 3 times.
    cur.execute("""
        SELECT SUM(o.shipping_cents) 
        FROM orders o
        JOIN order_items oi ON o.order_id = oi.order_id
        WHERE o.order_id IN (1001, 1002);
    """)
    naive_sum = cur.fetchone()[0]
    # Order 1001 has 3 items (600 * 3 = 1800). Order 1002 has 1 item (0 * 1 = 0). Total = 1800.
    assert naive_sum == 1800, f"Expected naive inflated sum 1800, got {naive_sum}"

    # Correct aggregation at the true entity grain:
    cur.execute("""
        SELECT SUM(o.shipping_cents)
        FROM orders o
        WHERE o.order_id IN (1001, 1002);
    """)
    true_sum = cur.fetchone()[0]
    # True shipping for 1001 (600) + 1002 (0) = 600.
    assert true_sum == 600, f"Expected true shipping sum 600, got {true_sum}"

    print("  [PASS] Join Fan-Out Mechanics: Demonstrated duplicate multiplication and grain correction")

def test_three_valued_logic_and_null_pitfall():
    conn = get_db_connection()
    cur = conn.cursor()

    # Seed temporary table with NULLs
    cur.execute("CREATE TABLE test_nulls (id INTEGER, ref_val INTEGER);")
    cur.execute("INSERT INTO test_nulls VALUES (1, 10), (2, 20), (3, NULL);")

    # In SQL 3-valued logic: val NOT IN (10, NULL) -> val != 10 AND val != NULL -> FALSE/UNKNOWN -> returns 0 rows!
    cur.execute("""
        SELECT COUNT(*) FROM (
            SELECT 5 AS val WHERE 5 NOT IN (SELECT ref_val FROM test_nulls)
        );
    """)
    count_not_in = cur.fetchone()[0]
    assert count_not_in == 0, f"Expected 0 rows for NOT IN with NULL subquery, got {count_not_in}"

    # NOT EXISTS correctly handles NULLs because it evaluates existence of rows matching correlated condition:
    cur.execute("""
        SELECT COUNT(*) FROM (
            SELECT 5 AS val 
            WHERE NOT EXISTS (
                SELECT 1 FROM test_nulls t WHERE t.ref_val = 5
            )
        );
    """)
    count_not_exists = cur.fetchone()[0]
    assert count_not_exists == 1, f"Expected 1 row for NOT EXISTS with NULL subquery, got {count_not_exists}"

    print("  [PASS] Three-Valued Logic: Verified NULL semantics and NOT IN vs NOT EXISTS behavior")

def test_window_functions():
    conn = get_db_connection()
    cur = conn.cursor()

    # Calculate DENSE_RANK() and cumulative running salary within Engineering department
    cur.execute("""
        SELECT name, salary,
               DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS salary_rank,
               SUM(salary) OVER (PARTITION BY department ORDER BY salary ASC 
                                 ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total
        FROM employees
        WHERE department = 'Engineering'
        ORDER BY salary ASC;
    """)
    rows = cur.fetchall()
    # Engineering employees:
    # Liam (110000) -> rank 4 (since 180k=1, 140k=2, 115k=3, 110k=4), running=110000
    # Emma (115000) -> rank 3, running=225000
    # Sophia (140000) -> rank 2, running=365000
    # Marcus (180000) -> rank 1, running=545000
    assert len(rows) == 4, f"Expected 4 Engineering employees, got {len(rows)}"
    assert rows[0][0] == 'Dev Liam' and rows[0][3] == 110000
    assert rows[1][0] == 'Dev Emma' and rows[1][3] == 225000
    assert rows[2][0] == 'Lead Sophia' and rows[2][3] == 365000
    assert rows[3][0] == 'VP Marcus' and rows[3][3] == 545000
    assert rows[3][2] == 1  # Highest salary has rank 1

    print("  [PASS] Window Functions: DENSE_RANK and cumulative running aggregates verified")

def test_recursive_cte_hierarchy():
    conn = get_db_connection()
    cur = conn.cursor()

    # Traverse organizational hierarchy starting from Eleanor (CEO)
    cur.execute("""
        WITH RECURSIVE org_tree AS (
            -- Anchor member: Eleanor has manager_id IS NULL (level 0)
            SELECT employee_id, name, manager_id, 0 AS level
            FROM employees
            WHERE manager_id IS NULL
            
            UNION ALL
            
            -- Recursive member: Find direct reports
            SELECT e.employee_id, e.name, e.manager_id, ot.level + 1
            FROM employees e
            JOIN org_tree ot ON e.manager_id = ot.employee_id
        )
        SELECT name, level FROM org_tree ORDER BY level ASC, employee_id ASC;
    """)
    rows = cur.fetchall()
    assert len(rows) == 8, f"Expected 8 employees in tree, got {len(rows)}"
    assert rows[0] == ('CEO Eleanor', 0)
    # Level 1: VP Marcus, VP Oliver
    level_1_names = [r[0] for r in rows if r[1] == 1]
    assert 'VP Marcus' in level_1_names and 'VP Oliver' in level_1_names
    # Level 3: Dev Liam, Dev Emma (reports to Sophia -> Marcus -> Eleanor)
    level_3_names = [r[0] for r in rows if r[1] == 3]
    assert 'Dev Liam' in level_3_names and 'Dev Emma' in level_3_names

    print("  [PASS] Common Table Expressions: Recursive CTE organizational tree traversal verified")

def test_transactions_and_savepoints():
    conn = get_db_connection()
    cur = conn.cursor()

    # Start explicit transaction
    cur.execute("BEGIN TRANSACTION;")
    cur.execute("INSERT INTO customers (customer_id, email, full_name) VALUES (99, 'temp@example.com', 'Temporary');")
    
    # Create Savepoint
    cur.execute("SAVEPOINT sp_before_bad_insert;")
    cur.execute("INSERT INTO customers (customer_id, email, full_name) VALUES (100, 'sp_temp@example.com', 'SP Temp');")
    
    # Roll back to savepoint
    cur.execute("ROLLBACK TO sp_before_bad_insert;")
    
    # Commit the outer transaction
    cur.execute("COMMIT;")

    # Verify Customer 99 exists, but Customer 100 was undone
    cur.execute("SELECT customer_id FROM customers WHERE customer_id IN (99, 100);")
    results = [r[0] for r in cur.fetchall()]
    assert 99 in results, "Customer 99 should have been committed"
    assert 100 not in results, "Customer 100 should have been rolled back via savepoint"

    # Now verify complete rollback
    cur.execute("BEGIN TRANSACTION;")
    cur.execute("DELETE FROM customers WHERE customer_id = 99;")
    cur.execute("ROLLBACK;")
    
    cur.execute("SELECT COUNT(*) FROM customers WHERE customer_id = 99;")
    assert cur.fetchone()[0] == 1, "Customer 99 should still exist after transaction rollback"

    print("  [PASS] Transactions & ACID: Savepoint rollback and full transaction rollback verified")

def test_guarded_atomic_inventory_update():
    conn = get_db_connection()
    cur = conn.cursor()

    # Product 103 has stock_quantity = 15
    product_id = 103
    request_qty = 10

    # Guarded atomic update: WHERE stock_quantity >= :request_qty
    cur.execute("""
        UPDATE products 
        SET stock_quantity = stock_quantity - ? 
        WHERE product_id = ? AND stock_quantity >= ?;
    """, (request_qty, product_id, request_qty))
    rows_affected = cur.rowcount
    assert rows_affected == 1, f"Expected 1 row updated for successful decrement, got {rows_affected}"

    # Verify remaining stock is 5
    cur.execute("SELECT stock_quantity FROM products WHERE product_id = ?;", (product_id,))
    stock = cur.fetchone()[0]
    assert stock == 5, f"Expected remaining stock 5, got {stock}"

    # Attempt to reserve 10 more (stock is only 5) -> Guard must fail atomically without race
    cur.execute("""
        UPDATE products 
        SET stock_quantity = stock_quantity - ? 
        WHERE product_id = ? AND stock_quantity >= ?;
    """, (request_qty, product_id, request_qty))
    rows_affected_oversell = cur.rowcount
    assert rows_affected_oversell == 0, f"Expected 0 rows updated when stock is insufficient, got {rows_affected_oversell}"

    # Stock must remain unchanged at 5
    cur.execute("SELECT stock_quantity FROM products WHERE product_id = ?;", (product_id,))
    stock_after = cur.fetchone()[0]
    assert stock_after == 5, f"Stock should remain 5, got {stock_after}"

    print("  [PASS] Concurrency Invariants: Guarded atomic update prevented inventory oversell")

def test_conditional_aggregation():
    conn = get_db_connection()
    cur = conn.cursor()

    cur.execute("""
        SELECT 
            COUNT(*) AS total_orders,
            SUM(CASE WHEN order_status = 'PAID' THEN 1 ELSE 0 END) AS paid_orders,
            SUM(CASE WHEN order_status = 'SHIPPED' THEN 1 ELSE 0 END) AS shipped_orders,
            SUM(CASE WHEN order_status = 'PENDING' THEN 1 ELSE 0 END) AS pending_orders
        FROM orders;
    """)
    total, paid, shipped, pending = cur.fetchone()
    assert total == 4, f"Expected total 4, got {total}"
    assert paid == 2, f"Expected paid 2, got {paid}"
    assert shipped == 1, f"Expected shipped 1, got {shipped}"
    assert pending == 1, f"Expected pending 1, got {pending}"

    print("  [PASS] Conditional Aggregation: CASE expression aggregation pivot verified")

def main():
    print("=================================================================")
    print("  MODULE 04 DATABASE & SQL VERIFICATION TEST SUITE               ")
    print("=================================================================")
    test_foreign_key_enforcement()
    test_join_duplicate_multiplication()
    test_three_valued_logic_and_null_pitfall()
    test_window_functions()
    test_recursive_cte_hierarchy()
    test_transactions_and_savepoints()
    test_guarded_atomic_inventory_update()
    test_conditional_aggregation()
    print("\nAll 8 Database Engineering & SQL Test Suites Passed Successfully!")
    return 0

if __name__ == "__main__":
    sys.exit(main())
