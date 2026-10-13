package assets.code_examples;

import java.util.*;

/**
 * Module 01 Code Examples & Comprehensive Test Harness.
 *
 * Demonstrates idiomatic, production-grade Java 21 implementations of core
 * algorithms taught in Module 1: Data Structures and Algorithms.
 *
 * Each implementation contains explicit preconditions, invariants,
 * boundary condition checks, and assertions verifying correctness.
 */
public class Module01CodeExamples {

    // =========================================================================
    // 1. TWO POINTERS: Sorted Two Sum & In-Place Deduplication
    // =========================================================================

    /**
     * Finds 1-based indices of two numbers in a non-decreasing sorted array that sum to target.
     * Precondition: numbers is sorted in non-decreasing order.
     * Time Complexity: O(n) - pointers move strictly towards each other.
     * Auxiliary Space: O(1) - constant memory.
     */
    public static int[] twoSumSorted(int[] numbers, int target) {
        if (numbers == null || numbers.length < 2) {
            return new int[]{-1, -1};
        }
        int left = 0;
        int right = numbers.length - 1;

        while (left < right) {
            long currentSum = (long) numbers[left] + numbers[right]; // Avoid integer overflow
            if (currentSum == target) {
                return new int[]{left + 1, right + 1}; // 1-based index
            } else if (currentSum < target) {
                left++; // Monotonicity guarantee: need a larger sum
            } else {
                right--; // Monotonicity guarantee: need a smaller sum
            }
        }
        return new int[]{-1, -1}; // Not found
    }

    /**
     * Removes duplicates in-place from a sorted array and returns the number of unique elements.
     * Invariant: elements in numbers[0 .. writeIndex] are unique and sorted.
     * Time Complexity: O(n)
     * Auxiliary Space: O(1)
     */
    public static int removeDuplicatesSorted(int[] numbers) {
        if (numbers == null || numbers.length == 0) {
            return 0;
        }
        int writeIndex = 0;
        for (int readIndex = 1; readIndex < numbers.length; readIndex++) {
            if (numbers[readIndex] != numbers[writeIndex]) {
                writeIndex++;
                numbers[writeIndex] = numbers[readIndex];
            }
        }
        return writeIndex + 1;
    }

    // =========================================================================
    // 2. SLIDING WINDOW: Longest Substring Without Repeating Characters
    // =========================================================================

