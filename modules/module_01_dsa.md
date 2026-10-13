# MODULE 1 — DATA STRUCTURES AND ALGORITHMS
## Conceptual Foundations, Pattern Recognition, Correctness, and Complexity Analysis

> **Authoritative Curriculum Source Artifact**  
> **Target Audience:** B.Tech Computer Science Fresher / Associate Software Engineer  
> **Primary Implementation Language:** Java (OpenJDK 21 LTS)  
> **Prerequisites:** Basic procedural programming (variables, loops, conditionals)

---

## Table of Contents
- [Section 1 — How to Think About Algorithms](#section-1--how-to-think-about-algorithms)
- [Section 2 — Complexity Analysis](#section-2--complexity-analysis)
- [Section 3 — Arrays and Strings](#section-3--arrays-and-strings)
- [Section 4 — Hashing and Hash-Based Collections](#section-4--hashing-and-hash-based-collections)
- [Section 5 — Two Pointers](#section-5--two-pointers)
- [Section 6 — Sliding Window](#section-6--sliding-window)
- [Section 7 — Prefix Sums and Range Reasoning](#section-7--prefix-sums-and-range-reasoning)
- [Section 8 — Binary Search](#section-8--binary-search)
- [Section 9 — Linked Lists](#section-9--linked-lists)
- [Section 10 — Stacks and Queues](#section-10--stacks-and-queues)
- [Section 11 — Trees and Binary Search Trees](#section-11--trees-and-binary-search-trees)
- [Section 12 — Heaps and Priority Queues](#section-12--heaps-and-priority-queues)
- [Section 13 — Graph Fundamentals and Traversal](#section-13--graph-fundamentals-and-traversal)
- [Section 14 — Essential Graph Algorithms](#section-14--essential-graph-algorithms)
- [Section 15 — Recursion and Backtracking](#section-15--recursion-and-backtracking)
- [Section 16 — Greedy Algorithms](#section-16--greedy-algorithms)
- [Section 17 — Dynamic Programming](#section-17--dynamic-programming)
- [Section 18 — Sorting and Searching Trade-Offs](#section-18--sorting-and-searching-trade-offs)
- [Section 19 — Pattern Recognition and Problem-Solving Method](#section-19--pattern-recognition-and-problem-solving-method)
- [Section 20 — Interview Practice and Self-Assessment](#section-20--interview-practice-and-self-assessment)

---

## Section 1 — How to Think About Algorithms

### 1.1 What an Algorithm Is
An **algorithm** is a well-defined, finite sequence of unambiguous computational steps that transforms a valid input into a specified output. It is not syntax, nor is it a programming language trick. An algorithm is a mathematical transformation operating on abstract data models.

```
       +------------------+
       |   Valid Input    |   Satisfies Preconditions P
       +--------+---------+
                |
                v
       +------------------+
       | Finite Sequence  |   Maintains Loop Invariants I
       | of Transitions   |   Guarantees Termination
       +--------+---------+
                |
                v
       +------------------+
       | Correct Output   |   Satisfies Postconditions Q
       +------------------+
```

### 1.2 Preconditions, Postconditions, and Correctness
A rigorous algorithm operates as a formal contract:
- **Preconditions ($P$):** The contractual assumptions the algorithm requires of its input. Examples: "The input array is non-null and sorted in non-decreasing order," or "The graph contains no negative-weight cycles." If preconditions are violated, the algorithm makes zero behavioral guarantees.
- **Postconditions ($Q$):** The contractual state of the system upon termination. Examples: "Returns the smallest index $i$ such that $A[i] = \text{target}$, or $-1$ if no such index exists."
- **Correctness:** An algorithm is **totally correct** if and only if for every input satisfying $P$, the algorithm halts and the terminal state satisfies $Q$. If an algorithm is guaranteed to produce correct results provided it halts, but termination is not proven, it is merely **partially correct**.

### 1.3 Invariants: The Engine of Algorithmic Proof
A **loop invariant** is a formal assertion about program state that holds true:
1. **Initialization:** Prior to the first iteration of the loop.
2. **Maintenance:** If it is true before an iteration, it remains true before the next iteration.
3. **Termination:** When the loop terminates, the invariant, combined with the loop termination condition, yields a state that directly proves the postcondition.

In FAANG technical interviews, candidates who state and defend their invariants stand out immediately from candidates who blindly guess pointer increments and boundary indexes.

### 1.4 Brute-Force Reasoning and Eliminating Redundant Work
Every algorithmic optimization begins with a correct **brute-force baseline**:
1. **Enumerate the State Space:** What is the naive universe of possibilities? For subarray problems, there are $O(n^2)$ contiguous subarrays. For subset problems, there are $2^n$ combinations.
2. **Identify Repeated Work (Redundancy):** Where does the brute force recalculate what it already computed?
   - In range sums, re-adding elements from index $i$ to $j$ when the sum of $i$ to $j-1$ was already computed.
   - In search, scanning elements one-by-one when their sorted order already rules out half the search space.
3. **Select an Appropriate Data Structure:** Choose a structure whose access and mutation primitives eliminate that specific bottleneck (e.g., hash maps for $O(1)$ complement lookups, prefix arrays for $O(1)$ range sums, heaps for $O(1)$ extrema inspection).
4. **Time-Space Trade-Offs:** Recognize that lower latency almost always demands precomputation or auxiliary memory.

### 1.5 Derivation vs. Memorization
Never memorize code snippets. Derive solutions through structural analysis:
- **Step 1:** Formulate small, manual concrete examples on paper.
- **Step 2:** Observe the mechanical steps your human mind takes to find the answer without exhaustive search.
- **Step 3:** Translate those human observations into state variables, boundaries, and invariants.
- **Step 4:** Test boundary inputs (empty, single-element, all duplicates, negative numbers).

---

## Section 2 — Complexity Analysis

### 2.1 The Meaning of Input Size $n$
The parameter $n$ represents the number of fundamental units in the input:
- For an array of integers, $n$ is the element count.
- For a string, $n$ is the character count.
- For a graph $G = (V, E)$, input size is bivariate: $|V|$ vertices and $|E|$ edges.
- For numeric primality testing, $n$ is the number of **bits** required to represent the number, not the numeric value itself.

### 2.2 Time vs. Space Complexity
- **Time Complexity:** The number of elementary operations (comparisons, arithmetic instructions, pointer assignments) executed as a function of input size.
- **Auxiliary Space:** The extra working memory allocated by the algorithm to solve the problem, **excluding** the memory occupied by the input and output.
- **Total Space:** $\text{Input Space} + \text{Auxiliary Space}$. In interview discussions, always clarify whether you are reporting auxiliary space or total space.

### 2.3 Asymptotic Notations: Big O, Big $\Omega$, and Big $\Theta$
Formally, let $f(n)$ and $g(n)$ be functions mapping positive integers to positive reals:
1. **Big O ($O$ - Asymptotic Upper Bound):**  
   $f(n) \in O(g(n))$ if there exist positive constants $c$ and $n_0$ such that:
   $$0 \le f(n) \le c \cdot g(n) \quad \text{for all } n \ge n_0$$
   *Meaning:* The growth rate of $f(n)$ is bounded above by $g(n)$.
2. **Big Omega ($\Omega$ - Asymptotic Lower Bound):**  
   $f(n) \in \Omega(g(n))$ if there exist positive constants $c$ and $n_0$ such that:
   $$0 \le c \cdot g(n) \le f(n) \quad \text{for all } n \ge n_0$$
   *Meaning:* The growth rate of $f(n)$ is bounded below by $g(n)$.
3. **Big Theta ($\Theta$ - Asymptotically Tight Bound):**  
   $f(n) \in \Theta(g(n))$ if and only if $f(n) \in O(g(n))$ and $f(n) \in \Omega(g(n))$.

```
    f(n) ^
         |                         c1 * g(n) [Upper Bound: O]
         |                       /
         |                      /    f(n) [Tight: Theta]
         |                     /    /
         |                    /    /    c2 * g(n) [Lower Bound: Omega]
         |                   /    /    /
         |                  /    /    /
         |                 /    /    /
         +----------------+----+----+-------------------------> n
                         0    n0
```

> **CRITICAL INTERVIEW MISTAKE:** Big O does **not** mean "worst-case." Big O is an upper bound on *any* function. An algorithm can have a best-case time complexity of $O(1)$, an average-case of $O(n)$, and a worst-case of $O(n^2)$. Stating "Quicksort is $O(n \log n)$" is incomplete; you must specify: "Quicksort runs in $O(n \log n)$ expected average time, but $O(n^2)$ worst-case time."

### 2.4 Asymptotic Growth Classes

| Class | Name | Intuition & Typical Trigger | Scalability ($n = 10^6$) |
| :--- | :--- | :--- | :--- |
| $O(1)$ | Constant | Direct array index access, hash table lookup, basic math | Instantaneous ($\approx 1$ op) |
| $O(\log n)$ | Logarithmic | Dividing search space in half (Binary Search, balanced tree ops) | $\approx 20$ ops |
| $O(n)$ | Linear | Single pass through an array or linked list, counting | $10^6$ ops ($\approx 10\text{ ms}$) |
| $O(n \log n)$ | Linearithmic | Optimal comparison sorts (MergeSort, HeapSort), divide-and-conquer | $\approx 2 \times 10^7$ ops |
| $O(n^2)$ | Quadratic | Nested loops over input, comparing all pairs | $10^{12}$ ops (Time Limit Exceeded!) |
| $O(2^n)$ | Exponential | Generating all subsets, brute-force recursion | Prohibitive for $n > 30$ |
| $O(n!)$ | Factorial | Generating all permutations | Prohibitive for $n > 12$ |

### 2.5 Deriving Loop Complexity from Code

#### Rule 1: Sequential Statements (Sum Rule)
If block $A$ takes $O(f(n))$ and sequential block $B$ takes $O(g(n))$, the total time is $O(f(n) + g(n)) = O(\max(f(n), g(n)))$.

#### Rule 2: Independent Nested Loops (Product Rule)
```java
for (int i = 0; i < n; i++) {       // Runs n times
    for (int j = 0; j < m; j++) {   // Runs m times
        // O(1) operations
    }
}
// Total Time: O(n * m)
```

#### Rule 3: Dependent Nested Loops
```java
for (int i = 0; i < n; i++) {
    for (int j = i; j < n; j++) {
        // O(1) work
    }
}
```
*Derivation:* The inner loop executes $(n - i)$ times. Total iterations:
$$\sum_{i=0}^{n-1} (n - i) = n + (n-1) + (n-2) + \dots + 1 = \frac{n(n+1)}{2} = \frac{1}{2}n^2 + \frac{1}{2}n \implies \Theta(n^2)$$

#### Rule 4: Halving and Doubling Patterns (Logarithms)
```java
int count = 0;
for (int i = 1; i < n; i *= 2) {
    count++;
}
```
*Derivation:* The variable $i$ takes values $1, 2, 4, 8, \dots, 2^k$. The loop terminates when $2^k \ge n \implies k = \lceil\log_2 n\rceil$. Total time: $\Theta(\log n)$.

### 2.6 Amortized Analysis (Dynamic Array Doubling)
When appending $n$ elements to a dynamic array (`ArrayList`):
- Most insertions take $O(1)$ time (copying into unused contiguous capacity).
- When the internal buffer is full (at powers of 2: $1, 2, 4, 8, \dots$), an array of double size is allocated and all $k$ elements are copied: cost is $k$.
- Total copy cost for $n$ insertions:
$$\sum_{j=0}^{\lfloor\log_2 n\rfloor} 2^j = 2^{\lfloor\log_2 n\rfloor + 1} - 1 < 2n$$
- Total cost for $n$ operations: $n \text{ (direct inserts)} + 2n \text{ (copies)} = 3n \text{ operations}$.
- Amortized cost per operation: $\frac{3n}{n} = 3 \in O(1)$.

---

## Section 3 — Arrays and Strings

### 3.1 Contiguous Storage and Physical Memory Layout
An array is a collection of elements of identical type stored in **contiguous physical memory addresses**.

```
Memory Address:  0x1000   0x1004   0x1008   0x100C   0x1010
Array Index:       [0]      [1]      [2]      [3]      [4]
Value:             42       17       89       -5       63
```

#### The Random Access Formula
To access index $i$:
$$\text{Address}(A[i]) = \text{BaseAddress} + (i \times \text{ElementSizeBytes})$$
Because address calculation requires only a single multiplication and addition, array indexing executes in **guaranteed $O(1)$ time**.

#### Cache Locality
Because modern CPUs load memory in **cache lines** (typically 64 bytes), accessing $A[0]$ automatically pre-fetches $A[1], A[2], \dots$ into L1/L2 hardware cache. Contiguous arrays exhibit superior **spatial locality** compared to pointer-based structures like linked lists.

### 3.2 Operation Cost Matrix

| Operation | Cost | Mechanical Explanation |
| :--- | :--- | :--- |
| **Lookup by Index** | $O(1)$ | Direct pointer arithmetic via base offset. |
| **Search (Unsorted)** | $O(n)$ | Must inspect every element in worst case. |
| **Search (Sorted)** | $O(\log n)$ | Binary search leverages monotonic ordering. |
| **Insertion (End)** | $O(1)$ amortized | Appending into pre-allocated buffer. |
| **Insertion (Beginning/Middle)** | $O(n)$ | Must shift all subsequent elements rightward by 1 index. |
| **Deletion (Beginning/Middle)** | $O(n)$ | Must shift all subsequent elements leftward by 1 index. |

### 3.3 Strings in Java: Immutability and Memory Mechanics
In Java, `String` instances are **immutable**.
- Once constructed, the underlying `byte[]` cannot be altered.
- Concatenating strings in a loop via `s += str` creates a new `String` object on every iteration, copying all previous characters:
```java
// PATHOLOGICAL ANTI-PATTERN: O(n^2) Time!
String s = "";
for (int i = 0; i < n; i++) {
    s += "a"; // Copies i characters on every step: 1 + 2 + ... + n = O(n^2)
}
```
- **The Solution (`StringBuilder`):** Backed by a mutable, expandable character buffer. Appending $n$ characters runs in **$O(n)$ total time** ($O(1)$ amortized per append).

### 3.4 In-Place Array Reversal
```java
public static void reverseArray(int[] arr) {
    if (arr == null || arr.length <= 1) return;
    int left = 0, right = arr.length - 1;
    while (left < right) {
        int temp = arr[left];
        arr[left] = arr[right];
        arr[right] = temp;
        left++;
        right--;
    }
}
```
- **Invariants:** At any step, elements $A[0 \dots \text{left}-1]$ and $A[\text{right}+1 \dots n-1]$ are fully reversed and in their final positions.
- **Complexity:** Time: $O(n)$, Auxiliary Space: $O(1)$.

---

## Section 4 — Hashing and Hash-Based Collections

### 4.1 Hash Tables: Conceptual Architecture
A **hash table** maps keys to values by computing an integer index from the key:
1. Pass key $k$ through a **hash function** $h(k)$ to produce an integer hash code.
2. Map the hash code into the table bucket range: $\text{index} = (h(k) \mathbin{\&} 0x7FFFFFFF) \pmod{\text{capacity}}$.
3. Store the key-value pair in that bucket.

```
Key ("apple") ---> Hash Code (93029210) ---> Index = 93029210 % 8 = 2
Table:
[0] -> null
[1] -> null
[2] -> ["apple" : 42] -> null
[3] -> null
...
```

### 4.2 Collisions and Resolution Strategies
When two distinct keys produce the same bucket index ($h(k_1) \equiv h(k_2) \pmod M$), a **collision** occurs.
1. **Separate Chaining:** Each bucket contains a linked list (or balanced red-black tree, as in Java 8+ OpenJDK `HashMap` when chain length $\ge 8$) of colliding entries.
2. **Open Addressing:** All entries reside directly in the table array. On collision, probe alternate slots:
   - *Linear Probing:* Check $i+1, i+2, \dots$ (suffers from primary clustering).
   - *Quadratic Probing:* Check $i+1^2, i+2^2, \dots$
   - *Double Hashing:* Step size determined by a second hash function $h_2(k)$.

### 4.3 The Java `equals()` and `hashCode()` Contract
Violating this contract silently breaks `HashMap` and `HashSet`:
1. If `o1.equals(o2) == true`, then `o1.hashCode() == o2.hashCode()` **MUST** be true.
2. If `o1.hashCode() == o2.hashCode()`, `o1.equals(o2)` is **NOT** required to be true (a collision).
3. If an object is mutated after being inserted as a key, its hash code changes, making it permanently unfindable in the map.

### 4.4 Canonical Pattern: Complement Lookup (Two Sum)
```java
public static int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> valueToIndex = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (valueToIndex.containsKey(complement)) {
            return new int[]{valueToIndex.get(complement), i};
        }
        valueToIndex.put(nums[i], i);
    }
    return new int[]{-1, -1};
}
```
- **Why this works:** Rather than checking all $O(n^2)$ pairs, we trade $O(n)$ space for $O(1)$ lookups.
- **Time Complexity:** $O(n)$ expected average time.
- **Auxiliary Space:** $O(n)$.

---

## Section 5 — Two Pointers

### 5.1 Mechanical Classification
1. **Opposite-Direction Pointers:** One pointer starts at index 0 (`left`), the other at $n-1$ (`right`). They converge toward each other. Used for sorted two-sum, palindrome verification, and container with most water.
2. **Same-Direction Pointers (Read/Write):** Both start at index 0. A `read` pointer scans forward, and a `write` pointer selectively records valid elements in-place. Used for deduplication and partition.
3. **Slow and Fast Pointers:** Pointers moving at different velocities (1 step vs 2 steps). Used for cycle detection and midpoint discovery.

### 5.2 Worked Example 1: Opposite-Direction Two Sum (Sorted Array)
**Problem:** Given sorted array `numbers`, find 1-based indices summing to `target`.
- **Monotonicity Property:** Because array is sorted, incrementing `left` strictly increases the sum; decrementing `right` strictly decreases the sum.

#### Execution State Trace: `numbers = [2, 7, 11, 15]`, `target = 9`

| Step | `left` (val) | `right` (val) | Current Sum | Comparison vs Target | Action Taken |
| :---: | :---: | :---: | :---: | :---: | :---: |
| Init | 0 (2) | 3 (15) | $2 + 15 = 17$ | $17 > 9$ | `right--` |
| 1 | 0 (2) | 2 (11) | $2 + 11 = 13$ | $13 > 9$ | `right--` |
| 2 | 0 (2) | 1 (7) | $2 + 7 = 9$ | $9 == 9$ | **Target Found! Return [1, 2]** |

### 5.3 Worked Example 2: In-Place Duplicate Removal (Read/Write)
```java
public static int removeDuplicates(int[] nums) {
    if (nums == null || nums.length == 0) return 0;
    int write = 0;
    for (int read = 1; read < nums.length; read++) {
        if (nums[read] != nums[write]) {
            write++;
            nums[write] = nums[read];
        }
    }
    return write + 1;
}
```
- **Loop Invariant:** At the start of iteration `read`, the prefix `nums[0 .. write]` contains all unique elements seen in `nums[0 .. read-1]`, in sorted order.
- **Complexity:** Time: $O(n)$, Auxiliary Space: $O(1)$.

---

## Section 6 — Sliding Window

### 6.1 Fixed vs. Variable Windows
- **Fixed-Size Window ($k$):** The window maintains a constant length $k$. Advance both boundaries simultaneously: `left++`, `right++`.
- **Variable-Size Window:** The `right` boundary expands to ingest elements until an invariant is violated (or a condition is met); the `left` boundary then contracts to restore the invariant.

### 6.2 The Monotonicity Requirement and the Negative Number Trap
> **CRITICAL PITFALL:** Sliding window algorithms that contract `left` based on running sums rely on **monotonicity**: adding an element must never decrease the sum, and removing an element must never increase the sum.
> 
> If an array contains **negative numbers**, adding an element can reduce the sum, destroying monotonicity. A contracting window cannot determine whether advancing `left` will increase or decrease the sum. For negative numbers, sliding window is invalid; use **Prefix Sums + Hash Map** instead!

### 6.3 Worked Example: Longest Substring Without Repeating Characters
```java
public static int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int maxLen = 0, left = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastSeen.containsKey(c)) {
            // Jump left forward, but never backward!
            left = Math.max(left, lastSeen.get(c) + 1);
        }
        lastSeen.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

#### State Trace for `s = "abba"`

| `right` | char | `lastSeen` before step | `left` update formula | `left` value | Window `s[left..right]` | `maxLen` |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| 0 | `'a'` | `{}` | `left = 0` | 0 | `"a"` | 1 |
| 1 | `'b'` | `{'a':0}` | `left = 0` | 0 | `"ab"` | 2 |
| 2 | `'b'` | `{'a':0, 'b':1}` | `max(0, 1 + 1) = 2` | 2 | `"b"` | 2 |
| 3 | `'a'` | `{'a':0, 'b':2}` | `max(2, 0 + 1) = 2` | 2 | `"ba"` | 2 |

*Note how `Math.max(left, ...)` prevented `left` from incorrectly moving backward to index 1 when `'a'` was re-encountered!*

---

## Section 7 — Prefix Sums and Range Reasoning

### 7.1 Mathematical Foundation
Let $A$ be an array of length $n$. The prefix sum array $P$ of length $n+1$ is defined as:
$$P[0] = 0, \quad P[k] = \sum_{i=0}^{k-1} A[i] \quad \text{for } 1 \le k \le n$$
To compute the sum of elements from index $L$ to $R$ inclusive:
$$\text{Sum}(A[L \dots R]) = P[R+1] - P[L]$$

```
Array A:       [  3,   1,   4,   1,   5  ]
Index:            0    1    2    3    4
Prefix P:   [ 0,  3,   4,   8,   9,  14  ]
Index:        0   1    2    3    4    5

Sum(A[1..3]) = P[4] - P[1] = 9 - 3 = 6 (1 + 4 + 1 = 6). Done in O(1)!
```

### 7.2 Prefix Sums with Hashing: Subarray Sum Equals $K$
**Problem:** Count contiguous subarrays summing to $k$, where elements can be negative.
- **Mathematical Transformation:**
  $$\text{Sum}(A[i \dots j]) = P[j+1] - P[i] = k \iff P[i] = P[j+1] - k$$
- **Algorithm:** Maintain a hash map of prefix sum frequencies. For each current running sum $P[j+1]$, query how many times $P[j+1] - k$ has appeared previously.
- **Time Complexity:** $O(n)$, **Auxiliary Space:** $O(n)$.

---

## Section 8 — Binary Search

### 8.1 The Monotonic Predicate Invariant
Binary search does not strictly require a sorted array; it requires a **monotonic boolean predicate** $f(x)$ over an ordered domain:
$$f: X \to \{\text{false}, \text{true}\} \quad \text{such that if } f(a) = \text{true} \text{ and } b > a, \text{ then } f(b) = \text{true}$$

```
Search Domain:  [ x0,   x1,   x2,   x3,   x4,   x5,   x6 ]
Predicate f(x): false false false true  true  true  true
                                    ^
                               First true (Lower Bound)
```

### 8.2 Boundary Conventions: Closed vs. Half-Open Ranges
1. **Closed Range `[low, high]`:** Loop condition `while (low <= high)`. Requires updating `high = mid - 1` and `low = mid + 1`.
2. **Half-Open Range `[low, high)`:** Loop condition `while (low < high)`. Termination guarantees `low == high`.

### 8.3 Integer Overflow Protection
Computing midpoint via `(low + high) / 2` will overflow a signed 32-bit integer when $\text{low} + \text{high} > 2^{31} - 1$.
- **Safe Alternatives:**
  - `mid = low + (high - low) / 2`
  - `mid = (low + high) >>> 1` (unsigned bit-shift in Java).

### 8.4 Overflow-Safe Lower Bound Implementation
```java
public static int lowerBound(int[] arr, int target) {
    int low = 0, high = arr.length; // Range [0, arr.length)
    while (low < high) {
        int mid = low + ((high - low) >>> 1);
        if (arr[mid] >= target) {
            high = mid; // First element >= target cannot be to the right of mid
        } else {
            low = mid + 1; // arr[mid] < target, so answer must be strictly > mid
        }
    }
    return low; // Points to first index where arr[index] >= target
}
```

---

## Section 9 — Linked Lists

### 9.1 Nodes, Pointers, and Memory Non-Contiguity
Unlike arrays, linked list nodes are allocated independently on the heap. Each node contains a value and a reference (`next`) to another node.
- **Pros:** $O(1)$ insertion and deletion given a reference to the preceding node; zero array-resizing copies.
- **Cons:** No random access ($O(n)$ traversal to index $i$); heavy pointer overhead (8 bytes per reference on 64-bit JVMs); poor cache locality.

### 9.2 In-Place Singly Linked List Reversal
```java
public static class ListNode {
    int val;
    ListNode next;
    ListNode(int val) { this.val = val; }
}

public static ListNode reverseList(ListNode head) {
    ListNode prev = null;
    ListNode curr = head;
    while (curr != null) {
        ListNode nextTemp = curr.next; // 1. Save next reference
        curr.next = prev;              // 2. Reverse pointer
        prev = curr;                   // 3. Advance prev
        curr = nextTemp;               // 4. Advance curr
    }
    return prev; // New head
}
```

```
Initial:      null <- [prev]    [curr: 1] -> [nextTemp: 2] -> [3] -> null
Step 1:               null <- [1]            [curr: 2] -> [nextTemp: 3]
Terminal:     null <- [1] <- [2] <- [3: prev]     [curr: null]
```

### 9.3 Cycle Detection (Floyd's Tortoise and Hare)
- Move `slow` by 1 step and `fast` by 2 steps.
- If a cycle exists, `fast` enters the loop and reduces the gap to `slow` by 1 node per iteration, guaranteeing a rendezvous in $O(\text{cycle length})$ steps without extra memory ($O(1)$ auxiliary space).

---

## Section 10 — Stacks and Queues

### 10.1 Operational Axioms
- **Stack:** Last-In, First-Out (**LIFO**). Push, Pop, Peek in $O(1)$.
- **Queue:** First-In, First-Out (**FIFO**). Enqueue (offer), Dequeue (poll), Peek in $O(1)$.
- **Java Collection Choice:** Never use the legacy `java.util.Stack` (synchronization overhead) or `java.util.LinkedList` (cache miss overhead). Always prefer `java.util.ArrayDeque` for both stacks and queues.

### 10.2 Monotonic Stack Architecture
A **monotonic stack** maintains its elements in strictly ascending or descending order.
- **Problem Solved:** Finding the **Next Greater Element** (or Next Smaller Element) for every position in an array in $O(n)$ time.
- **The Core Idea:** Push elements onto the stack. When an incoming element $x$ is larger than the element at the top of the stack, the incoming element is the "next greater" for the top element. Pop and record until the stack invariant is restored.

```java
public static int[] nextGreaterElement(int[] nums) {
    int n = nums.length;
    int[] result = new int[n];
    Arrays.fill(result, -1);
    Deque<Integer> stack = new ArrayDeque<>(); // Stores indices

    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && nums[stack.peek()] < nums[i]) {
            int topIndex = stack.pop();
            result[topIndex] = nums[i];
        }
        stack.push(i);
    }
    return result;
}
```
- **Complexity Proof:** Although there is a nested `while` loop, every index is pushed onto the stack exactly once and popped at most once. Total operations across the entire run: $\le 2n \implies \Theta(n)$ time. Auxiliary Space: $O(n)$.

---

## Section 11 — Trees and Binary Search Trees

### 11.1 Structural Terminology
- **Root:** The topmost node with no incoming edges.
- **Leaf:** A node with zero children.
- **Depth of Node $u$:** Number of edges on the path from root to $u$.
- **Height of Tree:** Maximum depth of any node.
- **Binary Search Tree (BST) Invariant:** For every node $u$, all values in $u$'s left subtree are strictly $< u.\text{val}$, and all values in $u$'s right subtree are strictly $> u.\text{val}$.

```
                 [ 8 ]          <-- Root (Depth 0)
                /     \
             [ 3 ]   [ 10 ]     <-- Depth 1
            /    \        \
          [ 1 ]  [ 6 ]   [ 14 ] <-- Leaves (Depth 2, Height = 2)
```

### 11.2 Tree Traversals
1. **Depth-First Search (DFS):**
   - **Preorder ($N \to L \to R$):** Process root, traverse left, traverse right. (Used for serialization).
   - **Inorder ($L \to N \to R$):** Traverse left, process root, traverse right. (**Yields sorted order in a BST!**)
   - **Postorder ($L \to R \to N$):** Traverse left, traverse right, process root. (Used for bottom-up computation, deleting trees).
2. **Breadth-First Search (BFS / Level-Order):**
   - Traverse layer by layer using a FIFO Queue.

### 11.3 BST Deletion Mechanics
Deleting a node $z$ has three distinct cases:
1. **$z$ is a leaf:** Simply remove it (set parent reference to null).
2. **$z$ has one child:** Splice $z$ out by pointing $z$'s parent directly to $z$'s child.
3. **$z$ has two children:** Find $z$'s in-order successor $y$ (the minimum node in $z$'s right subtree). Copy $y$'s value into $z$, then recursively delete $y$ from the right subtree.

---

## Section 12 — Heaps and Priority Queues

### 12.1 The Binary Heap Property
A binary heap is a complete binary tree satisfying the **heap order property**:
- **Min-Heap:** For every node $i$ other than root, $\text{val}(\text{parent}(i)) \le \text{val}(i)$. The minimum element resides at the root.
- **Max-Heap:** $\text{val}(\text{parent}(i)) \ge \text{val}(i)$. The maximum element resides at the root.

### 12.2 Compact Array Representation
Because a binary heap is a complete binary tree (filled at all levels except possibly the last, which is filled from left to right), it requires zero pointers. It is mapped contiguously into an array:
- Left child of index $i$: $2i + 1$
- Right child of index $i$: $2i + 2$
- Parent of index $i$: $\lfloor(i - 1) / 2\rfloor$

```
Array:   [ 1,   3,   6,   5,   9,   8 ]
Index:     0    1    2    3    4    5

Tree Structure:
                [ 1 ] (idx 0)
               /     \
         (idx 1) [ 3 ]   [ 6 ] (idx 2)
                /   \     /
       (idx 3)[ 5 ] [9] [ 8 ](idx 5)
```

### 12.3 Operations and Mathematical Nuances
- **Insert (`offer`):** Place element at end of array; sift up (bubble up) along parent path. Cost: $O(\log n)$.
- **Extract Min (`poll`):** Replace root with last element; sift down along smaller child path. Cost: $O(\log n)$.
- **Heapify Construction:** Building a heap from an unordered array of $n$ elements takes **$\Theta(n)$ time**, not $O(n \log n)$!
  - *Proof Intuition:* Most nodes reside near the bottom (leaves) where the sift-down distance is 0 or 1. Summing height $\times$ nodes at height yields $\sum_{h=0}^{\log n} \frac{n}{2^h} \cdot h = O(n)$.

---

## Section 13 — Graph Fundamentals and Traversal

### 13.1 Graph Representations: Adjacency List vs. Adjacency Matrix

| Metric / Operation | Adjacency List (`List<List<Integer>>`) | Adjacency Matrix (`boolean[][]`) |
| :--- | :--- | :--- |
| **Space Complexity** | $O(V + E)$ | $O(V^2)$ |
| **Check if $(u, v)$ is an edge** | $O(\text{deg}(u))$ | $O(1)$ |
| **Iterate neighbors of $u$** | $O(\text{deg}(u))$ (**Optimal for sparse graphs**) | $O(V)$ |
| **Memory Locality** | Pointer overhead per list | Contiguous 2D array |

### 13.2 Breadth-First Search (BFS) vs. Depth-First Search (DFS)
- **BFS (Queue):** Explores vertices in increasing order of path distance from source. Guarantees finding the **shortest path in unweighted graphs**.
- **DFS (Stack / Recursion):** Explores deeply along branches before backtracking. Ideal for topological sorting, cycle detection, and connected components.
- **Visited State Invariant:** Graphs can contain cycles. A `boolean[] visited` or `Set<Integer> visited` is strictly mandatory to prevent infinite loops.

---

## Section 14 — Essential Graph Algorithms

### 14.1 Topological Sorting (Kahn's In-Degree Algorithm)
Applies strictly to **Directed Acyclic Graphs (DAGs)**. Produces a linear ordering of vertices such that for every directed edge $(u, v)$, $u$ comes before $v$.
1. Compute in-degree for all vertices.
2. Enqueue all vertices with in-degree 0 into a Queue.
3. While queue is not empty: dequeue $u$, append to order, decrement in-degree of all neighbors $v$. If $v$'s in-degree reaches 0, enqueue $v$.
4. **Cycle Detection:** If the output contains fewer than $|V|$ vertices, the graph contains a directed cycle!

### 14.2 Dijkstra's Algorithm (Single-Source Shortest Paths)
Computes shortest path from source to all vertices in graphs with **non-negative edge weights**.
- **The Core Idea:** Greedy exploration using a min-heap (PriorityQueue) storing `(distance, vertex)`.
- **Why Negative Weights Break Dijkstra:** Dijkstra assumes that once a vertex is popped from the priority queue, its shortest distance is finalized. A negative-weight edge discovered later could retroactively decrease that distance, which Dijkstra will never revisit.
- **Time Complexity:** $O((V + E) \log V)$ with binary heap.

### 14.3 Disjoint Set Union (DSU / Union-Find)
Manages a partition of a set into disjoint subsets:
- **Path Compression:** During `find(x)`, point $x$ directly to the root of its set: $O(\alpha(n))$ amortized.
- **Union by Rank / Size:** Attach the shallower tree to the deeper tree to prevent degeneration into a linked list.
- **Combined Complexity:** Nearly $O(1)$ amortized ($\alpha(n) \le 4$ for all practical universe sizes).

---

## Section 15 — Recursion and Backtracking

### 15.1 The Call Stack and Recursion Trees
Every recursive invocation allocates a new **stack frame** containing local variables, parameters, and return addresses.
- **Base Case:** Halts recursion and returns a value without making further calls. Missing base cases cause `StackOverflowError`.
- **Recursive Step:** Decomposes problem into smaller self-similar subproblems.

### 15.2 The Backtracking Template: Choose, Explore, Unchoose
Backtracking systematically explores the search space tree:
```java
private void backtrack(State state, List<Result> solutions) {
    if (state.isComplete()) {
        solutions.add(state.snapshot());
        return;
    }
    for (Choice choice : state.getAvailableChoices()) {
        state.apply(choice);       // 1. CHOOSE
        backtrack(state, solutions); // 2. EXPLORE
        state.undo(choice);        // 3. UNCHOOSE (Restores Invariant!)
    }
}
```

```
                        Decision Root []
                       /                \
             Include 1 [1]              Exclude 1 []
             /          \               /          \
      Include 2 [1,2]  Excl 2 [1]   Incl 2 [2]    Excl 2 []
```

---

## Section 16 — Greedy Algorithms

### 16.1 The Greedy Choice Property
A greedy algorithm makes the locally optimal choice at each stage with the hope of finding a global optimum.
- **When Valid:** A problem exhibits:
  1. **Greedy-Choice Property:** A global optimum can be arrived at by making locally optimal choices.
  2. **Optimal Substructure:** An optimal solution to the problem contains optimal solutions to subproblems.
- **Proof Method (Exchange Argument):** Assume an optimal solution exists that differs from the greedy choice. Prove that swapping the optimal choice with the greedy choice does not worsen the solution.

### 16.2 The Counterexample Trap
> **WARNING:** Locally optimal choices are frequently globally disastrous.
> 
> *Classic Counterexample (Coin Change):* Coin denominations `[1, 3, 4]`, target `6`.
> - Greedy choice (largest first): selects `4`, remaining `2` -> selects two `1`s. Total coins: `3` (`4 + 1 + 1`).
> - Optimal solution: `3 + 3 = 6`. Total coins: `2`!
> - Greedy fails because choices cannot be undone; **Dynamic Programming** is required.

---

## Section 17 — Dynamic Programming

### 17.1 Two Hallmarks of DP
1. **Overlapping Subproblems:** The problem breaks down into subproblems that are solved repeatedly.
2. **Optimal Substructure:** The optimal solution to the overall problem can be constructed from optimal solutions to its subproblems.

### 17.2 Top-Down (Memoization) vs. Bottom-Up (Tabulation)
- **Top-Down:** Write natural recursive formulation; cache results in a memo array/map. Traverses only reachable states.
- **Bottom-Up:** Iteratively fill a table in topological order of dependencies. Eliminates recursion stack overhead and enables space optimization.

### 17.3 Worked Example: 0/1 Knapsack Problem
**Problem:** Given weights $w_i$, values $v_i$, and capacity $W$, maximize total value without exceeding $W$. Each item can be used at most once.

#### State Definition
Let $dp[i][w]$ be the maximum value obtainable using a subset of the first $i$ items with weight capacity $w$.

#### Transition Equation
$$dp[i][w] = \begin{cases} 
dp[i-1][w] & \text{if } w < w_i \text{ (item too heavy)} \\
\max(dp[i-1][w], \, dp[i-1][w - w_i] + v_i) & \text{if } w \ge w_i 
\end{cases}$$

#### Space Optimization: Rolling 1D Array
Because $dp[i][w]$ depends only on row $i-1$, we can collapse the 2D table into a 1D array of size $W+1$ by iterating backwards from $W$ down to $w_i$:
```java
public static int knapsack(int[] values, int[] weights, int capacity) {
    int[] dp = new int[capacity + 1];
    for (int i = 0; i < values.length; i++) {
        int val = values[i], wt = weights[i];
        for (int w = capacity; w >= wt; w--) {
            dp[w] = Math.max(dp[w], dp[w - wt] + val);
        }
    }
    return dp[capacity];
}
```
*Why backwards?* If we iterated forward, $dp[w - wt]$ would have already been updated with item $i$ in the current iteration, inadvertently allowing the same item to be selected multiple times (which would solve Unbounded Knapsack, not 0/1 Knapsack!).

---

## Section 18 — Sorting and Searching Trade-Offs

### 18.1 Comprehensive Sorting Comparison Matrix

| Algorithm | Best Time | Average Time | Worst Time | Auxiliary Space | Stable? | Key Mechanism & Trade-Off |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Insertion Sort** | $O(n)$ | $O(n^2)$ | $O(n^2)$ | $O(1)$ | Yes | Excellent on nearly-sorted data or small $n$ ($n \le 32$). |
| **Merge Sort** | $O(n \log n)$ | $O(n \log n)$ | $O(n \log n)$ | $O(n)$ | Yes | Deterministic; requires $O(n)$ auxiliary buffer. |
| **QuickSort** | $O(n \log n)$ | $O(n \log n)$ | $O(n^2)$ | $O(\log n)$ | No | Cache-friendly in-place partitioning; worst-case if pivot is poor. |
| **HeapSort** | $O(n \log n)$ | $O(n \log n)$ | $O(n \log n)$ | $O(1)$ | No | Guaranteed in-place $O(n \log n)$; poor cache locality. |

### 18.2 Java Runtime Sorting Implementations
- **Primitive Types (`Arrays.sort(int[])`):** Uses **Dual-Pivot Quicksort** by Vladimir Yaroslavskiy. Does not require stability; prioritizes CPU cache throughput and $O(1)$ memory.
- **Object Types (`Arrays.sort(T[])`, `Collections.sort()`):** Uses **Timsort** (hybrid of MergeSort and InsertionSort). **Must be stable** so that secondary sorting keys preserve primary order; worst-case $O(n \log n)$, $O(n)$ space.

### 18.3 The $\Omega(n \log n)$ Comparison Lower Bound
Any sorting algorithm that determines order strictly through pairwise comparisons can be represented as a **decision tree**:
- There are $n!$ possible permutations of $n$ elements.
- The decision tree must have at least $n!$ leaves to identify the correct sorted order.
- A binary tree of height $h$ has at most $2^h$ leaves:
$$2^h \ge n! \implies h \ge \log_2(n!) = \sum_{k=1}^n \log_2 k \ge \frac{n}{2} \log_2\left(\frac{n}{2}\right) \implies h \in \Omega(n \log n)$$
Thus, no comparison-based sort can beat $O(n \log n)$ in the worst case!

---

## Section 19 — Pattern Recognition and Problem-Solving Method

### 19.1 Diagnostic Signals vs. Necessary Evidence

| Problem Signal / Clue | Likely Algorithmic Pattern | Necessary Verification Before Committing |
| :--- | :--- | :--- |
| "Find pair with sum $S$" | Two Pointers (if sorted) OR Hashing | Can the array be sorted without violating output index requirements? |
| "Contiguous subarray with condition" | Sliding Window OR Prefix Sums | Does the condition satisfy monotonicity? If negative numbers exist, sliding window is invalid. |
| "Shortest path in unweighted grid" | Breadth-First Search (BFS) | Are all step weights uniform and non-negative? |
| "Top $K$ frequent or extreme elements" | Min-Heap of size $K$ OR QuickSelect | Is stream online (Heap) or static array (QuickSelect $O(n)$ avg)? |
| "Generate all valid combinations" | Backtracking | What are the branching factor and constraints? Is $n \le 20$? |
| "Maximize/minimize with choices" | Dynamic Programming OR Greedy | Does greedy exchange argument hold? If not, do subproblems overlap? |

### 19.2 The 10-Step Repeatable Interview Workflow
1. **Restate the Problem:** Rephrase in your own words; verify input/output types.
2. **Clarify Constraints:** Ask about $n$, value ranges, duplicates, negatives, memory limits.
3. **Establish Brute Force:** State the naive solution explicitly; state its exact time/space complexity.
4. **Identify Redundant Work:** Highlight where brute force recalculates known data.
5. **Select Pattern & Structure:** Propose optimal structure; state why its invariants eliminate the bottleneck.
6. **State the Invariant:** Formulate loop or recursive invariants.
7. **Trace Small Example:** Step through variables on a 3-4 element sample.
8. **Write Clean Code:** Implement cleanly with defensive boundary checks.
9. **Dry-Run Boundary Cases:** Test empty array, single element, duplicates, extreme numbers.
10. **State Complexity:** Provide tight $\Theta$ bounds for time and auxiliary space.

---

## Section 20 — Interview Practice and Self-Assessment

### Question 1: Invariant & Correctness
**Q:** In binary search, why is the midpoint calculation `mid = (low + high) >>> 1` preferable to `(low + high) / 2`, and what loop invariant guarantees termination in `while (low < high)`?  
**Model Answer:**  
In Java, signed 32-bit integers overflow into negative values when their sum exceeds $2^{31} - 1 = 2,147,483,647$. Using `(low + high) / 2` with large bounds results in a negative index, triggering `ArrayIndexOutOfBoundsException`. The unsigned right-shift operator `>>>` treats the 32-bit sum as unsigned, safely evaluating values up to $2^{32} - 1$.  
The loop invariant for `while (low < high)` with `mid = low + ((high - low) >>> 1)` guarantees that $\text{low} \le \text{mid} < \text{high}$. When updating `low = mid + 1`, `low` strictly increases; when updating `high = mid`, `high` strictly decreases. Because the search interval $[\text{low}, \text{high})$ strictly shrinks on every iteration, termination is mathematically guaranteed in at most $\lceil\log_2 n\rceil + 1$ iterations.

### Question 2: Mechanistic Debugging
**Q:** A candidate writes the following code to find the longest subarray with sum $\le K$. When tested with `nums = [3, -2, 5]`, it fails. Explain why.
```java
int left = 0, sum = 0, maxLen = 0;
for (int right = 0; right < nums.length; right++) {
    sum += nums[right];
    while (sum > K) {
        sum -= nums[left++];
    }
    maxLen = Math.max(maxLen, right - left + 1);
}
```
**Model Answer:**  
The code incorrectly assumes sliding window monotonicity. In an array with negative numbers, contracting the window by incrementing `left` does not guarantee decreasing the sum; if `nums[left]` is negative, `sum -= nums[left]` will actually **increase** `sum`. Furthermore, a valid subarray might be formed later because subsequent negative numbers could bring the sum back below $K$. For general arrays with negative numbers, this problem requires prefix sums combined with a monotonic deque or coordinate compression and Fenwick trees, achieving $O(n \log n)$ time.

### Question 3: Trade-Off Analysis
**Q:** Compare `ArrayList` versus `LinkedList` in Java for implementing a queue.  
**Model Answer:**  
Neither should be used; `ArrayDeque` is optimal. However, comparing `ArrayList` vs `LinkedList`:
- `ArrayList` provides $O(1)$ random access and cache locality, but deleting from the head (dequeue) requires shifting all elements, taking $O(n)$ time.
- `LinkedList` provides $O(1)$ insertion at tail and deletion at head, but each node allocates a 24-byte wrapper object containing two 8-byte pointers on a 64-bit JVM. This incurs substantial memory overhead, triggers frequent garbage collections, and causes hardware CPU cache misses due to scattered heap memory.
- `ArrayDeque` uses a circular array buffer, providing $O(1)$ amortized head and tail operations with contiguous memory layout and zero node-allocation overhead.

---

## Revision and Verification Checklist
- [x] All 20 required curriculum sections present and fully articulated.
- [x] Mathematical derivations for Big O, $\Omega$, and $\Theta$ included.
- [x] Explicit warning against sliding window with negative numbers included.
- [x] Contiguous memory layout and cache line implications explained.
- [x] Java-specific contracts (`equals`/`hashCode`, `String` immutability, `ArrayDeque` vs `Stack`) documented.
- [x] Zero placeholder tokens or superficial summaries.
