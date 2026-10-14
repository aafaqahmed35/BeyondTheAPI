# Module 01: Data Structures and Algorithms (Java)

## 1. Core Mental Models & Asymptotic Complexity

Algorithm engineering requires proving correctness and predicting resource consumption before writing code. An algorithm is defined by its **invariants**—conditions that hold true before, during, and after each state transition.

### 1.1 Asymptotic Notations & Formal Bounds
Asymptotic analysis models the rate of growth of execution time or memory footprint as input size $n \to \infty$, abstracting away hardware-specific clock cycles:

- **Big-O ($O$): Asymptotic Upper Bound.** $f(n) \in O(g(n))$ if $\exists c > 0, n_0 > 0$ such that $0 \le f(n) \le c \cdot g(n)$ for all $n \ge n_0$. Represents the worst-case envelope.
- **Big-Omega ($\Omega$): Asymptotic Lower Bound.** $f(n) \in \Omega(g(n))$ if $\exists c > 0, n_0 > 0$ such that $0 \le c \cdot g(n) \le f(n)$ for all $n \ge n_0$. Represents the intrinsic difficulty lower limit.
- **Big-Theta ($\Theta$): Asymptotically Tight Bound.** $f(n) \in \Theta(g(n)) \iff f(n) \in O(g(n)) \land f(n) \in \Omega(g(n))$.