    /**
     * Computes the length of the longest substring without duplicate characters.
     * Invariant: window s[left .. right] contains strictly distinct characters.
     * Time Complexity: O(n) - each character is visited at most twice.
     * Auxiliary Space: O(min(n, alphabet_size)) - map stores at most distinct chars.
     */
    public static int lengthOfLongestSubstringWithoutRepeating(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        Map<Character, Integer> lastSeen = new HashMap<>();
        int maxLength = 0;
        int left = 0;

        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            if (lastSeen.containsKey(c)) {
                // Advance left boundary to skip duplicate, ensuring left never moves backwards
                left = Math.max(left, lastSeen.get(c) + 1);
            }
            lastSeen.put(c, right);
            maxLength = Math.max(maxLength, right - left + 1);
        }
        return maxLength;
    }

    // =========================================================================
    // 3. PREFIX SUMS & HASHING: Subarray Sum Equals Target (Handles Negatives)
    // =========================================================================

    /**
     * Counts the total number of contiguous subarrays whose sum equals target.
     * Concept: prefixSum[j] - prefixSum[i-1] = target => prefixSum[i-1] = prefixSum[j] - target.
     * Time Complexity: O(n)
     * Auxiliary Space: O(n)
     */
    public static int countSubarraysWithSum(int[] nums, int target) {
        if (nums == null || nums.length == 0) {
            return 0;
        }
        Map<Long, Integer> prefixCounts = new HashMap<>();
        prefixCounts.put(0L, 1); // Base case: prefix sum of empty subarray is 0

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

    // =========================================================================
    // 4. BINARY SEARCH: Overflow-Safe Lower Bound (First Index >= Target)
    // =========================================================================

    /**
     * Finds the lower bound: the first index where array[index] >= target.
     * If all elements are smaller than target, returns array.length.
     * Search space invariant: answer always lies in range [low, high].
     * Time Complexity: O(log n)
     * Auxiliary Space: O(1)
     */
    public static int lowerBound(int[] arr, int target) {
        if (arr == null) {
            return -1;
        }
        int low = 0;
        int high = arr.length; // Half-open range [0, arr.length]

        while (low < high) {
            // Unsigned shift avoids 32-bit signed integer overflow (low + high)
            int mid = low + ((high - low) >>> 1);
            if (arr[mid] >= target) {
                high = mid; // Answer could be mid, or further left
            } else {
                low = mid + 1; // arr[mid] < target, so answer must be strictly > mid
            }
        }
        return low;
    }

    // =========================================================================
    // 5. MONOTONIC STACK: Next Greater Element (Rightward)
    // =========================================================================

    /**
     * For each element in nums, finds the first element to its right that is strictly greater.
     * If no greater element exists, records -1.
     * Stack Invariant: Stack stores indices with strictly decreasing values (monotonically decreasing).
     * Time Complexity: O(n) - each index pushed and popped at most once.
     * Auxiliary Space: O(n)
     */
    public static int[] nextGreaterElement(int[] nums) {
        if (nums == null || nums.length == 0) {
            return new int[0];
        }
        int n = nums.length;
        int[] result = new int[n];
        Arrays.fill(result, -1);
        Deque<Integer> stack = new ArrayDeque<>(); // Stores indices

        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && nums[stack.peek()] < nums[i]) {
                int resolvedIndex = stack.pop();
                result[resolvedIndex] = nums[i];
            }
            stack.push(i);
        }
        return result;
    }

    // =========================================================================
    // 6. DISJOINT SET UNION (UNION-FIND): Path Compression + Union by Rank
    // =========================================================================

    public static class DisjointSetUnion {
        private final int[] parent;
        private final int[] rank;
        private int componentCount;

        public DisjointSetUnion(int n) {
            if (n <= 0) throw new IllegalArgumentException("Size must be positive");
            this.parent = new int[n];
            this.rank = new int[n];
            this.componentCount = n;
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                rank[i] = 0;
            }
        }

        public int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]); // Path compression: flattens the tree
            }
            return parent[x];
        }

        public boolean union(int x, int y) {
            int rootX = find(x);
            int rootY = find(y);
            if (rootX == rootY) {
                return false; // Already in same component (cycle detected)
            }
            // Union by rank: attach shallower tree to deeper tree
            if (rank[rootX] < rank[rootY]) {
                parent[rootX] = rootY;
            } else if (rank[rootX] > rank[rootY]) {
                parent[rootY] = rootX;
            } else {
                parent[rootY] = rootX;
                rank[rootX]++;
            }
            componentCount--;
            return true;
        }

        public int getComponentCount() {
            return componentCount;
        }
    }

    // =========================================================================
    // 7. DIJKSTRA'S ALGORITHM: Non-Negative Single-Source Shortest Paths
    // =========================================================================

    public record Edge(int to, int weight) {}

    /**
     * Computes shortest path from source to all nodes in a weighted directed graph.
     * Precondition: weights must be non-negative (weight >= 0).
     * Time Complexity: O((V + E) log V) using binary heap PriorityQueue.
     * Auxiliary Space: O(V + E)
     */
    public static int[] dijkstraShortestPaths(int numVertices, List<List<Edge>> adjList, int source) {
        if (numVertices <= 0 || source < 0 || source >= numVertices) {
            throw new IllegalArgumentException("Invalid vertices or source");
        }
        int[] dist = new int[numVertices];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[source] = 0;

        // Min-heap storing [distance, vertex]
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
        pq.offer(new int[]{0, source});

        while (!pq.isEmpty()) {
            int[] current = pq.poll();
            int currentDist = current[0];
            int u = current[1];

            // Stale entry check (optimization for decrease-key simulation)
            if (currentDist > dist[u]) {
                continue;
            }

            for (Edge edge : adjList.get(u)) {
                int v = edge.to();
                int weight = edge.weight();
                if (weight < 0) {
                    throw new IllegalArgumentException("Negative edge weight detected! Dijkstra invalid.");
                }
                if (dist[u] + weight < dist[v]) {
                    dist[v] = dist[u] + weight;
                    pq.offer(new int[]{dist[v], v});
                }
            }
        }
        return dist;
    }

    // =========================================================================
    // 8. DYNAMIC PROGRAMMING: 0/1 Knapsack (Space-Optimized 1D Tabulation)
    // =========================================================================

    /**
     * Solves 0/1 Knapsack: maximize value without exceeding weight capacity W.
     * State: dp[w] = maximum value obtainable with capacity w using examined items.
     * Invariant: Backwards iteration prevents reusing the same item multiple times.
     * Time Complexity: O(n * W)
     * Auxiliary Space: O(W)
     */
    public static int knapsack01(int[] values, int[] weights, int capacity) {
        if (values == null || weights == null || values.length != weights.length || capacity <= 0) {
            return 0;
        }
        int[] dp = new int[capacity + 1];

        for (int i = 0; i < values.length; i++) {
            int val = values[i];
            int wt = weights[i];
            // Iterate backwards from capacity down to item weight
            for (int w = capacity; w >= wt; w--) {
                dp[w] = Math.max(dp[w], dp[w - wt] + val);
            }
        }
        return dp[capacity];
    }

    // =========================================================================
    // 9. BACKTRACKING: Distinct Subsets Generation (Include/Exclude)
    // =========================================================================

    public static List<List<Integer>> generateSubsets(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        if (nums == null) return result;
        backtrackSubsets(nums, 0, new ArrayList<>(), result);
        return result;
    }

    private static void backtrackSubsets(int[] nums, int index, List<Integer> current, List<List<Integer>> result) {
        result.add(new ArrayList<>(current)); // Snapshot valid subset
        for (int i = index; i < nums.length; i++) {
            current.add(nums[i]); // Choose
            backtrackSubsets(nums, i + 1, current, result); // Explore
            current.remove(current.size() - 1); // Unchoose (backtrack invariant)
        }
    }

    // =========================================================================
    // 10. COMPREHENSIVE VERIFICATION SUITE (TEST RUNNER)
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("Starting Module 01 Algorithmic Verification Test Suite...");

        // Test 1: Two Sum Sorted
        assert Arrays.equals(twoSumSorted(new int[]{2, 7, 11, 15}, 9), new int[]{1, 2}) : "TwoSum basic failed";
        assert Arrays.equals(twoSumSorted(new int[]{-5, -2, 0, 3, 8}, 1), new int[]{2, 4}) : "TwoSum negative failed";
        assert Arrays.equals(twoSumSorted(new int[]{1, 2}, 10), new int[]{-1, -1}) : "TwoSum not found failed";
        System.out.println("  [PASS] Two Pointers (Sorted Two Sum)");

        // Test 2: In-place Deduplication
        int[] dupArr = new int[]{0, 0, 1, 1, 1, 2, 2, 3, 3, 4};
        int uniqueCount = removeDuplicatesSorted(dupArr);
        assert uniqueCount == 5 : "Deduplication count failed";
        assert Arrays.equals(Arrays.copyOfRange(dupArr, 0, 5), new int[]{0, 1, 2, 3, 4}) : "Deduplication values failed";
        System.out.println("  [PASS] Two Pointers (In-Place Deduplication)");

        // Test 3: Sliding Window Longest Substring
        assert lengthOfLongestSubstringWithoutRepeating("abcabcbb") == 3 : "Sliding window abcabcbb failed";
        assert lengthOfLongestSubstringWithoutRepeating("bbbbb") == 1 : "Sliding window bbbbb failed";
        assert lengthOfLongestSubstringWithoutRepeating("pwwkew") == 3 : "Sliding window pwwkew failed";
        assert lengthOfLongestSubstringWithoutRepeating("") == 0 : "Sliding window empty failed";
        System.out.println("  [PASS] Sliding Window (Longest Unique Substring)");

        // Test 4: Prefix Sums with Negatives
        int[] prefixArr = new int[]{1, -1, 5, -2, 3};
        assert countSubarraysWithSum(prefixArr, 3) == 3 : "Prefix sum target 3 failed"; // [1,-1,5,-2], [5,-2], [3]
        assert countSubarraysWithSum(new int[]{1, 1, 1}, 2) == 2 : "Prefix sum target 2 failed";
        System.out.println("  [PASS] Prefix Sums with Hash Map (Negative Handling)");

        // Test 5: Binary Search Lower Bound
        int[] sortedArr = new int[]{1, 2, 4, 4, 4, 6, 8};
        assert lowerBound(sortedArr, 4) == 2 : "Lower bound exact match failed";
        assert lowerBound(sortedArr, 5) == 5 : "Lower bound insertion point failed";
        assert lowerBound(sortedArr, 0) == 0 : "Lower bound smaller than all failed";
        assert lowerBound(sortedArr, 10) == 7 : "Lower bound larger than all failed";
        System.out.println("  [PASS] Binary Search (Overflow-Safe Lower Bound)");

        // Test 6: Monotonic Stack (Next Greater Element)
        int[] stackInput = new int[]{4, 5, 2, 25};
        int[] stackExpected = new int[]{5, 25, 25, -1};
        assert Arrays.equals(nextGreaterElement(stackInput), stackExpected) : "Monotonic stack failed";
        System.out.println("  [PASS] Monotonic Stack (Next Greater Element)");

        // Test 7: Disjoint Set Union
        DisjointSetUnion dsu = new DisjointSetUnion(5);
        assert dsu.getComponentCount() == 5;
        assert dsu.union(0, 1);
        assert dsu.union(1, 2);
        assert !dsu.union(0, 2) : "Redundant union cycle detection failed";
        assert dsu.find(0) == dsu.find(2);
        assert dsu.getComponentCount() == 3;
        System.out.println("  [PASS] Disjoint Set Union (Path Compression & Rank)");

        // Test 8: Dijkstra's Shortest Path
        int vCount = 5;
        List<List<Edge>> adj = new ArrayList<>();
        for (int i = 0; i < vCount; i++) adj.add(new ArrayList<>());
        adj.get(0).add(new Edge(1, 4));
        adj.get(0).add(new Edge(2, 1));
        adj.get(2).add(new Edge(1, 2));
        adj.get(1).add(new Edge(3, 1));
        adj.get(2).add(new Edge(3, 5));
        adj.get(3).add(new Edge(4, 3));
        int[] dists = dijkstraShortestPaths(vCount, adj, 0);
        // Shortest paths: 0->0=0, 0->2=1, 0->2->1=3, 0->2->1->3=4, 0->2->1->3->4=7
        assert dists[0] == 0 && dists[1] == 3 && dists[2] == 1 && dists[3] == 4 && dists[4] == 7 : "Dijkstra failed";
        System.out.println("  [PASS] Dijkstra's Algorithm (PriorityQueue Shortest Paths)");

        // Test 9: 0/1 Knapsack
        int[] values = new int[]{60, 100, 120};
        int[] weights = new int[]{10, 20, 30};
        int capacity = 50;
        int maxVal = knapsack01(values, weights, capacity);
        assert maxVal == 220 : "0/1 Knapsack max value failed, expected 220, got " + maxVal;
        System.out.println("  [PASS] Dynamic Programming (0/1 Knapsack Tabulation)");

        // Test 10: Backtracking Subsets
        List<List<Integer>> subsets = generateSubsets(new int[]{1, 2, 3});
        assert subsets.size() == 8 : "Subsets count failed, expected 8, got " + subsets.size();
        System.out.println("  [PASS] Backtracking (Include/Exclude Subsets)");

        System.out.println("\nAll 10 Algorithmic Test Suites Passed Successfully!");
    }
}
