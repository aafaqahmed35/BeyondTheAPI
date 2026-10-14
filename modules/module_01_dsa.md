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
- [Section 21 — DSA Cheatsheet: Interview Revision Reference](#section-21--dsa-cheatsheet-interview-revision-reference)

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

The table below outlines common complexity classes along with practical engineering heuristics for interview problem-solving.

> **Engineering Rule of Thumb:** In automated grading environments and coding interviews, systems typically allow approximately $10^7$ to $10^8$ basic CPU operations per second. The operation counts and feasibility thresholds below represent **rough practical heuristics**, not universal physical constants or rigid hardware limits.

| Class | Name | Intuition & Typical Trigger | Practical Heuristic ($n = 10^6$) | Typical Feasible $n$ Limit |
| :--- | :--- | :--- | :--- | :--- |
| $O(1)$ | Constant | Direct array index access, hash table lookup, basic math | Instantaneous ($\sim 1$ op) | Any practical size |
| $O(\log n)$ | Logarithmic | Dividing search space in half (Binary Search, balanced tree ops) | Extremely fast ($\approx 20$ ops) | $n \le 10^{18}$ |
| $O(n)$ | Linear | Single pass through an array or linked list, counting | Feasible within typical time limits ($\sim 10^6$ ops) | $n \le 10^7$ |
| $O(n \log n)$ | Linearithmic | Optimal comparison sorts (MergeSort, HeapSort), divide-and-conquer | Feasible ($\sim 2 \times 10^7$ ops) | $n \le 10^6$ |
| $O(n^2)$ | Quadratic | Nested loops over input, comparing all pairs | Tends to exceed typical 1-second limits ($\sim 10^{12}$ ops) | $n \le 5 \times 10^3$ |
| $O(2^n)$ | Exponential | Generating all subsets, brute-force recursion | Prohibitive for general execution | $n \le 20 \dots 25$ |
| $O(n!)$ | Factorial | Generating all permutations | Prohibitive for general execution | $n \le 10 \dots 12$ |

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

### 2.6 Amortized Analysis (Dynamic Array Expansion)

#### The Canonical Theoretical Doubling Model
Dynamic array expansion is commonly analyzed using a theoretical **doubling model** (growth factor of 2):
- Most insertions take $O(1)$ time (copying into unused pre-allocated capacity).
- When the internal buffer is full (at sizes $1, 2, 4, 8, \dots, 2^k$), an array of double size is allocated and all $k$ existing elements are copied: cost is $k$.
- Total copy cost across $n$ insertions:
$$\sum_{j=0}^{\lfloor\log_2 n\rfloor} 2^j = 2^{\lfloor\log_2 n\rfloor + 1} - 1 < 2n$$
- Total cost for $n$ operations: $n \text{ (direct inserts)} + 2n \text{ (copies)} < 3n \text{ operations}$.
- Amortized cost per operation: $\frac{3n}{n} = 3 \in O(1)$.

#### Practical Runtime Distinction: OpenJDK `ArrayList`
While textbook analysis assumes a doubling factor of 2, production runtimes make deliberate engineering trade-offs:
- In OpenJDK `ArrayList`, the default growth factor is **$1.5\times$** (calculated as `newCapacity = oldCapacity + (oldCapacity >> 1)`).
- **Engineering Rationale:** A growth factor of $1.5$ (rather than 2.0) reduces memory overhead and allows memory allocators to potentially reuse previously released contiguous memory chunks in subsequent reallocations, mitigating heap fragmentation while still mathematically preserving $O(1)$ amortized append time.

---

## Section 3 — Arrays and Strings

### 3.1 Contiguous Storage and Physical Memory Layout
An array is a collection of elements of identical type stored in **contiguous memory addresses**.

```
Relative Memory Offset:  +0 bytes   +4 bytes   +8 bytes   +12 bytes  +16 bytes
Array Index:               [0]        [1]        [2]        [3]        [4]
Value:                     42         17         89         -5         63
```

#### The Random Access Formula and Managed Runtime Reality
In the theoretical Random Access Machine (RAM) model, any index $i$ is addressed via:
$$\text{Address}(A[i]) = \text{BaseAddress} + (i \times \text{ElementSizeBytes})$$
Because address calculation requires only a single multiplication and addition, array indexing executes in **guaranteed $O(1)$ time**.

In a managed runtime like Java:
- Programmers do not manipulate raw physical memory pointers; the JVM abstracts direct hardware memory.
- Java objects and arrays reside in the managed JVM heap, where the Garbage Collector (GC) may relocate memory during compaction.
- The $O(1)$ random access guarantee remains strictly preserved because the JVM calculates the memory offset relative to the array object's internal base reference before performing element access.

#### Cache Locality
Because modern CPU hardware transfers memory from RAM into CPU caches in **cache lines** (typically 64 bytes), accessing $A[0]$ automatically brings adjacent array elements $A[1], A[2], \dots$ into L1/L2 hardware cache. Contiguous arrays exhibit superior **spatial locality** compared to pointer-linked structures.

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

### 4.1 Hash Tables: Conceptual Architecture vs. OpenJDK `HashMap`

#### The Conceptual Model
A theoretical hash table maps keys to values by converting a key $k$ into an integer bucket index:
1. Pass key $k$ through a **hash function** $h(k)$ to produce an integer hash code.
2. Map the hash code into the table capacity range via modulo:
   $$\text{index} = (h(k) \mathbin{\&} 0x7FFFFFFF) \pmod{\text{capacity}}$$
3. Store the key-value entry in the corresponding bucket.

```
Key ("apple") ---> Hash Code (93029210) ---> Index = 93029210 % 8 = 2
Table:
[0] -> null
[1] -> null
[2] -> ["apple" : 42] -> null
[3] -> null
...
```

#### OpenJDK `HashMap` Implementation Mechanics
While the conceptual model uses modulo arithmetic, OpenJDK's `HashMap` introduces specific performance optimizations:
1. **Power-of-Two Capacity:** The bucket array length $n$ is always strictly a power of two ($n = 2^k$, default initial capacity = 16).
2. **Bitwise Masking Index Calculation:** Instead of expensive integer division/modulo (`%`), it calculates bucket index using bitwise AND:
   $$\text{index} = (n - 1) \mathbin{\&} \text{hash}$$
   Because $n$ is a power of two, $n - 1$ is a binary bitmask of all ones ($16 - 1 = 15 = 0b00001111$), making index calculation a single CPU cycle bitwise operation.
3. **Bit Perturbation (Defensive Hash Spreading):** If a user's `hashCode()` only varies in higher bits, masking with $n-1$ would cause massive collisions on low bits. OpenJDK applies a supplemental hash function:
   $$\text{hash} = (h = \text{key.hashCode}()) \oplus (h \mathbin{>\!\!>\!\!>} 16)$$
   This XORs the high 16 bits with the low 16 bits, ensuring entropy from the upper bits influences bucket selection.

### 4.2 Collisions and Resolution Strategies

When two distinct keys produce the same bucket index ($h(k_1) \equiv h(k_2) \pmod n$), a **collision** occurs.
1. **Separate Chaining:** Each bucket contains a linked list of colliding entries (`Node<K,V>`).
2. **Open Addressing:** All entries reside directly in the table array. On collision, probe alternate slots (Linear Probing, Quadratic Probing, or Double Hashing).

#### OpenJDK Treeification: Implementation Detail vs. Map Specification
> **IMPORTANT DISTINCTION:** The `java.util.Map` interface specification provides **zero guarantees** about treeification. Treeification is an internal implementation optimization introduced in OpenJDK 8 to defend against Hash Collision Denial-of-Service attacks (hash flooding).

In OpenJDK's `HashMap`:
- **Treeification Trigger:** When entries are added to a bucket, if the chain length reaches **`TREEIFY_THRESHOLD = 8`**, the bucket is a candidate for treeification into a Red-Black Tree (`TreeNode<K,V>`), reducing worst-case lookup from $O(k)$ to $O(\log k)$.
- **The Capacity Precondition:** Treeification **only** occurs if the total table capacity is at least **`MIN_TREEIFY_CAPACITY = 64`**. If a bucket reaches 8 entries but table capacity is under 64, `HashMap` calls `resize()` to double the table capacity and re-distribute entries across buckets instead of constructing a tree!
- **Untreeification Threshold:** If deletions or table resizing reduce the number of entries in a treeified bucket to **`UNTREEIFY_THRESHOLD = 6`**, the tree is automatically converted back into a standard singly linked list.

### 4.3 The Java `equals()` and `hashCode()` Contract and Key Immutability

Violating this contract silently breaks hash collections:
1. **Contract Requirement 1:** If `o1.equals(o2) == true`, then `o1.hashCode() == o2.hashCode()` **MUST** be true.
2. **Contract Requirement 2:** If `o1.hashCode() == o2.hashCode()`, `o1.equals(o2)` is **NOT** required to be true (a collision).
3. **The Danger of Key Mutation:** If an object is inserted as a key into a `HashMap` and subsequently mutated such that its `hashCode()` or `equals()` evaluation changes:
   - When calling `map.get(key)` with the mutated key, the map recalculates `hash`, pointing to a different bucket where the entry cannot be found (returns `null`).
   - Even querying the original bucket may fail if `equals()` no longer matches.
   - The original entry remains trapped in the map, potentially causing silent data corruption and memory leaks. **Rule:** Map keys should always be immutable (e.g., `String`, `Integer`, or Java `record` with immutable components).

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
- **Monotonicity Property:** Because the array is sorted in non-decreasing order:
  - Incrementing `left` does not decrease the sum ($A[\text{left}+1] \ge A[\text{left}]$, so current sum increases or remains unchanged if adjacent values are duplicates).
  - Decrementing `right` does not increase the sum ($A[\text{right}-1] \le A[\text{right}]$, so current sum decreases or remains unchanged).
  - Therefore, if `currentSum < target`, decrementing `right` can never reach `target` (it would only make the sum smaller or equal), so incrementing `left` is the strictly necessary direction. Conversely, if `currentSum > target`, incrementing `left` can never reach `target`, so decrementing `right` is required.

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
- **Fixed-Size Window ($k$):** The window maintains a constant length $k$. Advance both boundaries simultaneously: `left++`, `right++`. (Notice that fixed-size sliding windows work completely fine even if the array contains negative numbers, because window expansion and contraction are determined strictly by index length, not by sum thresholds).
- **Variable-Size Window:** The `right` boundary expands to ingest elements until a condition is met; the `left` boundary contracts to restore or optimize a window invariant.

### 6.2 The Monotonicity Requirement and Subarray Problem Breakdown

> **CRITICAL ARCHITECTURAL DISTINCTION:** Negative numbers do **not** invalidate all sliding-window algorithms. Fixed-size windows and frequency-based windows (e.g., longest substring without duplicate characters or anagram finders) function correctly regardless of numeric sign.
>
> Rather, negative numbers specifically invalidate **variable-size, sum-based sliding windows** whose correctness relies on **monotonicity**:
> - With non-negative numbers: expanding `right` monotonically increases (or does not decrease) the window sum, and contracting `left` monotonically decreases (or does not increase) the window sum.
> - With negative numbers: expanding `right` can *decrease* the running sum, and contracting `left` can *increase* the running sum! This breaks the fundamental greedy invariant: you cannot know whether advancing `left` will move the sum closer to or further from the target constraint.

#### Choosing the Correct Subarray Strategy When Negative Numbers Are Allowed

Do not treat any single tool as a universal panacea. Select the strategy according to the exact problem structure:

| Problem Formulation | Allowed Values | Optimal Algorithmic Strategy | Asymptotic Complexity | Mechanical Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Exact Subarray Sum Equals $K$** | Negative, Zero, Positive | **Prefix Sums + Frequency Hash Map** | Time: $O(n)$<br>Aux Space: $O(n)$ | $\text{Sum}(A[i \dots j]) = P[j+1] - P[i] = K \iff P[i] = P[j+1] - K$. Count occurrences of complement $P[j+1] - K$ seen so far. |
| **Shortest Subarray with Sum at Least $K$** | Negative, Zero, Positive | **Prefix Sums + Monotonic Deque** | Time: $O(n)$<br>Aux Space: $O(n)$ | Maintaining a deque of indices with strictly increasing prefix sums $P[i]$. Front pop: when $P[j] - P[\text{first}] \ge K$, record length and pop front (since later $j'$ would only yield a longer window). Back pop: if $P[j] \le P[\text{last}]$, pop back because $j$ appears later and has a smaller prefix sum, making $\text{last}$ suboptimal for all future queries. |
| **Longest Subarray with Sum at Most $K$** | Negative, Zero, Positive | **Prefix Sums + Monotonic Prefix-Min Array** | Time: $O(n)$ or $O(n \log n)$<br>Aux Space: $O(n)$ | Maintain running prefix sums. To maximize $j - i$ such that $P[j] - P[i] \le K \iff P[i] \ge P[j] - K$, we need the smallest index $i$ with a sufficiently small $P[i]$. This is solved via two pointers over precomputed prefix-minimum arrays in $O(n)$ time, or via a sorted list of prefix sums with binary search in $O(n \log n)$ time. |

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
**Problem:** Count contiguous subarrays summing to $k$, where elements can be negative, zero, or positive.

#### Mathematical Transformation
$$\text{Sum}(A[i \dots j]) = P[j+1] - P[i] = k \iff P[i] = P[j+1] - k$$

#### The Fundamental Role of `prefixCounts.put(0L, 1)`
When implementing this with a frequency map `Map<Long, Integer> prefixCounts`, you must initialize the map with `prefixCounts.put(0L, 1)` before processing the array:
- **Why this is mandatory:** The entry `(0L, 1)` represents the prefix sum of an empty subarray (the state before examining index 0, where length is 0 and sum is 0).
- If a valid subarray starting at index 0 satisfies $\sum_{m=0}^j A[m] = k$, then its prefix sum is $P[j+1] = k$. Its required complement is $P[j+1] - k = k - k = 0$.
- Without the initial `(0L, 1)` entry, any valid subarray originating at index 0 would fail to find its complement and be silently omitted from the count!

```java
public static int countSubarraysWithSum(int[] nums, int target) {
    if (nums == null || nums.length == 0) return 0;
    Map<Long, Integer> prefixCounts = new HashMap<>();
    prefixCounts.put(0L, 1); // Crucial: represents empty prefix before index 0

    long runningSum = 0;
    int count = 0;

    for (int num : nums) {
        runningSum += num;
        long complement = runningSum - target;
        count += prefixCounts.getOrDefault(complement, 0);
        prefixCounts.put(runningSum, prefixCounts.getOrDefault(runningSum, 0) + 1);
    }
    return count;
}
```
- **Complexity:** Time: $O(n)$, Auxiliary Space: $O(n)$ to store prefix sum frequencies.

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

### 8.3 Integer Overflow Protection and Midpoint Formulas
In binary search, evaluating midpoint via `(low + high) / 2` introduces a subtle, dangerous bug when `low + high > Integer.MAX_VALUE` ($2^{31} - 1 = 2,147,483,647$):
- In signed 32-bit two's-complement arithmetic, overflow wraps around into negative numbers, yielding a negative `mid` that triggers an immediate `ArrayIndexOutOfBoundsException`.
- **Recommended Standard Across Languages:**
  $$\text{mid} = \text{low} + \frac{\text{high} - \text{low}}{2}$$
  Because $\text{high} \ge \text{low}$, the difference $\text{high} - \text{low}$ is always non-negative and smaller than $\text{high}$, completely preventing integer overflow.
- **Java-Specific Optimization (Unsigned Bit-Shift):**
  $$\text{mid} = \text{low} + ((\text{high} - \text{low}) \mathbin{>\!\!>\!\!>} 1) \quad \text{or} \quad \text{mid} = (\text{low} + \text{high}) \mathbin{>\!\!>\!\!>} 1$$
  The logical shift operator `>>>` treats the 32-bit sum as an unsigned integer, shifting the sign bit into bit position 30. For non-negative array bounds ($\text{low} \ge 0, \text{high} \ge 0$), this correctly evaluates midpoints up to $2^{32} - 1$. In this handbook, we consistently use `low + ((high - low) / 2)` or `low + ((high - low) >>> 1)`.

### 8.4 Overflow-Safe Lower Bound Implementation
```java
public static int lowerBound(int[] arr, int target) {
    if (arr == null) return -1;
    int low = 0, high = arr.length; // Range [0, arr.length)
    while (low < high) {
        int mid = low + ((high - low) >>> 1); // Overflow-safe midpoint
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

### 9.1 Nodes, References, and JVM Memory Layout Realities
Unlike arrays, linked list nodes are allocated independently on the heap. Each node contains a data element and pointer references (`next`, and `prev` in doubly linked lists) to other nodes.

#### The Reality of JVM Object Overhead and Compressed OOPs
Textbook explanations often claim that "pointers in 64-bit systems are always 8 bytes." In modern JVM runtimes (such as OpenJDK 64-bit HotSpot), memory layout is significantly more nuanced:
1. **Compressed OOPs (`-XX:+UseCompressedOops`):** On 64-bit JVMs with heap sizes under 32 GB (the standard default), the JVM compresses 64-bit object references down to **4-byte (32-bit) pointers** using an 8-byte shift addressing strategy.
2. **Object Headers:** Every Java object on the heap requires an object header consisting of:
   - *Mark Word:* 8 bytes (stores hash code, GC age bits, locking synchronization flags).
   - *Klass Word:* 4 bytes (with Compressed Class Pointers) or 8 bytes (without).
3. **8-Byte Memory Alignment (Padding):** The HotSpot JVM aligns all objects to multiples of 8 bytes.

```
Layout of a LinkedList Node (Compressed OOPs, Heap < 32GB):
+-------------------------------+-------------------+--------------------+--------------------+---------------+
| Mark Word (8 bytes)           | Klass (4 bytes)   | item ref (4 bytes) | next ref (4 bytes) | prev ref (4B) |
+-------------------------------+-------------------+--------------------+--------------------+---------------+
Total Node Size: 24 bytes (plus the actual referenced data object)
```

Without Compressed OOPs (e.g., heaps $\ge 32\text{ GB}$):
- Header: 16 bytes (8 mark + 8 klass).
- Three references: $3 \times 8 = 24$ bytes.
- Total Node Size: **40 bytes** per element!

#### Engineering Implications: Linked Lists vs. Arrays
- **Memory Overhead:** Storing an integer in an array requires exactly 4 bytes. Storing an integer in a `java.util.LinkedList` requires a 24-byte `Node` object plus a 16-to-24-byte boxed `java.lang.Integer` object—a **10x memory penalty**!
- **Spatial Locality and Cache Line Misses:** Array elements are contiguous, loading 16 integers per 64-byte hardware cache line. Linked list nodes are scattered across arbitrary heap memory addresses; traversing from `curr` to `curr.next` frequently causes a CPU L1/L2 cache miss, stalling CPU execution while waiting on RAM.
- **When Linked Lists Make Sense:** Only when inserting or deleting elements at arbitrary positions in $O(1)$ time given an already positioned reference, or when building specialized internal structures (like LRU cache eviction queues).

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

### 10.1 Operational Axioms and Java Collection Choices
- **Stack:** Last-In, First-Out (**LIFO**). Push, Pop, Peek in $O(1)$.
- **Queue:** First-In, First-Out (**FIFO**). Enqueue (offer), Dequeue (poll), Peek in $O(1)$.

#### Choosing the Right Java Implementation
- **The Recommended Default:** In single-threaded code, `java.util.ArrayDeque` is the standard default for both LIFO stacks and FIFO queues. It uses a resizable circular array buffer, avoiding the synchronization locks of legacy `java.util.Stack` (which extends `Vector`) and avoiding the node-allocation and cache-miss overhead of `java.util.LinkedList`.
- **Important Operational Boundaries & Trade-Offs:**
  - `ArrayDeque` **prohibits null elements**: calling `offer(null)` or `push(null)` throws `NullPointerException`.
  - `ArrayDeque` is **not thread-safe**: in concurrent environments, prefer concurrent queues from `java.util.concurrent` (such as `ConcurrentLinkedQueue`, `ArrayBlockingQueue`, or `LinkedBlockingQueue`).
  - `ArrayDeque` does **not support indexed access**: if you need $O(1)$ lookup by position, use an array or `ArrayList`.

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

> **Mathematical Floor vs. Java Integer Division:** In Java, integer division `(i - 1) / 2` truncates towards zero. For any valid child index in a 0-indexed heap ($i \ge 1$), the numerator $i - 1$ is non-negative ($i - 1 \ge 0$). On non-negative numbers, truncation towards zero is mathematically identical to the mathematical floor function $\lfloor(i - 1) / 2\rfloor$. Thus, Java's `(i - 1) / 2` is exact and overflow-safe for valid array indices.

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

#### Breadth-First Search (BFS) Implementation & Invariants
BFS explores vertices in concentric layers outward from a source, using a FIFO queue.
- **Shortest Path Invariant:** In an unweighted graph, the first time vertex $v$ is dequeued, the distance recorded is guaranteed to be the shortest path from the source.
- **Why `visited[v] = true` Happens at Enqueue:** Marking a node visited **upon enqueuing** (rather than upon dequeuing) is strictly necessary to prevent adding the same node to the queue multiple times across parallel edges, keeping queue space bounded by $O(V)$.

```java
public static int[] bfsShortestDistances(int numVertices, List<List<Integer>> adjList, int source) {
    int[] dist = new int[numVertices];
    Arrays.fill(dist, -1);
    boolean[] visited = new boolean[numVertices];
    Deque<Integer> queue = new ArrayDeque<>();

    visited[source] = true;
    dist[source] = 0;
    queue.offer(source);

    while (!queue.isEmpty()) {
        int u = queue.poll();
        for (int v : adjList.get(u)) {
            if (!visited[v]) {
                visited[v] = true;      // Mark visited immediately on enqueue
                dist[v] = dist[u] + 1;  // Unweighted shortest path
                queue.offer(v);
            }
        }
    }
    return dist;
}
```
- **Time Complexity:** $O(V + E)$ — every vertex is enqueued/dequeued once; every edge is scanned once (directed) or twice (undirected).
- **Auxiliary Space:** $O(V)$ for queue and visited array.

#### Depth-First Search (DFS) Implementation & Call Stack Analysis
DFS explores deeply along each branch before backtracking.
```java
public static void dfs(int u, List<List<Integer>> adjList, boolean[] visited, List<Integer> traversalOrder) {
    visited[u] = true;
    traversalOrder.add(u);
    for (int v : adjList.get(u)) {
        if (!visited[v]) {
            dfs(v, adjList, visited, traversalOrder);
        }
    }
}
```
- **Recursion-Stack Space:** The call stack consumes $O(h)$ frames, where $h$ is the maximum recursion depth. In the worst case (a degenerate line graph $0 \to 1 \to 2 \dots \to V-1$), recursion stack depth is **$O(V)$**. For deep graphs ($V > 10^4$), an iterative DFS with an explicit `ArrayDeque` stack avoids JVM `StackOverflowError`.
- **Output Space vs. Auxiliary Space:** The `traversalOrder` list stores $V$ elements ($O(V)$ output space); auxiliary working space is $O(V)$ for `visited` and the recursion call stack.

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
        state.apply(choice);         // 1. CHOOSE: Mutate state
        backtrack(state, solutions); // 2. EXPLORE: Recurse into subproblem
        state.undo(choice);          // 3. UNCHOOSE: Restore invariant
    }
}
```

#### Concrete Worked Example: Generating All Distinct Subsets
**Problem:** Given array `nums = [1, 2, 3]`, generate all $2^3 = 8$ subsets.

```java
public static List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    List<Integer> currentPath = new ArrayList<>();
    backtrackSubsets(nums, 0, currentPath, result);
    return result;
}

private static void backtrackSubsets(int[] nums, int start, List<Integer> current, List<List<Integer>> result) {
    // Every state in the decision tree represents a valid subset
    result.add(new ArrayList<>(current)); // Snapshot copy: O(current.size())

    for (int i = start; i < nums.length; i++) {
        current.add(nums[i]);                         // 1. CHOOSE
        backtrackSubsets(nums, i + 1, current, result); // 2. EXPLORE
        current.remove(current.size() - 1);           // 3. UNCHOOSE (Restores list)
    }
}
```

#### Decision Tree & Execution Trace for `nums = [1, 2, 3]`
```
                         start=0, current=[]
                        /         |         \
              choose 1 /   choose 2|          \ choose 3
                      v           v            v
                   [1]           [2]          [3]
                  /   \           |
        choose 2 /     \choose 3  | choose 3
                v       v         v
              [1,2]    [1,3]    [2,3]
                |
       choose 3 |
                v
             [1,2,3]
```

- **State Restoration Invariant:** Notice line `current.remove(current.size() - 1)`. Because `current` is shared across recursive invocations, removing the recently added element before moving to the next iteration of the loop is strictly necessary. Without this unchoose step, choices from parallel branches would pollute sibling states.
- **Complexity Analysis (Stack Depth vs. Output Space):**
  - **Recursion Call Stack Depth:** The maximum tree depth is $n$ (from root down to `[1, 2, 3]`), so auxiliary stack space is strictly **$O(n)$**.
  - **Output Space:** There are $2^n$ total subsets, each with an average size of $n/2$. Total memory required to store the returned result is **$O(n \cdot 2^n)$**.
  - **Time Complexity:** $2^n$ states are visited; copying each subset takes $O(n)$ on average $\implies \Theta(n \cdot 2^n)$ time.

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

#### Concrete Example Setup
- Items: Item 0 ($wt=10, val=60$), Item 1 ($wt=20, val=100$), Item 2 ($wt=30, val=120$).
- Total Knapsack Capacity $W = 50$.

#### 2D State Definition & Recurrence
Let $dp[i][w]$ be the maximum value obtainable using a subset of the first $i$ items with capacity $w$:
$$dp[i][w] = \begin{cases} 
dp[i-1][w] & \text{if } w < w_i \text{ (item too heavy to include)} \\
\max(dp[i-1][w], \, dp[i-1][w - w_i] + v_i) & \text{if } w \ge w_i \text{ (choose max of excluding vs including)}
\end{cases}$$

#### Space Optimization: Rolling 1D Array and Why Backward Iteration Is Mandatory
Because $dp[i][w]$ depends only on row $i-1$, we can collapse the table into a single 1D array of size $W+1$:

```java
public static int knapsack(int[] values, int[] weights, int capacity) {
    int[] dp = new int[capacity + 1];
    for (int i = 0; i < values.length; i++) {
        int val = values[i], wt = weights[i];
        // Iterate backwards from capacity down to wt
        for (int w = capacity; w >= wt; w--) {
            dp[w] = Math.max(dp[w], dp[w - wt] + val);
        }
    }
    return dp[capacity];
}
```

#### Why Iterating Backward Prevents Item Duplication
Consider what would happen if the inner loop ran **forward** ($w = wt \to \text{capacity}$) on Item 0 ($wt=10, val=60$):
1. At $w = 10$: $dp[10] = \max(0, dp[0] + 60) = 60$.
2. At $w = 20$: $dp[20] = \max(0, dp[10] + 60) = 60 + 60 = 120$! (Item 0 was included a second time).
3. At $w = 30$: $dp[30] = \max(0, dp[20] + 60) = 120 + 60 = 180$! (Item 0 was included a third time).
Forward iteration solves **Unbounded Knapsack** (where items are infinite), not 0/1 Knapsack.

When iterating **backward** ($w = 50 \to 10$):
- When computing $dp[50]$, it references $dp[50 - 10] = dp[40]$, which still holds the value from the *previous* item $i-1$.
- Every element $dp[w - wt]$ consulted has not yet been overwritten in the current item iteration, guaranteeing that each item is included at most once.

#### Value Table Trace for Capacity $W = 50$
- After Item 0 ($wt=10, val=60$): $dp[10 \dots 50] = 60$.
- After Item 1 ($wt=20, val=100$): $dp[20] = 100$, $dp[30 \dots 50] = \max(60, dp[w-20] + 100) = 160$.
- After Item 2 ($wt=30, val=120$):
  - At $w=50$: $dp[50] = \max(160, dp[50-30] + 120) = \max(160, 100 + 120) = \mathbf{220}$ (Items 1 and 2: $100 + 120 = 220$, weights $20 + 30 = 50$).
- **Final Result:** $220$.
- **Complexity:** Time: $O(n \cdot W)$, Auxiliary Space: $O(W)$ (compressed from $O(n \cdot W)$).

---

## Section 18 — Sorting and Searching Trade-Offs

### 18.1 Comprehensive Sorting Comparison Matrix

| Algorithm | Best Time | Average Time | Worst Time | Auxiliary Space | Stable? | Key Mechanism & Trade-Off |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Insertion Sort** | $O(n)$ | $O(n^2)$ | $O(n^2)$ | $O(1)$ | Yes | Simple; optimal on nearly-sorted data or small arrays ($n \le 32$). |
| **Merge Sort** | $O(n \log n)$ | $O(n \log n)$ | $O(n \log n)$ | $O(n)$ | Yes | Deterministic divide-and-conquer; requires $O(n)$ auxiliary buffer. |
| **QuickSort** | $O(n \log n)$ | $O(n \log n)$ | $O(n^2)$ | $O(\log n)$ avg<br>$O(n)$ worst | No | Cache-friendly in-place partitioning. Worst-case $O(n^2)$ time and $O(n)$ recursion depth if pivot choices are degenerate. |
| **HeapSort** | $O(n \log n)$ | $O(n \log n)$ | $O(n \log n)$ | $O(1)$ | No | Guaranteed in-place $O(n \log n)$; poorer cache locality than Quicksort due to index jumps. |

#### Notes on QuickSort Auxiliary Space
- **Expected / Average Stack Depth:** With random pivot selection or median-of-three, the recursion tree is balanced, consuming $O(\log n)$ stack frames.
- **Worst-Case Stack Depth:** If partitions are unbalanced (e.g., partitioning an already-sorted array with a naive first-element pivot without tail-call recursion optimization), the recursion tree degenerates into a linear chain of depth $O(n)$.
- **Engineering Mitigation:** Implementers recurse on the smaller partition first and eliminate the tail call for the larger partition, guaranteeing at most $O(\log n)$ stack depth even in worst-case time scenarios.

### 18.2 Java Runtime Sorting: API Guarantees vs. Implementation Choices

It is vital to distinguish between what the Java Standard Library specification guarantees versus how OpenJDK chooses to implement those guarantees:
- **Object Arrays (`Arrays.sort(T[])`, `Collections.sort()`):**
  - *API Specification Guarantee:* Must be a **stable** sort running in guaranteed $O(n \log n)$ time.
  - *OpenJDK Implementation:* Uses **Timsort** (an adaptive hybrid of MergeSort and InsertionSort designed by Tim Peters). Stability is mandatory so that secondary sorting keys preserve prior ordering.
- **Primitive Arrays (`Arrays.sort(int[])`, `Arrays.sort(double[])`, etc.):**
  - *API Specification Guarantee:* Fast in-place sort. Stability is not required because primitive identical values (e.g., two `int` values equal to 5) are indistinguishable.
  - *OpenJDK Implementation:* Uses **Dual-Pivot Quicksort** by Vladimir Yaroslavskiy, Jon Bentley, and Joshua Bloch. It partitions the array into three segments using two pivots, maximizing CPU L1/L2 cache locality and avoiding auxiliary array allocation.

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
| "Contiguous subarray with condition" | Sliding Window OR Prefix Sums | Does the condition satisfy monotonicity? If negative numbers exist, sum-based sliding window is invalid. |
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
**Q:** In binary search, why is the midpoint calculation `mid = low + ((high - low) / 2)` or `(low + high) >>> 1` preferable to `(low + high) / 2`, and what loop invariant guarantees termination in `while (low < high)`?
**Model Answer:**
In Java, signed 32-bit integers overflow into negative values when their sum exceeds $2^{31} - 1 = 2,147,483,647$. Using `(low + high) / 2` with large bounds results in a negative index, triggering `ArrayIndexOutOfBoundsException`. The unsigned right-shift operator `>>>` treats the 32-bit sum as unsigned, safely evaluating values up to $2^{32} - 1$. The standard expression `low + ((high - low) / 2)` completely avoids large intermediate sums.
The loop invariant for `while (low < high)` with `mid = low + ((high - low) / 2)` guarantees that $\text{low} \le \text{mid} < \text{high}$. When updating `low = mid + 1`, `low` strictly increases; when updating `high = mid`, `high` strictly decreases. Because the search interval $[\text{low}, \text{high})$ strictly shrinks by at least one element on every iteration, termination is mathematically guaranteed in at most $\lceil\log_2 n\rceil + 1$ iterations.

### Question 2: Mechanistic Debugging
**Q:** A candidate writes the following code to find the longest subarray with sum $\le K$. When tested with `nums = [3, -2, 5]`, it fails. Explain why, and state the correct algorithmic strategy.
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
The code incorrectly assumes sliding window monotonicity. In an array with negative numbers, contracting the window by incrementing `left` does not guarantee decreasing the sum; if `nums[left]` is negative, `sum -= nums[left]` will actually **increase** the sum. Furthermore, a valid subarray might be formed later because subsequent negative numbers could bring the sum back below $K$.
**Correct Strategies for Subarray Problems with Negative Numbers:**
- If the problem asks for **Subarray Sum Equals $K$**, use **Prefix Sums + Frequency Hash Map** ($O(n)$ time, $O(n)$ space).
- If the problem asks for **Shortest Subarray with Sum at Least $K$**, use **Prefix Sums + Monotonic Deque** ($O(n)$ time, $O(n)$ space).
- For this specific problem (**Longest Subarray with Sum at Most $K$**), compute the prefix sum array $P$. The condition is $P[j] - P[i] \le K \iff P[i] \ge P[j] - K$ with $i < j$, maximizing $j - i$. This can be solved in $O(n \log n)$ time by maintaining a monotonic list of prefix sums and using binary search, or in $O(n)$ time using precomputed prefix-minimum arrays with a two-pointer scan.

### Question 3: Trade-Off Analysis
**Q:** Compare `ArrayList` versus `LinkedList` in Java for implementing a queue. What is the recommended standard collection, and why?
**Model Answer:**
Neither `ArrayList` nor `LinkedList` is optimal for a standard queue; `java.util.ArrayDeque` is the recommended default.
- **`ArrayList`:** Provides $O(1)$ random access and excellent spatial cache locality. However, removing from the head (`remove(0)`) requires shifting all $n-1$ subsequent elements leftward, degrading queue dequeue operations to $O(n)$ time.
- **`LinkedList`:** Provides $O(1)$ pointer-based insertion at tail and removal at head. However, every element is wrapped in a `Node` object. On modern 64-bit JVMs with Compressed OOPs enabled (the default for heaps $< 32\text{ GB}$), each node consumes 24 bytes (12-byte header + 12 bytes for `item`, `next`, `prev` references). Without Compressed OOPs, each node consumes 40 bytes. This causes heavy garbage collection churn and severe hardware CPU cache misses because node objects are scattered arbitrarily across the heap.
- **`ArrayDeque`:** Implemented as a resizable circular array buffer. It provides $O(1)$ amortized insertion at tail and removal at head with zero node object allocations and contiguous memory layout that maximizes cache pre-fetching.
*Caveat:* `ArrayDeque` prohibits `null` elements and is not thread-safe; if thread safety is required, use concurrent queues like `ConcurrentLinkedQueue` or `ArrayBlockingQueue`.

---

## Section 21 — DSA Cheatsheet: Interview Revision Reference

> **Quick-Revision Technical Summary:** This self-contained cheatsheet consolidates core algorithmic complexities, data-structure contracts, pattern indicators, formulas, and interview failure modes taught throughout Module 1.

### 21.1 Algorithmic Complexity Master Reference

All bounds below are asymptotic and reflect typical production and interview implementations:

| Structure / Algorithm | Operation / Case | Time Complexity | Auxiliary Space | Conditional Assumptions / Critical Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Array** | Index Access | $\Theta(1)$ | $O(1)$ | Direct offset calculation relative to base reference. |
| **Array** | Search (Unsorted) | $O(n)$ | $O(1)$ | Requires linear scan across entire array. |
| **Array** | Search (Sorted) | $\Theta(\log n)$ | $O(1)$ | Requires monotonic ordering; binary search. |
| **Dynamic Array (`ArrayList`)** | Append (`add`) | $O(1)$ amortized | $O(1)$ amortized | $O(n)$ worst-case during buffer reallocation/copying. |
| **Dynamic Array (`ArrayList`)** | Insert/Delete at Index $i$ | $O(n)$ | $O(1)$ | Shifting elements right or left by $n - i$ positions. |
| **Singly Linked List** | Insert/Delete after Node | $\Theta(1)$ | $O(1)$ | Given direct reference to predecessor node. |
| **Singly Linked List** | Access / Search | $O(n)$ | $O(1)$ | Sequential traversal from `head`. |
| **Hash Table (`HashMap`)** | Insert / Lookup / Delete | $O(1)$ expected avg<br>$O(\log n)$ worst | $O(n)$ total space | Assumes well-distributed hash code; OpenJDK treeifies buckets to Red-Black trees when chain $\ge 8$ and capacity $\ge 64$. |
| **Binary Search Tree (Unbalanced)** | Search / Insert / Delete | $O(h)$ ($h$ is height)<br>$O(n)$ worst-case | $O(h)$ stack/aux | Degenerates to linked list on sorted input. |
| **Balanced BST (`TreeMap` / AVL)** | Search / Insert / Delete | $\Theta(\log n)$ | $O(1)$ aux ($O(n)$ total) | Red-Black Tree guarantees height $h \le 2\log_2(n+1)$. |
| **Binary Heap (`PriorityQueue`)** | Peek Min/Max | $\Theta(1)$ | $O(1)$ | Root element inspection. |
| **Binary Heap (`PriorityQueue`)** | Insert (`offer`) / Extract (`poll`) | $O(\log n)$ | $O(1)$ | Sift-up / Sift-down along tree height. |
| **Binary Heap Construction** | Build Heap (`heapify`) | $\Theta(n)$ | $O(1)$ | Bottom-up sift-down across leaves and subtrees. |
| **Disjoint Set Union (DSU)** | `find` / `union` | $\Theta(\alpha(n))$ amortized | $O(n)$ total space | Uses both Path Compression and Union by Rank/Size ($\alpha(n) \le 4$). |
| **Breadth-First Search (BFS)** | Graph Traversal | $\Theta(V + E)$ | $O(V)$ | Using FIFO queue; computes unweighted shortest paths. |
| **Depth-First Search (DFS)** | Graph Traversal | $\Theta(V + E)$ | $O(V)$ worst stack | Recursion stack depth is $O(V)$ on line graphs. |
| **Dijkstra's Algorithm** | Single-Source Shortest Path | $O((V + E) \log V)$ | $O(V)$ | Uses Min-Heap `PriorityQueue`; **requires non-negative edge weights**. |
| **Topological Sort (Kahn's)** | DAG Linear Ordering | $\Theta(V + E)$ | $O(V)$ | In-degree queue; detects cycles if processed vertices $< V$. |
| **Merge Sort** | Sorting | $\Theta(n \log n)$ | $O(n)$ | Stable; deterministic; requires auxiliary buffer. |
| **QuickSort** | Sorting | $O(n \log n)$ avg<br>$O(n^2)$ worst | $O(\log n)$ avg stack<br>$O(n)$ worst stack | In-place; unstable; sensitive to pathological pivot choices. |
| **HeapSort** | Sorting | $\Theta(n \log n)$ | $O(1)$ | In-place; unstable; poorer spatial locality than QuickSort. |

### 21.2 Data Structure Operation Comparison

| Data Structure | Random Access | Search | Insertion | Deletion | Memory Locality | Primary Use Case |
| :--- | :---: | :---: | :---: | :---: | :--- | :--- |
| **Primitive Array (`int[]`)** | $O(1)$ | $O(n)$ | $O(n)$ | $O(n)$ | Contiguous (Optimal) | Known fixed size, cache-sensitive tight loops. |
| **`ArrayList`** | $O(1)$ | $O(n)$ | $O(1)$ amortized at end | $O(n)$ | Contiguous (Optimal) | General-purpose list with frequent read/append. |
| **`ArrayDeque`** | None ($O(n)$) | $O(n)$ | $O(1)$ amortized head/tail | $O(1)$ head/tail | Circular Array (High) | Standard LIFO stack and FIFO queue default. |
| **`LinkedList`** | $O(n)$ | $O(n)$ | $O(1)$ with pointer | $O(1)$ with pointer | Dispersed Heap (Poor) | Highly specific node-splicing (e.g. LRU cache internal list). |
| **`HashMap`** | N/A | $O(1)$ avg | $O(1)$ avg | $O(1)$ avg | Bucket array + nodes | Key-value mapping, $O(1)$ membership, complement lookup. |
| **`TreeMap`** | N/A | $O(\log n)$ | $O(\log n)$ | $O(\log n)$ | Balanced binary tree | Ordered keys, range queries (`subMap`, `floorKey`, `ceilingKey`). |
| **`PriorityQueue`** | N/A | $O(n)$ | $O(\log n)$ | $O(\log n)$ (root) | Compact array (High) | Streaming extrema, Top-K elements, Dijkstra, Huffman coding. |

### 21.3 Algorithm Pattern Recognition Guide

| Problem Signal / Clue | Primary Pattern | Secondary / Fallback Pattern | Mandatory Verification Before Committing |
| :--- | :--- | :--- | :--- |
| "Find pair with target sum" | Two Pointers (if array is sorted) | Hash Map (Complement Lookup) | Can array be sorted without breaking output index requirements? |
| "Contiguous subarray with sum / count" | Sliding Window (if non-negative) | Prefix Sums + Hash Map | Does condition satisfy monotonicity? If negative numbers exist, sum-based sliding window is invalid! |
| "Shortest subarray with sum $\ge K$" | Prefix Sums + Monotonic Deque | Binary Search over Prefix Sums | Monotonic deque gives $O(n)$; sliding window fails if negatives exist. |
| "Longest substring without duplicates" | Sliding Window + Frequency Map | Two Pointers | Ensure `left` pointer never moves backward (`left = Math.max(left, lastSeen + 1)`). |
| "Search in sorted / monotonic domain" | Binary Search (`[low, high)`) | Linear Scan | Verify boolean predicate $f(x)$ is strictly monotonic (all `false` then all `true`). |
| "Find Next Greater / Smaller element" | Monotonic Stack | Segment Tree / Brute Force | Maintain stack of indices; monotonic invariant matches search direction. |
| "Shortest path in unweighted graph/grid" | Breadth-First Search (BFS) | Bidirectional BFS | Verify all edge weights are uniform ($1$); mark visited upon enqueue. |
| "Shortest path in weighted graph" | Dijkstra's Algorithm | Bellman-Ford (if negatives) | Verify all edge weights are non-negative ($w \ge 0$). |
| "Task scheduling / prerequisite order" | Topological Sort (Kahn's / DFS) | Cycle Detection | Graph must be a DAG; verify cycle detection check. |
| "Connected components / cycle in undirected"| Disjoint Set Union (Union-Find) | DFS / BFS | Union-Find with path compression + rank is nearly $O(1)$ per query. |
| "Generate all subsets / permutations" | Backtracking | Iterative Bitmasking | Branching factor must be small ($n \le 20$ for subsets, $n \le 11$ for permutations). |
| "Optimize over choices with overlapping subproblems" | Dynamic Programming | Memoized Recursion | Verify subproblems overlap and optimal substructure holds. |

### 21.4 Essential Formulas and Invariants

1. **Binary Search Midpoint:**
   $$\text{mid} = \text{low} + \frac{\text{high} - \text{low}}{2} \quad \text{or} \quad \text{mid} = \text{low} + ((\text{high} - \text{low}) \mathbin{>\!\!>\!\!>} 1)$$
   *Invariant in `[low, high)`:* Target always lies in $[\text{low}, \text{high})$. Terminates when $\text{low} == \text{high}$.

2. **Binary Heap Index Relationships (0-indexed):**
   $$\text{left}(i) = 2i + 1, \quad \text{right}(i) = 2i + 2, \quad \text{parent}(i) = \frac{i - 1}{2}$$

3. **Prefix Sum Range Query:**
   $$\text{Sum}(A[L \dots R]) = P[R+1] - P[L], \quad \text{where } P[0] = 0, \, P[k] = \sum_{i=0}^{k-1} A[i]$$

4. **Kahn's Topological Sort Cycle Invariant:**
   $$\text{ProcessedNodeCount} == |V| \iff \text{Graph is a DAG (Acyclic)}$$

5. **0/1 Knapsack Space-Optimized Recurrence:**
   $$dp[w] = \max(dp[w], \, dp[w - w_i] + v_i) \quad \text{iterated } w = W \text{ down to } w_i$$

### 21.5 Sorting and Searching Comparison

| Algorithm | Stability | In-Place? | Best Time | Average Time | Worst Time | Aux Space | Practical Best Use Case |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Insertion Sort** | Yes | Yes | $O(n)$ | $O(n^2)$ | $O(n^2)$ | $O(1)$ | Small arrays ($n \le 32$) or nearly-sorted data. |
| **Selection Sort** | No | Yes | $O(n^2)$ | $O(n^2)$ | $O(n^2)$ | $O(1)$ | Minimizing number of writes to memory ($O(n)$ swaps). |
| **Merge Sort** | Yes | No | $O(n \log n)$ | $O(n \log n)$ | $O(n \log n)$ | $O(n)$ | Linked list sorting, external tape sorting, stable requirements. |
| **QuickSort** | No | Yes | $O(n \log n)$ | $O(n \log n)$ | $O(n^2)$ | $O(\log n)$ avg | General-purpose in-memory array sorting (Dual-Pivot Quicksort). |
| **HeapSort** | No | Yes | $O(n \log n)$ | $O(n \log n)$ | $O(n \log n)$ | $O(1)$ | Embedded systems needing guaranteed $O(n \log n)$ with $O(1)$ RAM. |
| **Counting Sort** | Yes | No | $O(n + k)$ | $O(n + k)$ | $O(n + k)$ | $O(k)$ | Small integer range $k \ll n$. |
| **Radix Sort** | Yes | No | $O(d(n + k))$| $O(d(n + k))$| $O(d(n + k))$| $O(n + k)$ | Fixed-length keys / integers / strings. |

### 21.6 Java Collections Framework (JCF) & Runtime Reference

- **`HashMap` (OpenJDK Internals):**
  - Bucket index: `(capacity - 1) & hash`, where `hash = (h = key.hashCode()) ^ (h >>> 16)`.
  - Initial capacity: `16`. Load factor: `0.75`. Resizes by doubling ($2\times$).
  - Treeification: Bucket converts from linked list to Red-Black tree when chain length $\ge 8$ **AND** table capacity $\ge 64$.
  - Untreeification: Converts from Red-Black tree back to linked list when entries drop to $\le 6$.
- **`ArrayList` (OpenJDK Internals):**
  - Growth formula: `newCapacity = oldCapacity + (oldCapacity >> 1)` ($1.5\times$ growth factor).
- **`ArrayDeque`:**
  - Circular array implementation.
  - Recommended general-purpose stack/queue default; forbids `null`; not thread-safe.
- **`PriorityQueue`:**
  - Min-heap by default. Pass `Comparator.reverseOrder()` or `(a, b) -> Integer.compare(b, a)` for max-heap.
  - Heapify constructor `new PriorityQueue<>(collection)` runs in $\Theta(n)$ time.
- **`Arrays.sort()`:**
  - Primitives (`int[]`): Dual-Pivot Quicksort (unstable, in-place, cache-optimized).
  - Objects (`T[]`): Timsort (stable, $O(n)$ auxiliary buffer, guaranteed $O(n \log n)$).

### 21.7 10-Step Interview Problem-Solving Checklist

1. [ ] **Restate & Clarify:** Repeat problem in your own words. Confirm input types, bounds, and output format.
2. [ ] **Inspect Constraints:** Check $n$ magnitude ($10^5 \implies O(n)$ or $O(n \log n)$; $10^3 \implies O(n^2)$; $20 \implies O(2^n)$).
3. [ ] **State Brute Force:** Articulate the naive solution clearly. State its exact time and space complexity.
4. [ ] **Identify Redundancy:** Where does brute force recalculate what was already computed?
5. [ ] **Select Pattern:** Match structure to the bottleneck (hashing for lookup, heaps for extrema, sliding window for contiguous ranges).
6. [ ] **Define Invariant:** State the loop invariant, pointer boundary conditions, or recursive state definition.
7. [ ] **Dry-Run Small Trace:** Trace variable values manually on a 3-to-4 element test case before writing code.
8. [ ] **Implement Cleanly:** Write modular, idiomatic Java with meaningful names and defensive parameter checks.
9. [ ] **Check Boundary Cases:** Test empty input, single element, duplicates, all negatives, and overflow limits.
10. [ ] **State Complexity:** Provide tight $\Theta$ bounds for time complexity and auxiliary space.

### 21.8 Common Engineering Failure Modes & Traps

1. **Integer Overflow in Midpoint:** Naive `(low + high) / 2` wraps to negative when sum $> 2^{31} - 1$. Use `low + (high - low) / 2` or `(low + high) >>> 1`.
2. **Negative Numbers in Sum-Based Sliding Window:** Expanding/contracting window breaks monotonicity. Use Prefix Sums + Hash Map ($= K$) or Monotonic Deque ($\ge K$).
3. **Key Mutation in Hash Maps:** Mutating an object after inserting it as a key changes its bucket index, making it unfindable and leaking memory. Keep keys immutable.
4. **Graph Traversal Without Visited Set:** Cyclic graphs cause infinite loops in DFS/BFS. Always maintain a `visited` set or array.
5. **Marking Visited on Dequeue Instead of Enqueue in BFS:** Causes parallel edges to enqueue duplicate node copies, exploding queue memory beyond $O(V)$. Mark visited on enqueue.
6. **String Concatenation in Loops:** `s += c` copies all characters on every iteration, creating $O(n^2)$ time and massive GC garbage. Use `StringBuilder`.
7. **Modifying Collection During Traversal:** Mutating a collection while iterating directly throws `ConcurrentModificationException`. Use `Iterator.remove()` or indices.
8. **Forward Iteration in 0/1 Knapsack:** Forward iteration updates the 1D DP table using items from the *current* iteration, turning 0/1 Knapsack into Unbounded Knapsack. Iterate backward.

### 21.9 Essential Interview Questions & Rapid Answers

1. **Q: Why does Dijkstra fail on negative edge weights?**
   *A:* Dijkstra greedily finalizes a vertex's shortest distance when popped from the priority queue, assuming no future edge can shorten it. A negative edge discovered later violates this greedy choice property and would require retroactively updating already finalized paths.
2. **Q: How does `ArrayDeque` differ from `LinkedList` for queue operations?**
   *A:* `ArrayDeque` is backed by a contiguous circular array with $O(1)$ amortized operations, zero per-element wrapper allocations, and high CPU cache locality. `LinkedList` allocates a 24-byte `Node` object per entry (on 64-bit JVMs with Compressed OOPs) and causes severe cache misses.
3. **Q: What is the difference between Big O and worst-case complexity?**
   *A:* Big O is an asymptotic upper bound on a mathematical function; worst-case describes an input scenario that causes the algorithm to execute the maximum number of operations. An algorithm can have an $O(1)$ best-case and an $O(n)$ worst-case.
4. **Q: Why is building a binary heap $\Theta(n)$ instead of $O(n \log n)$?**
   *A:* Bottom-up heapify performs sift-down on each node. The vast majority of nodes reside near the bottom: half are leaves (sift-down distance 0), a quarter have height 1, an eighth have height 2. Summing $\sum_{h=0}^{\log n} \frac{n}{2^h} h = O(n)$.
5. **Q: When should you use a Monotonic Stack?**
   *A:* When you need to find the Nearest Greater Element or Nearest Smaller Element for every element in an array in linear $O(n)$ time.

### 21.10 Mental Models and Critical Distinctions

- **Theoretical RAM Model vs. Managed JVM Reality:** Theoretical models assume raw memory pointer arithmetic; in Java, the JVM abstracts physical addresses, relocates objects during GC compaction, and preserves $O(1)$ random access via internal offset calculation.
- **Auxiliary Space vs. Output Space:** Auxiliary space measures working memory allocated by the algorithm to solve the problem; output space is the memory occupied by the returned result (e.g., $O(n \cdot 2^n)$ for all subsets).
- **Amortized $O(1)$ vs. Strict $O(1)$:** Amortized $O(1)$ guarantees that any sequence of $M$ operations takes $O(M)$ total time (average cost per operation is constant), even if individual operations (like buffer reallocation) take $O(n)$ worst-case time.
- **Greedy vs. Dynamic Programming:** Greedy makes a single locally optimal choice without backtracking; DP evaluates multiple overlapping choices and records subproblem answers to construct a globally optimal solution.