### 1.2 Master Theorem for Divide-and-Conquer
For recurrences of the form $T(n) = aT(n/b) + f(n)$ where $a \ge 1, b > 1$:
Let the critical exponent be $c_{\text{crit}} = \log_b a$.
1. **Case 1 (Work dominated by leaves):** If $f(n) \in O(n^c)$ where $c < c_{\text{crit}}$, then $T(n) \in \Theta(n^{\log_b a})$. (e.g., Strassen's matrix multiplication).
2. **Case 2 (Work evenly split across levels):** If $f(n) \in \Theta(n^{c_{\text{crit}}} \log^k n)$ where $k \ge 0$, then $T(n) \in \Theta(n^{\log_b a} \log^{k+1} n)$. (e.g., MergeSort: $a=2, b=2, k=0 \implies \Theta(n \log n)$).
3. **Case 3 (Work dominated by root):** If $f(n) \in \Omega(n^c)$ where $c > c_{\text{crit}}$ and regularity condition $a f(n/b) \le k f(n)$ holds for $k < 1$, then $T(n) \in \Theta(f(n))$.

### 1.3 Amortized Analysis Mechanics
Amortized analysis averages the cost of a sequence of operations over time, guaranteeing that even if a single operation is expensive, the average cost across $k$ consecutive calls is bounded:
- **Aggregate Method:** Total cost of $k$ operations divided by $k$.
- **Accounting Method:** Credit is stored on data elements during cheap operations to pay for future expensive operations.
- **Potential Method (Physicist's Method):** A potential function $\Phi(D)$ maps data structure state $D$ to a real number. The amortized cost of the $i$-th operation is $\hat{c}_i = c_i + \Phi(D_i) - \Phi(D_{i-1})$. For dynamic array resizing with doubling, choosing $\Phi = 2 \cdot \text{size} - \text{capacity}$ yields $\hat{c}_i \le 3 = O(1)$ amortized append.

---

## 2. Linear Structures & Sequential Patterns

### 2.1 Array Hardware Locality vs. Linked Representations
Arrays occupy contiguous memory blocks. Element access at index $i$ is calculated via direct pointer arithmetic:
$$\text{Address}(A[i]) = \text{BaseAddress} + i \times \text{sizeof}(\text{Element})$$
This guarantees $\Theta(1)$ random access and maximizes **spatial locality**, causing CPU cache lines (typically 64 bytes) to prefetch subsequent elements into L1/L2 caches. Conversely, linked lists store elements as disconnected heap nodes linked by pointers, introducing pointer dereference overhead ($8$ bytes per reference on 64-bit JVMs with compressed OOPs disabled) and frequent cache misses.

### 2.2 OpenJDK `ArrayList` Resizing Mechanics
In Java's `ArrayList`, growth follows a $1.5\times$ expansion policy:
```java
int newCapacity = oldCapacity + (oldCapacity >> 1);
```
Choosing a growth factor $\gamma \in (1, 2)$ (such as $1.5$) ensures that previously deallocated memory blocks can be reused by modern allocators for subsequent expansions, whereas $\gamma \ge 2$ guarantees every reallocation exceeds the sum of all previously freed chunks.

### 2.3 Two Pointers & Sliding Window Patterns
1. **Converging Pointers:** Left pointer at index $0$, right pointer at $n-1$. Used on monotonically sorted arrays to locate target pairs in $O(n)$ time without allocating secondary hash structures.
2. **Fast & Slow Pointers (Floyd's Tortoise & Hare):** Fast pointer moves at $2\times$ velocity, slow pointer at $1\times$. In a linked list with cycle length $C$ and prefix length $F$, when they collide:
   $$2(F + a) = F + a + kC \implies F = kC - a$$
   Resetting one pointer to the head and advancing both at $1\times$ guarantees they meet at the exact cycle entrance.
3. **Sliding Window Invariant:** Window $[\text{left}, \text{right}]$ expands by advancing `right` and incorporating element state. When window invariants are violated (e.g., character count exceeds threshold), advance `left` to restore the invariant:
   ```java
   int left = 0, maxLen = 0;
   Map<Character, Integer> counts = new HashMap<>();
   for (int right = 0; right < s.length(); right++) {
       char c = s.charAt(right);
       counts.put(c, counts.getOrDefault(c, 0) + 1);
       while (counts.get(c) > k) { // Invariant violation
           char leftChar = s.charAt(left);
           counts.put(leftChar, counts.get(leftChar) - 1);
           left++;
       }
       maxLen = Math.max(maxLen, right - left + 1);
   }
   ```

### 2.4 Prefix Sums & Difference Arrays
- **1D Prefix Sum:** Precompute $P[i] = \sum_{j=0}^{i-1} A[j]$. Any range sum $\sum_{k=L}^{R} A[k] = P[R+1] - P[L]$ in $O(1)$ time.
- **Difference Array:** To apply $m$ range updates $+V$ to ranges $[L, R]$, compute $D[L] += V$ and $D[R+1] -= V$. A single prefix sum pass computes the final array in $O(n + m)$ rather than $O(n \times m)$.

---

## 3. Binary Search & Monotonic Predicates

Binary search applies to any domain where a boolean condition $P(x)$ transitions monotonically from `false` to `true` across the search space (or vice-versa).

### 3.1 Overflow-Safe Invariant Template
To prevent 32-bit signed integer overflow when `low + high > Integer.MAX_VALUE`, always calculate midpoints using:
$$\text{mid} = \text{low} + \frac{\text{high} - \text{low}}{2}$$

```java
// Lower Bound (bisect_left): Returns first index where predicate P(mid) is true
public static int lowerBound(int low, int high, IntPredicate predicate) {
    while (low < high) {
        int mid = low + (high - low) / 2;
        if (predicate.test(mid)) {
            high = mid; // Candidate found; search left for earlier occurrence
        } else {
            low = mid + 1; // Discard left half
        }
    }
    return low; // Points to first index satisfying predicate, or high if none
}
```

---

## 4. Hash Tables & Collision Resolution

### 4.1 Collision Resolution Mechanics
- **Separate Chaining:** Buckets store linked lists or balanced search trees of entries. Under hash collisions, worst-case lookup degrades to $O(n)$ (linked list) or $O(\log n)$ (treeified).
- **Open Addressing (Linear/Quadratic Probing, Double Hashing):** All entries reside within the main bucket array. Deleted slots require tombstone markers (`DELETED`) to prevent prematurely terminating subsequent search probes.

### 4.2 OpenJDK `HashMap` Architecture
1. **Power-of-Two Table Capacity:** Capacity is strictly $2^k$. Index computation uses bitwise masking instead of modulo division:
   $$\text{index} = (n - 1) \ \& \ \text{hash}$$
2. **Bit Spreading / Perturbation Function:** Shifts high 16 bits down to XOR with low 16 bits to prevent clustering when keys differ only in high bits:
   $$\text{hash} = \text{key.hashCode}() \oplus (\text{key.hashCode}() \ggg 16)$$
3. **Treeification:** When bucket collision chain length $\ge 8$ and table capacity $\ge 64$, the bucket is converted from a singly-linked list to a Red-Black Tree (`TreeNode`), guaranteeing $O(\log n)$ worst-case lookup during hostile hash collision attacks. When collisions drop to $\le 6$, the bucket is untreeified back to a linked list.

---

## 5. Stacks, Queues & Priority Heaps

### 5.1 Monotonic Stacks
A monotonic stack maintains elements in strictly increasing or decreasing order. Used to find the **Next Greater Element** or **Previous Greater Element** in $O(n)$ total time across an array of length $n$:
```java
int[] result = new int[n];
Deque<Integer> stack = new ArrayDeque<>(); // Stores indices
for (int i = 0; i < n; i++) {
    while (!stack.isEmpty() && arr[stack.peek()] < arr[i]) {
        int idx = stack.pop();
        result[idx] = arr[i]; // arr[i] is next greater element for idx
    }
    stack.push(i);
}
```

### 5.2 `ArrayDeque` vs. `LinkedList`
In Java, `ArrayDeque` consistently outperforms `LinkedList` for both stack (`push`/`pop`) and queue (`offer`/`poll`) operations. `ArrayDeque` uses an unboxed circular array buffer with bitwise wraparound (`(head - 1) & (elements.length - 1)`), whereas `LinkedList` allocates a new `Node` object on the heap per operation, inducing GC pressure and cache misses.

### 5.3 Binary Heaps & Linear-Time Heapify
A binary heap is a complete binary tree stored in an array:
- Parent of index $i$: $\lfloor (i - 1) / 2 \rfloor$
- Left child of index $i$: $2i + 1$; Right child: $2i + 2$
- **Sift-Up (Insert):** $O(\log n)$ swaps along height.
- **Sift-Down (Extract-Min):** $O(\log n)$ swaps along height.
- **Linear Build-Heap (`heapify`):** Building a heap from an unsorted array runs in $O(n)$ time via bottom-up sift-down across non-leaf nodes:
  $$\sum_{h=0}^{\lfloor \log_2 n \rfloor} \frac{n}{2^{h+1}} \times O(h) = \frac{n}{2} \sum_{h=0}^{\infty} \frac{h}{2^h} = \frac{n}{2} \times 2 = O(n)$$

---

## 6. Trees, Graphs & Disjoint Sets

### 6.1 Binary Search Trees & Balanced Invariants
A BST satisfies: $\forall x \in \text{left}(N), x.\text{key} < N.\text{key}$ and $\forall y \in \text{right}(N), y.\text{key} > N.\text{key}$. In-order traversal yields keys in strictly ascending sorted order.
- **Unbalanced BST:** Degenerates to $O(n)$ linked list on presorted input.
- **Red-Black Tree:** Guarantees height $h \le 2\log_2(n+1)$ via 5 invariants: (1) Every node is red or black; (2) Root is black; (3) All leaf nodes (`NIL`) are black; (4) Red nodes cannot have red children; (5) Every path from node to descendant leaf has the same black-height.

### 6.2 Graph Traversals & Cycle Detection
- **BFS:** Uses a FIFO queue. Computes single-source shortest path in unweighted graphs in $O(V + E)$ time.
- **DFS:** Uses recursive call stack or explicit LIFO stack. In directed graphs, cycle detection requires tracking three node states:
  - **White (0):** Unvisited.
  - **Gray (1):** Currently visiting (on active recursion stack). Encountering a Gray node proves a **back-edge**, detecting a directed cycle.
  - **Black (2):** Fully processed.

### 6.3 Dijkstra's Shortest Path & Topological Sort
1. **Dijkstra's Algorithm:** Computes shortest path from single source on non-negative weighted graphs in $O((V + E) \log V)$ using a min-heap priority queue. Edge relaxation rule:
   $$\text{if } d[u] + w(u, v) < d[v] \implies d[v] = d[u] + w(u, v)$$
   *Caveat:* Dijkstra fails on graphs with negative edge weights; use Bellman-Ford ($O(V \times E)$) instead.
2. **Kahn's Topological Sort:** Computes dependency ordering on Directed Acyclic Graphs (DAGs) in $O(V + E)$ time:
   - Calculate in-degree of all vertices.
   - Enqueue all nodes with $\text{in-degree} = 0$.
   - While queue not empty: dequeue $u$, append to order, decrement in-degree of all neighbors $v$. If $\text{in-degree}(v) == 0$, enqueue $v$.
   - If total processed nodes $< |V|$, the graph contains a directed cycle.

### 6.4 Disjoint Set Union (Union-Find)
Tracks partitioned sets with near-$O(1)$ operations using **Path Compression** and **Union by Rank**:
```java
class UnionFind {
    private final int[] parent, rank;
    public UnionFind(int n) {
        parent = new int[n]; rank = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
    }
    public int find(int i) {
        if (parent[i] != i) parent[i] = find(parent[i]); // Path compression
        return parent[i];
    }
    public boolean union(int i, int j) {
        int rootI = find(i), rootJ = find(j);
        if (rootI == rootJ) return false; // Already connected
        if (rank[rootI] < rank[rootJ]) parent[rootI] = rootJ;
        else if (rank[rootI] > rank[rootJ]) parent[rootJ] = rootI;
        else { parent[rootJ] = rootI; rank[rootI]++; }
        return true;
    }
}
```
Any sequence of $m$ operations over $n$ elements executes in $O(m \cdot \alpha(n))$ time, where $\alpha(n)$ is the Inverse Ackermann function ($\alpha(n) \le 4$ for all practical inputs).

---

## 7. Dynamic Programming & Sorting Master Matrix

### 7.1 Dynamic Programming Formulation Framework
DP applies when a problem exhibits **Optimal Substructure** (optimal solution contains optimal sub-solutions) and **Overlapping Subproblems**.
1. **State Definition:** Explicitly define what $DP[i][j]$ represents in plain English.
2. **Base Cases:** Smallest subproblems solvable by inspection (e.g., $DP[0] = 0$).
3. **State Transition Relation:** Express $DP[i]$ as a function of previously computed states.
4. **Order of Computation:** Ensure dependencies are evaluated prior to dependent states.
5. **Space Optimization:** If $DP[i]$ depends only on row $i-1$, reduce memory from $O(n^2)$ to $O(n)$ using rolling arrays or single-array backwards updates (e.g., 0/1 Knapsack: update backwards from capacity $W$ down to $w_i$ to prevent counting the same item twice).

### 7.2 Sorting Algorithms Comparative Matrix

| Algorithm | Best Time | Avg Time | Worst Time | Aux Space | Stable? | Mechanical Notes |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **QuickSort** | $O(n \log n)$ | $O(n \log n)$ | $O(n^2)$ | $O(\log n)$ | No | $O(n^2)$ occurs when pivot is extremum on presorted input. Mitigate with randomized or median-of-three pivot. |
| **MergeSort** | $O(n \log n)$ | $O(n \log n)$ | $O(n \log n)$ | $O(n)$ | Yes | Guaranteed $O(n \log n)$ bound. Preferred for linked lists and external disk sorting. |
| **HeapSort** | $O(n \log n)$ | $O(n \log n)$ | $O(n \log n)$ | $O(1)$ | No | In-place sorting with zero extra space. Cache-unfriendly due to non-contiguous heap index jumps. |
| **TimSort** | $O(n)$ | $O(n \log n)$ | $O(n \log n)$ | $O(n)$ | Yes | Hybrid merge/insertion sort. Detects natural ascending/descending runs. Java's default for objects. |
| **Counting Sort** | $O(n + k)$ | $O(n + k)$ | $O(n + k)$ | $O(k)$ | Yes | Non-comparison sort. Requires small integer range $k$. |

---

## 8. Failure Modes, Edge Cases & Interview Traps

| Component / Pattern | Failure Mode | Root Cause | Preventive Invariant |
| :--- | :--- | :--- | :--- |
| **Binary Search** | Infinite loop or missed match | `low = mid` without $+1$ causing non-terminating loop when `high - low == 1`. | Use strict lower-bound template: `low = mid + 1` and `high = mid`. |
| **Fast/Slow Pointers** | `NullPointerException` | Accessing `fast.next.next` without validating `fast != null && fast.next != null`. | Check loop condition: `while (fast != null && fast.next != null)`. |
| **0/1 Knapsack DP** | Item counted multiple times | Updating DP array forwards in 1D space, overwriting earlier capacities needed by same step. | Iterate capacity $w$ backwards from $W$ down to $\text{weight}[i]$. |
| **Graph Traversal** | StackOverflowError | Cyclic graph traversed via DFS without tracking visited nodes. | Track visited nodes via boolean array or 3-color states. |
| **Priority Queue** | Stale elements | Updating object fields while inside `PriorityQueue` without re-inserting. | Re-insert modified object or store immutable snapshot pairs. |
---

## 9. High-Bar Interview Scenarios & Algorithmic Synthesis

### Scenario 1: Design an In-Memory LFU (Least Frequently Used) Cache in $O(1)$ Time
- **Problem Statement:** Implement an LFU cache with `get(key)` and `put(key, value)` executing in strictly $O(1)$ time complexity. When capacity is exceeded, evict the key with minimum access frequency. If multiple keys share the minimum frequency, evict the least recently used among them.
- **Structural Blueprint:**
  1. `Map<Integer, Node> keyTable`: Maps key to node storing value and frequency.
  2. `Map<Integer, DoublyLinkedList> freqTable`: Maps each frequency $f$ to a doubly linked list of nodes with frequency $f$.
  3. `int minFreq`: Tracks current minimum frequency across all cached elements.
- **Causal Invariant:** When `get(key)` is invoked, node frequency increments from $f$ to $f+1$. Remove node from `freqTable.get(f)`. If `freqTable.get(f)` becomes empty and $f == 	ext{minFreq}$, increment $	ext{minFreq} \mathrel{+}= 1$. Append node to `freqTable.get(f+1)`. All operations execute via pointer manipulation in $O(1)$ time.

### Scenario 2: Median of Two Sorted Arrays in $O(\log(\min(m, n)))$ Time
- **Problem Statement:** Given sorted arrays $A$ of size $m$ and $B$ of size $n$, compute the combined median in logarithmic time.
- **Mathematical Invariant:** Partition $A$ at cut index $i$ and $B$ at cut index $j = \lfloor (m + n + 1)/2 
floor - i$. The partition is valid iff:
  $$A[i-1] \le B[j] \quad \land \quad B[j-1] \le A[i]$$
- **Binary Search Derivation:** Binary search exclusively on the smaller array ($m \le n$). If $A[i-1] > B[j]$, partition $i$ is too far right $\implies$ search left. If $B[j-1] > A[i]$, partition $i$ is too far left $\implies$ search right. Runs in $O(\log(\min(m, n)))$ time with $O(1)$ space.

### Scenario 3: Trapping Rain Water in $O(n)$ Time and $O(1)$ Space
- **Problem Statement:** Given $n$ non-negative integers representing elevation map widths of 1, compute total trapped water.
- **Two-Pointer Invariant:** Water trapped at index $i$ is $\min(	ext{maxLeft}, 	ext{maxRight}) - 	ext{height}[i]$. Using two pointers `left = 0, right = n - 1` maintaining running `leftMax` and `rightMax`:
  $$	ext{if } 	ext{leftMax} < 	ext{rightMax} \implies 	ext{water} \mathrel{+}= \max(0, 	ext{leftMax} - 	ext{height}[	ext{left}]); \quad 	ext{left}++$$
  Because `leftMax < rightMax`, the right boundary is guaranteed sufficiently tall to contain water up to `leftMax`, eliminating the need to inspect intermediate heights.
---

## 10. Advanced Algorithmic Patterns & Derivations

### 10.1 In-Place Singly Linked List Reversal
Reversing a singly linked list in-place in $\Theta(n)$ time and $O(1)$ space requires maintaining three pointer references: `prev`, `curr`, and `next`:
```
Initial:     [prev = null]    [curr -> Node A] -> [Node B] -> [Node C]
Step 1:      Save next = curr.next (Node B)
             curr.next = prev (null)
             Advance prev = curr (Node A), curr = next (Node B)
Final:       [Node C] -> [Node B] -> [Node A] -> null (return prev)
```
```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null, curr = head;
    while (curr != null) {
        ListNode nextTemp = curr.next; // Preserve forward link
        curr.next = prev;              // Invert link pointer
        prev = curr;                   // Advance prev
        curr = nextTemp;               // Advance curr
    }
    return prev; // New head of reversed list
}
```

### 10.2 Levenshtein Edit Distance Matrix & Transitions
Given two strings $s_1$ (length $m$) and $s_2$ (length $n$), compute minimum insertions, deletions, and substitutions to convert $s_1 \to s_2$.
- **State Definition:** $DP[i][j]$ is the minimum edit distance between prefix $s_1[0..i-1]$ and $s_2[0..j-1]$.
- **Recurrence Relation:**
  $$DP[i][j] = \begin{cases} DP[i-1][j-1] & \text{if } s_1[i-1] == s_2[j-1] \\ 1 + \min\Big(DP[i-1][j], DP[i][j-1], DP[i-1][j-1]\Big) & \text{if } s_1[i-1] \ne s_2[j-1] \end{cases}$$
  where $DP[i-1][j]$ represents **Deletion**, $DP[i][j-1]$ represents **Insertion**, and $DP[i-1][j-1]$ represents **Substitution**.
- Space optimizes from $O(m \times n)$ down to $O(\min(m, n))$ using two rolling 1D rows.

### 10.3 Morris In-Order Tree Traversal ($O(1)$ Auxiliary Space)
Standard tree traversals require $O(h)$ call stack space. **Morris Traversal** achieves strictly $O(1)$ auxiliary space without recursion or stacks by establishing temporary **threaded pointers**:
1. If current node has no left child: visit current, move to right child.
2. If current node has a left child: find its **in-order predecessor** (rightmost node in left subtree).
   - If predecessor's right pointer is `null`: set `predecessor.right = curr` (create thread), move `curr = curr.left`.
   - If predecessor's right pointer is `curr`: set `predecessor.right = null` (revert tree structure), visit `curr`, move `curr = curr.right`.
