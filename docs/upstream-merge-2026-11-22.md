# Upstream integration review — 2026-11-22

## Scope

Merge `konsoletyper/teavm:master` at `4dfbc459aaf3209e397ad970458ef898a4baced8` into `ethanresnick/teavm:master` at `ce2e810bf84ffcf001b6550b2c159006c9a70953`. The common ancestor is `109cf91e74f0617fa779b9702377c16d7fc107a2`. There are 139 upstream-only commits and 165 fork-only commits. This is a real two-parent merge, preserving both histories. No upstream commits are intentionally omitted. The final source combines upstream changes with the conflict resolutions below.

The 139-commit inventory below defines the reviewed snapshot; later upstream commits are outside this PR. `git cherry` found no patch-identical commits in this range, but there are many semantic duplicates because the fork and upstream implementations evolved separately.

## Major upstream changes

### Wasm GC generation

`cffe04f82` replaces the AST/decompiler-based Wasm GC instruction generator with `WasmCodeGenerator`, which generates Wasm from TeaVM IR using flow reconstruction. The new pipeline then eliminates dead code/stores, optimizes locals and instruction sequences, and removes identity copies. Follow-ups fix comparison placement, exception-handler flow reconstruction, array construction optimization, signed-zero negation, and coroutine blocks that consume stack values. Debug disassembly is improved and emitted even for failed compilations.

The fork's JSPI mode, failed/async class initialization, negative array size checks, and direct multidimensional-array constructors were implemented in the old generator. They are ported to the replacement. The deleted `WasmGCInstructionGenerationVisitor` is not kept alongside the new architecture.

The coroutine splitter must preserve the distinction between an exit from an original conditional arm and the synthetic branch that selects the else arm. Both the optimized and general splitting paths previously redirected original then-arm exits to an inner block, allowing execution to fall through into else. Map original arm exits to the outer wrapper; in the general path, target the inner wrapper directly for the synthetic else-selection branch. Condition reconstruction and local-variable coalescing remain enabled for coroutine functions.

The fork's monitor/reference-queue regressions first exposed this defect. Three standalone Java regressions in `CoroutineConditionalTest` also reproduce it on unmodified upstream, without JSPI or fork class-library changes: a suspendable then-arm exit returns 3 instead of 2, its nested form returns 4 instead of 2, and a value-producing nested conditional returns 13 instead of 12. All three pass with the targeted fix. Restoring only the general splitting path's old behavior makes the value-producing regression fail again, establishing coverage of both paths. The standalone fix and regressions are also proposed upstream in [PR #1386](https://github.com/konsoletyper/teavm/pull/1386).

### SSA and optimizer invariants

`5b6241731` defines ordered inputs for exception-handler phis. The compiler must represent the value visible at each possible throw point, including the entry value before a definition. This avoids ambiguity when a protected block supplies several versions of one variable to a handler. `bb93a2b3c`, `d3da31677`, and `38de33b19` update splitting, optimizations and backend transformations to preserve the invariant. `8b8658b82` adds an opt-in IR verifier, enabled by CI.

This overlaps the fork's handler-phi fixes and exception-aware dominator walk. The coordinated upstream implementations of `PhiUpdater`, `DominatorWalker`, `AsyncMethodFinder` family traversal and nullness phi deduplication replace the corresponding earlier fork algorithms. The fork's C unmanaged-method suspension filter is reapplied. Existing fork regression cases are retained; the expected handler-phi listing is updated to entry-value-first ordering.

Other upstream compiler work includes checked integer division/remainder operations, range analysis for eliminating redundant checks, null/bounds exception propagation, more precise coroutine-restored types, dependency-analysis memory reductions, ancestor-set hierarchy lookup, assertion stripping, and explicit opt-in for async support.

### Class library and runtime

- Strings/Unicode: `String.lines`, whitespace-correct `isBlank`, `Character.toString(int)`, unpaired-surrogate offset handling, UTF-8 properties bundles, and `SuppressWarnings.value`.
- Collections/streams: sorted/sequenced unmodifiable wrappers, empty immutable-map lookup fixes, null-return handling in concurrent-map computation, and corrected `distinct` state across advances.
- I/O/formatting: unsigned single-byte file reads, file skipping, console writes/flushes, Formatter `%n`/`%%` argument handling and HALF_UP `%f` rounding.
- Threads/references: correct timed/interrupted waits, linked monitor wait queues, waking queued entrants, weak-reference registry cleanup and reference-queue removal, and spare-monitor reuse on C.
- Numerics: specified bounded Random sequence, floating-point constants, backend-native `Math.log10`.
- Runtime: `System.exit`/`Runtime.exit` plus exit-aware asynchronous callbacks, rooted C allocations/check exceptions, and fiber internal suspension handling.

### Other upstream work

The bootstrap compiler and test platform compile TeaVM to Wasm GC, including extension/SPI bridges, resource/archive support, source maps and debug tooling. JSO gains fetch bindings, a SharedArrayBuffer length constructor, Navigator user-agent access, null `@JSByRef` support and wrapper/type fixes. Build tooling gains local properties, isolated-project compatibility, Java 25 main entry points, optional JVM test runs, compiler heap measurements/JFR hooks, and Checkstyle 14.4.0. The project version becomes `0.18.0-SNAPSHOT`.

## Conflicts and duplicated fork work

| Area and upstream commits | Existing fork work | Integration decision |
| --- | --- | --- |
| New Wasm GC generator (`cffe04f82` and follow-ups) | JSPI, class initializer failure/waiting, missing-class bottom types, negative array sizes, direct multidimensional allocation | Adopt upstream generator; port fork hooks and checks. Carry the leaf type through the constructor API, preserve missing-class joins and upstream externref joins. |
| SSA/exception phis (`4b3c3676a`, `06770c556`, `4d2726b03`, `5b6241731`, `f4fce5c68`) | `588a94dd0` handler versions and fork nullness deduplication | Adopt upstream ordered-phi implementation and verifier; keep fork tests with updated expected ordering. |
| Exception-aware dominators (`327b8a73c`, `3246819a7`) | `982f3cecd` protected-block facts leaking into handlers | Adopt upstream CFG-2 dominator traversal; retain fork handler/null/bounds/class-init regressions. |
| Checked division (`e54eb98c3`, `cb1f6e098`, `2b290ab64`) | Fork PR #114, helper-call divisor checks and preservation of unused throwing results | Adopt upstream checked operations/range analysis and overflow-safe C helpers. Replace the signed helper pass; retain explicit helper checks for unsigned intrinsic calls, which upstream checked binary operations do not cover. Remove the old pre-inlining helper pass. Keep fork implicit-exception preservation modes and upstream's conservative boolean API. Keep unused potentially throwing divisions through inlining until the later upstream check pass, and propagate arithmetic exception types into class inference. |
| Async discovery (`4b90bf2c9`, `80d262305`) | Fork PR #7 and #10 monitor and inherited-interface fixes | Use upstream discovery/family algorithm; preserve `canSuspend` filter so unmanaged C runtime code cannot become suspendable. |
| Fiber internals (`fa09927ea`, `30631ce5a`, `80152835b`) | Fork suspend ordering, growing object stack, JSPI | Use upstream detached-fiber callback state for ordinary coroutines and preserve a distinct JSPI suspension/completion path. Preserve clearing consumed outcomes. |
| Monitor waits (`6731eafeb`, `52a1b5bf6`, `99bfa1b5b`, `ff58324e5`, `4dfbc459a`) | Fork PR #18/#19, completed interrupt-handler cleanup, monotonic deadlines | Adopt linked wait lists and spare-monitor reuse; retain monotonic timer objects, nanosecond rounding, handler cleanup and monitor reacquisition on interruption. Unlink timed/interrupted waiters immediately. |
| JS timer plumbing (`ff58324e5`, exit support `28c2c13c5`) | Fork `Platform.scheduleAfter/scheduleAt` and deadline rearming | Retain fork schedule API and restore its dependency wiring, which upstream removed after switching its callers. Add upstream exit guards to retained timer callbacks. |
| Weak references (`8cd97e4f2`, `534dd7a0f`, `677cf931f`) | Fork PR #29/#30, enqueue/clear/null semantics, interruptible queue waits | Integrate upstream registry plumbing and generated `reportNext`/`supplyImpl` split; preserve weak held references, null referents, enqueue semantics and monotonic remove timeouts. Preserve actual reference subclasses in queue delivery, and keep both regression suites. |
| Dependency transitions (`9089fe100`, `bbc601ccb`, `00c0c9b6d`) | `3d56cda38` delivering all merged-domain source types | Use upstream no-argument domain merge and pending-domain improvements; preserve fork implicit exception and class-init analysis. |
| Floating-point JS grouping (`7248c2d7d`) | Fork grouping fix and `FloatingPointGroupingTest` | Keep fork parenthesization, which already preserves floating-point grouping; combine upstream and fork regressions. |
| Console output (`f29a88992`) | Fork console byte-write/flush fix, PR #12 | Merge shared implementation; keep callback captured before clearing, with both regression suites. |
| `String.lines` (`dde52b305`) | `732175e53` lines, indent and transform | Keep one `lines()` method and fork's shared line-boundary helpers used by `indent`; retain upstream tests and whitespace changes. |
| `Random.nextInt(bound)` (`5e8f489fa`) | `c6993e5cc` specified bounded sequences | Keep one fork bounded implementation, already equivalent to the specified algorithm, together with nextBoolean/nextBytes corrections. |
| Sorted collection wrappers (`60628f095`) | Fork PR #76, sorted/navigable/live/null-safe wrappers and entry protections | Adopt upstream collection/set/map/sorted factories and sequenced-map factory, retaining fork navigable and empty-view support. Preserve the sorted `SortedMap.reversed()` view behavior in the new upstream sorted-map wrapper. Keep both regression suites. |
| Concurrent map computations (`7883fc417`) | `2f87947fb` broader concurrent-map contract fixes | Preserve fork direct-entry access, null checks, iterator/entry behavior and removal on null computation; combine tests and remove duplicated null-return guard. |
| `Math.log10` (`091b7a61a`) | Fork PR #55/#126 accurate fdlibm math | Keep fork fdlibm path on C/Wasm GC and its imported implementation on other targets; accept upstream surrounding backend changes. |
| File skip/arrays (`aee7f3771`, `4bdf81d8d`) | Fork RandomAccessFile lifecycle/access-mode fixes and negative-size validation | Preserve fork accessor operation tracking while incorporating skip/rooting changes. Retain unchecked recursive allocation after initial dimension validation and add upstream write barrier. |
| Wasm debug/heap (`ab0eb85c8`) | Fork PR #25 debug frame endings, failed memory growth handling | Use upstream renderer `finish()`; retain fork memory.grow failure result and oversized-allocation checks. |
| Async opt-in (`3d8ed0974`) | Fork tests were written when async was enabled by default | Adopt upstream default; annotate fork-only tests and added test methods with `@AsyncEnabled` so their existing behavior is explicit. |
| Shared test resources (`d4ca79c28`, SPI/bootstrap changes) | Fork PR #161 excludes host JDK bytecode | Use shared resource provider and preserve host-module exclusion and SPI-transformed class source. |
| CI (`dab3f6ce6`, `d4bac254a`) | Fork PR #164 C shards, optimized tests, timing artifacts | Retain shards/optimized runs/artifacts and add upstream JVM-run suppression and IR verification. Timeout and JVM-enabled property constants both remain. |

Git reported 64 conflicting paths. Some semantic duplicates above (`TString`, `TRandom`, `TCollections`, `PlatformGenerator`, and duplicate exception helpers) merged textually and were caught during review/build validation; they also required reconciliation.

## Compatibility considerations

Async support is now explicitly opt-in through the compiler API/tooling. Consumers moving to this revision must enable it for threading/async programs. This PR preserves that upstream default. It does not update the Kafka repository's pinned TeaVM revision, Maven version or shared-memory patch; that migration needs its own consumer validation.

Wasm generation, SSA handling and coroutine internals have changed substantially. Targeted tests and IR verification are essential; performance and full Kafka acceptance should be assessed before adopting this compiler in the application. The new bootstrap platform is included but is not a replacement for exercising the normal backends.

## Validation

Completed locally with JDK 25. The core suite and both style tasks passed; all reported skips are test-declared skips, with no failures or errors.

| Check | JUnit cases | Passed | Skipped | Result |
| --- | ---: | ---: | ---: | --- |
| Core compiler suite | 569 | 567 | 2 | Pass |
| Targeted JVM/JS/Wasm GC, including JSPI | 536 | 534 | 2 | Pass |
| Targeted native C on macOS | 101 | 100 | 1 | Pass |
| Repository-wide checkstyleMain | — | — | — | Pass |
| Repository-wide checkstyleTest | — | — | — | Pass |
| git diff --cached --check | — | — | — | Pass |

Backend tests ran with optimized and unoptimized configurations, and `teavm.tests.checkIr=true`. JS minification was disabled for this targeted run. Browser execution used the existing headless Chromium installation. Native C used the repository's `compile-c-macos-fast.sh` and disabled the debugger wrapper.

The JS/Wasm group covers BufferedWriter, CoroutineConditionalTest, AsyncTest, VMTest, DivisionByZero, IntegerDivision, implicit exception handlers, JSPI compilation/VM/thread behavior, monitors and reference queues, collections/concurrent maps/blocking queues, console output, RandomAccessFile, floating-point grouping, Math, Random, line/indent/blank-string behavior and resource bundles. The C group covers arithmetic, Math, Random, BufferedWriter, RandomAccessFile and monitor entry/reentrancy/wait-notify cases.

The browser suite was rerun after restoring both optimizations; it includes all 62 StringTest cases as well as the three new coroutine cases. The targeted coroutine fix was separately validated on upstream `4dfbc459a`: all three new regressions fail before the fix and pass afterward, together with 18 existing `AsyncTest` cases. The upstream core suite passed 534 cases with two skips; repository-wide main/test style checks passed. The fork's focused async, JSPI, monitor, and reference-queue group passed 172 cases with one skip. These checks used optimized/unoptimized browser compilation and IR verification.

The full repository test suite across every backend, bootstrap self-hosting execution, Linux/GCC CI, performance benchmarks and Kafka application acceptance were not run. The normal optimizer pipeline is restored; no performance benchmark was run.


## Detailed comparison of materially different overlapping fixes

The comparisons below use the pre-merge fork tip `ce2e810bf` and upstream tip `4dfbc459a`. They distinguish a different implementation of the same fix from unrelated improvements in a file that happened to conflict. The merge choice is explicit for each case.

### 1. Values visible to exception-handler phis

**Issue:** a handler must see whichever version of a variable existed at the instruction that threw, including a parameter or the value on entry to the protected block.

**Fork:** [`588a94dd0`](https://github.com/ethanresnick/teavm/commit/588a94dd0) extends `PhiUpdater` to retain the versions assigned while renaming a block (`definedVersions`) and supply them to handler phis. It fixes missing values without defining a compiler-wide ordering contract for several inputs from the same protected block.

**Upstream:** [`06770c556`](https://github.com/konsoletyper/teavm/commit/06770c556) addresses the same omission; [`5b6241731`](https://github.com/konsoletyper/teavm/commit/5b6241731) then makes the solution an IR invariant. `ExceptionPhi` records, per-source watch lists, and `startExceptionEdges()` track handler inputs as definitions occur. Entry values come first; subsequent inputs follow assignment order. Existing handler phis are rebuilt as well as synthesized ones. Block splitting, optimizers and backends are updated to preserve that ordering, with an optional verifier checking it.

**Comparison/merge:** the fork repairs completeness locally; upstream additionally makes the throw-point relationship explicit and maintainable across transformations. Adopt upstream's coordinated implementation and keep the fork regressions, updating the expected phi order.

### 2. Optimizer facts leaking from protected code into exception paths

**Issue:** a null check, bounds check or class initialization in a protected block may not have run when control reaches a handler. Treating the whole block as a dominator can incorrectly remove checks after the exception.

**Fork:** [`982f3cecd`](https://github.com/ethanresnick/teavm/commit/982f3cecd) computes dominance on a graph with separate entry/body nodes, then compresses it back into a tree of original basic blocks. It searches for the nearest dominating body and maintains multiple traversal roots when no such body exists. Normal CFG/context information remains separate.

**Upstream:** [`327b8a73c`](https://github.com/konsoletyper/teavm/commit/327b8a73c) walks the two-node graph directly. Filtering occurs at an entry node; visit/endVisit scope is attached to the body node. It also changes traversal order and exposes a single-normal-predecessor query that distinguishes exception edges and parallel branch targets.

**Comparison/merge:** both use the same sound model, but upstream avoids the compressed tree and special root bookkeeping. Adopt upstream's direct walk, retaining the fork's handler/null/bounds/class-initialization tests.

### 3. Exceptions inserted after dependency analysis

**Issue:** later-generated checks can throw exceptions that no explicit instruction in the original program appears to create. Missing those exception types can make an optimizer specialize a handler to the wrong subclass or eliminate valid behavior.

**Fork:** the PR #98 work (`c77fde80d`) introduces a reusable `ImplicitExceptions` instruction reader and explicit propagation into dependency analysis and `ClassInference`. It covers null dereferences, array bounds, casts, negative array lengths and class-initializer failures; later fork work tracks some local SSA facts to reduce impossible exceptions.

**Upstream:** [`94c548228`](https://github.com/konsoletyper/teavm/commit/94c548228), [`cb1f6e098`](https://github.com/konsoletyper/teavm/commit/cb1f6e098) and the bounds changes use target-configured `nullChecked`, `arrayBoundsChecked` and `divisionChecked` flags. Individual instruction visitors send the corresponding exception to the active handler consumer; each transformation contributes its required dependencies.

**Comparison/merge:** upstream organizes propagation around which checks the target will insert; the fork has broader shared coverage, including its separate class-inference and initializer-failure logic. Keep upstream's flag/check integration and the fork's broader analysis. Extend the latter to arithmetic exceptions now that signed checks are inserted later; the fork's subclass-handler regression caught that integration requirement.

### 4. Integer division by zero and unused throwing results

**Issue:** integer division/remainder must throw a Java `ArithmeticException`, even when its result is discarded; backend traps or C undefined behavior are insufficient.

**Fork:** PR #114 (`a70ea7818`) inserts `DivisionCheck.checkDivisor` calls. The pass recognizes signed binary operations and unsigned `Integer`/`Long` intrinsics, skips known nonzero constants, and shares checks within a basic block. C inlines the helper through an intrinsic. Checks were inserted before inlining, so later unused-value elimination could safely discard checked arithmetic. The fork's three preservation modes (`REMOVE`, `KEEP_UNCHECKED`, `KEEP_ALL`) also protect other implicitly throwing instructions.

**Upstream:** [`e54eb98c3`](https://github.com/konsoletyper/teavm/commit/e54eb98c3) introduces first-class checked division/remainder IR operations. `DivisionCheckInsertion` computes integer ranges through definitions, branches and phis, retaining checked operations unless safety is proved. C's checked arithmetic also handles signed MIN_VALUE / -1 without overflow/traps. Unused-value analysis has a conservative boolean mode and recognizes checked operations. The signed check pass runs after inlining.

**Comparison/merge:** upstream represents the exception in the IR and has a richer proof system; the fork's approach is simpler and also covers unsigned intrinsic calls. Adopt upstream signed operations/range analysis, preserve explicit unsigned checks and the fork's broader preservation modes, and keep potentially throwing signed divisions through inlining until the new pass runs. Dropping the fork's pre-inlining helpers without this adjustment caused the discarded-result tests to fail.

### 5. Inherited implementations of async interface methods

**Issue:** `Child extends Parent implements Interface` may inherit the suspending implementation from `Parent`, even though `Parent` does not implement that interface. Calls through the interface must still be treated as potentially suspending.

**Fork:** [`f66c906d5`](https://github.com/ethanresnick/teavm/commit/f66c906d5) scans concrete classes, their interfaces and interface ancestors, resolves each implementation, and adds interface methods when the resolved implementation is async. It also uses a Boolean-valued family cache and a general method-family pass.

**Upstream:** [`80d262305`](https://github.com/konsoletyper/teavm/commit/80d262305) constructs a reverse superclass/interface graph, starts from known async methods, and walks inheriting descendants with `addImplicitOverrides()`. It stops that inherited path where a subclass declares its own method, tracks visited method/class combinations, and accumulates the family in a set.

**Comparison/merge:** the fork searches the whole class/interface surface and resolves implementations; upstream follows inheritance outward from actual async seeds, avoiding unrelated work. Adopt upstream's traversal. Reapply the fork's separate `canSuspend` filter, needed to exclude unmanaged C runtime methods; that filter is not an alternative implementation of the interface fix.

### 6. Fiber internals recursively entering suspendable code

**Issue:** stack growth or the body of an async callback may call code classified as suspendable while the fiber itself is saving/restoring state.

**Fork:** [`b8068a09c`](https://github.com/ethanresnick/teavm/commit/b8068a09c) avoids `Arrays.copyOf(Object[])` by allocating an object array and copying it with a loop. The suspend-order fix keeps the fiber in the running state while the async body runs, registers the pending call first, and detects synchronous completion through the current fiber/pending-resume flag. JSPI later adds its own stack-parking path.

**Upstream:** [`fa09927ea`](https://github.com/konsoletyper/teavm/commit/fa09927ea) detaches the current fiber while internal operations run. It can use `Arrays.copyOf` for every stack type. Callback-local states distinguish a synchronously resolved value/exception from a suspended callback; only an unresolved call is linked into the pending-call list. The current fiber is restored after the async body finishes.

**Comparison/merge:** the fork avoids one problematic operation and keeps the enclosing fiber runnable; upstream provides a general non-fiber context plus an explicit callback state machine. Adopt upstream's ordinary-coroutine path and keep a distinct JSPI path. Preserve the fork's placement of `Thread.currentThread()` after the resuming-state fast path, so class initialization cannot run while the fiber is still marked resuming.

### 7. Expired/interrupted monitor waits and their scheduling

**Issue:** expired waiters can absorb a later `notify`, stay retained by a monitor, or resume without reacquiring the lock.

**Fork:** PR #18/#19 (`c2901e524` and related merges) keeps an `ArrayDeque` of listeners with an expired marker. A timed-out/interrupted waiter removes itself from the queue, and notification skips expired entries. Arbitrary removal scans the queue. Scheduling branches between `Platform` on JS and `EventQueue` on Wasm/C. The fork additionally uses monotonic timer objects with cancellation and early-fire rearming, rounds fractional-millisecond waits, and clears completed interrupt handlers.

**Upstream:** [`6731eafeb`](https://github.com/konsoletyper/teavm/commit/6731eafeb), [`52a1b5bf6`](https://github.com/konsoletyper/teavm/commit/52a1b5bf6) and [`ff58324e5`](https://github.com/konsoletyper/teavm/commit/ff58324e5) move to an intrusive doubly linked wait list. `queued` and a one-shot `performed` state replace expired-entry filtering; removal happens immediately and in constant time. JS also uses `EventQueue`. Upstream's timer form still uses an integer timer ID and a wall-clock deadline.

**Comparison/merge:** upstream simplifies lifetime management and avoids linear queue removal; the fork has stronger clock/cancellation behavior and additional interrupt cleanup. Use the upstream list with fork timer objects and cleanup semantics. Retain the necessary JS timer API and dependency wiring; copying upstream's removal of that wiring broke the retained fork timers.

### 8. Waking a Wasm GC thread blocked in ReferenceQueue.remove

**Issue:** a collected reference must first satisfy a queued `remove()` waiter, rather than always going into the poll queue.

**Fork:** [`4e2d7a18b`](https://github.com/ethanresnick/teavm/commit/4e2d7a18b) places `if (reportNext(reference)) return` directly in the Java template for `supply`. A stub `reportNext` stands for the real queue method. The transformer excludes that stub from copying and remaps invocation references from the template class to the destination queue class.

**Upstream:** [`677cf931f`](https://github.com/konsoletyper/teavm/commit/677cf931f) leaves `supply` native in the template, moves queue insertion into `supplyImpl`, then builds the real `supply` IR with `ProgramEmitter`: call the actual queue's `reportNext`, return if delivered, otherwise call `supplyImpl`.

**Comparison/merge:** runtime behavior is equivalent; upstream avoids the placeholder method and generates the cross-class call explicitly. Adopt the upstream construction. Retain fork timeout/interrupt semantics and propagate actual reference subclasses into `supply`, rather than only the base WeakReference type, so overridden methods remain reachable.

### 9. Unmodifiable sorted/sequenced collection views

**Issue:** an unmodifiable wrapper must protect nested views, iterator removal, entry mutation, default mutators and reversed views while reflecting changes to the backing collection.

**Fork:** [`db186767f`](https://github.com/ethanresnick/teavm/commit/db186767f) uses anonymous wrappers and nested classes inside `TCollections`. It adds sorted/navigable factories and empty views, wraps entries and iterators, and preserves sorted/navigable reversed views where the backing map supports them. Its general collection/map wrappers are less complete for mutation attempts that would otherwise do nothing.

**Upstream:** [`60628f095`](https://github.com/konsoletyper/teavm/commit/60628f095) introduces a reusable hierarchy (`TUnmodifiableCollection`, `TUnmodifiableMap`, sequenced/sorted variants, entry-set wrappers). It overrides mutating methods explicitly, including no-op cases, and caches consistent key/value/entry views. It adds `unmodifiableSequencedMap`, but not the fork's full navigable/empty-view surface. Its sorted-map wrapper inherits a reversed view that loses the sorted wrapper type.

**Comparison/merge:** upstream has a more complete reusable mutation barrier; the fork has broader API coverage and stronger sorted-view preservation. Adopt upstream's common/sorted wrappers and sequenced-map factory, retain the fork's navigable and empty views, and preserve sortedness when reversing the upstream sorted wrapper. Both test suites were necessary: upstream tests caught no-op mutation holes in ours; fork tests caught loss of the sorted reversed view in theirs.

### 10. Math.log10 correctness and backend strategy

**Issue:** the former logarithm formula was inaccurate and backend implementations must satisfy Java's numeric contract.

**Fork:** PR #55/#126 (`f3486b49f`, `d3df8411d`) implements fdlibm-based StrictMath and selects the compiled fdlibm `log10` on Wasm GC and C. On JS it calls the host function. The fork also fixes other transcendental functions; those broader fixes are not duplicated by upstream's log10 commit.

**Upstream:** [`091b7a61a`](https://github.com/konsoletyper/teavm/commit/091b7a61a) makes `log10` a native operation on every backend, using the JS/Math import and the C platform's native math implementation.

**Comparison/merge:** upstream has a smaller implementation and delegates numeric behavior to the host. The fork deliberately chooses compiled fdlibm for the Wasm import cost and for tighter C accuracy; its source documents the glibc accuracy issue. Keep the fork strategy. No new performance benchmark was run during this merge, so the existing performance rationale is not presented as a fresh measurement.

### 11. Random.nextInt(bound) sequence compatibility

**Issue:** Random's bounded output must follow the specified algorithm and seeded sequence, rather than an arbitrary RandomGenerator default.

**Fork:** [`c6993e5cc`](https://github.com/ethanresnick/teavm/commit/c6993e5cc) takes `next(31)`, uses a shift based on `Integer.numberOfLeadingZeros(bound)` for power-of-two bounds, and uses rejection sampling otherwise. It also supplies the specified nextBoolean/nextBytes algorithms.

**Upstream:** [`5e8f489fa`](https://github.com/konsoletyper/teavm/commit/5e8f489fa) uses the textbook long multiplication and right shift for power-of-two bounds, with the same rejection-sampling condition for other bounds.

**Comparison/merge:** these produce the same bounded sequence and consume the same random bits. The meaningful implementation difference is avoiding long arithmetic in the fork's power-of-two path. Keep the fork method and its broader sequence fixes; do not add a duplicate nextInt overload.

### 12. Console flushing across partial UTF-8 sequences

**Issue:** console byte writes must be buffered/decoded correctly, and flushing must not corrupt subsequent output.

**Fork:** PR #12 (`a830720cb`) fixes byte writes and buffered output, but its flush invokes decoding with `endOfInput=true` and resets the decoder afterwards.

**Upstream:** [`f29a88992`](https://github.com/konsoletyper/teavm/commit/f29a88992) shares the same basic byte-output repair but uses `endOfInput=false` for flush and does not reset the decoder. An incomplete multibyte sequence stays pending until later bytes arrive.

**Comparison/merge:** most of the repair is equivalent; this flush policy is a material behavioral difference. Keep upstream's streaming UTF-8 behavior, while retaining the fork's harmless capture-before-clear callback form and both regression suites.

### 13. Preserving floating-point grouping in generated JavaScript

**Issue:** removing parentheses from `a + (b + c)` or `a * (b * c)` can change floating-point rounding.

**Fork:** the renderer conservatively parenthesizes right-side arithmetic of equal precedence. Only logical/bitwise associative operations get the looser rule. It also keeps `%`, `*` and `/` at the same JavaScript precedence.

**Upstream:** [`7248c2d7d`](https://github.com/konsoletyper/teavm/commit/7248c2d7d) passes a type-derived associativity flag to the renderer: integer ADD/MULTIPLY may omit those parentheses; floating-point ADD/MULTIPLY must keep them.

**Comparison/merge:** both preserve floating-point evaluation order. Upstream is more selective and can emit fewer integer parentheses; the fork is more conservative and also carries the modulo-precedence correction. Retain the fork's grouping behavior, with the upstream checked-arithmetic rendering additions and both test suites.

### Overlaps that do not have substantially different implementations

- **WeakReference registry-cell release:** upstream `8cd97e4f2` and fork `6eb6de84c` share the same weak-held-reference/unregister/secondary-finalizer design. Later upstream registry plumbing and the fork's null/enqueue extensions need integration, but the cleanup fix itself is not a competing algorithm.
- **Empty immutable-map lookups:** upstream `802ce1b94` and fork `bc2b8c68a` use the same zero-length guard before computing a hash slot. The fork additionally fixes Integer.MIN_VALUE hash indexing in maps and sets; that is extra coverage, not a different empty-map fix.
- **Merged dependency domains:** upstream `9089fe100` and fork `3d56cda38` both deliver the complete source type set rather than just the current batch. Upstream removes the unused batch argument and adds separate pending/empty-domain improvements. The central fix is the same.
- **Repeated reflection metadata generation:** upstream `c8adc921d` and the fork's PR #3 remove the same `metadataGenerator.generate()` call from the ClassInfo reflection intrinsic and remove its now-unused dependency. This is substantively the same fix.
- **Waking all monitor entrants:** upstream `99bfa1b5b` and the fork's PR #1 retain the entering queue until it is empty, rather than dropping it after one removal. This is the same correction.
- **Null-return ConcurrentHashMap computations:** upstream `7883fc417` and fork `2f87947fb` both avoid storing a null result and remove the entry when computeIfPresent returns null. Getter/setter versus direct-field access is a small adaptation. The fork's much broader weakly-consistent traversal, resize forwarding, hashing and null-contract changes are additional work, not an alternative algorithm for this fix.
- **String.lines:** upstream `dde52b305` and fork `732175e53` both lazily scan CR/LF/CRLF boundaries. The fork extracts shared line-end/next-line helpers for indent; upstream inlines that scanner. This is primarily code organization, not a different line-splitting algorithm.
- **Wasm debug-frame endings:** upstream `ab0eb85c8` and fork PR #25 both advance the debug position and close outstanding method frames at function end. Upstream calls it `finish`; the old fork helper is removed as redundant.
- **C file skipping:** this was a file-level conflict, not two fixes for the same bug. Upstream `aee7f3771` changes skip from absolute to relative seeking; the fork's RandomAccessFile changes track read/write transitions and synchronization. Keep the relative seek and reset the fork's operation state after it succeeds.

The new Wasm GC generator, bootstrap compiler, UTF-8 properties bundles, String.isBlank correction, signed-zero negation and several other upstream changes are additions relative to this fork. They are covered in the upstream inventory, but are not counted as alternative implementations of an issue the fork had already fixed.

## Status of upstream issues listed in #1341

Checked directly against GitHub on 2026-11-22. These are tracker states, not a claim that every original reproducer was rerun unchanged.

| Issue | Status | Subject |
| --- | --- | --- |
| [#1258](https://github.com/konsoletyper/teavm/issues/1258) | Closed — completed | Fiber stack-growth recursion |
| [#1248](https://github.com/konsoletyper/teavm/issues/1248) | **Open** | Scalar replacement leaves uses of a removed phi |
| [#1324](https://github.com/konsoletyper/teavm/issues/1324) | Closed — completed | Calls on caught implicit exceptions trap |
| [#1249](https://github.com/konsoletyper/teavm/issues/1249) | Closed — completed | Integer division Java semantics |
| [#1218](https://github.com/konsoletyper/teavm/issues/1218) | **Open** | Missing catch classes produce invalid Wasm GC |
| [#1335](https://github.com/konsoletyper/teavm/issues/1335) | Closed — completed | Console byte writes and flushing |
| [#1328](https://github.com/konsoletyper/teavm/issues/1328) | Closed — completed | ConcurrentHashMap null computation results |
| [#1325](https://github.com/konsoletyper/teavm/issues/1325) | Closed — completed | Seeded bounded Random sequences |
| [#1319](https://github.com/konsoletyper/teavm/issues/1319) | Closed — completed | Math.log10 powers of ten |
| [#606](https://github.com/konsoletyper/teavm/issues/606) | **Open** | RandomAccessFile rw opening/read behavior |
| [#735](https://github.com/konsoletyper/teavm/issues/735) | **Open** | Double parse/format round-trip gaps |

The umbrella [#1341](https://github.com/konsoletyper/teavm/issues/1341) is itself still open. Its original note already characterizes the fork's #735 coverage as partial; do not describe that issue as resolved by this sync.

## Complete upstream commit inventory

Chronological order, excluding commits already reachable from the fork:

- [`eee579033`](https://github.com/konsoletyper/teavm/commit/eee5790337be9259e4dce871f32a904625b83f21) wasm gc: internal tool to compare module sizes, produced by tests
- [`e90c9b01b`](https://github.com/konsoletyper/teavm/commit/e90c9b01b973f06483f22e481db0336f42a0f2a1) compiler benchmarks: add gradle setting to run benchmarks under jfr
- [`54225f871`](https://github.com/konsoletyper/teavm/commit/54225f8712a6893f857e2944ccf09af1b6c8efc1) Don't insert null checks when SwitchBootstraps.typeSwitch receives a primitive type
- [`956cec1b7`](https://github.com/konsoletyper/teavm/commit/956cec1b7f04ad0e313871472f5ef4af0de4af5b) Fix some cases when synthesized IR instructions are not getting any location information
- [`4c9a470b8`](https://github.com/konsoletyper/teavm/commit/4c9a470b8c06bbc26784a89f505b04186f96d638) Specialize iterator over phi inputs, since it's a frequent operation
- [`4b3c3676a`](https://github.com/konsoletyper/teavm/commit/4b3c3676a6f59914df5facef17821d41f67bcef9) Mark parameter variables as defined when updating phis in SSA form
- [`bca0d0f92`](https://github.com/konsoletyper/teavm/commit/bca0d0f923cbaa3d4c76edb9277dbff0b68cb823) junit: when async test crashes and never reaches completion, report it to the server rather than hanging forever
- [`ec91cf0e5`](https://github.com/konsoletyper/teavm/commit/ec91cf0e5c73ad17bdfd998357fdd46af607bd08) In coroutine transformer, properly support case when possibly optimizable block appears at the location where stack is not empty
- [`ec6b03793`](https://github.com/konsoletyper/teavm/commit/ec6b037937ba56918cb33d7f96ca12622707b092) wasm gc: improvements and bugfixes to disassembler, add disassembler that prints Wasm IR as text
- [`7e3bb0f34`](https://github.com/konsoletyper/teavm/commit/7e3bb0f34c150d5b1c5935c440bda0d76590391e) junit: write test disassembly even when the test itself fails or does not compile successfully (but still produces a module)
- [`cffe04f82`](https://github.com/konsoletyper/teavm/commit/cffe04f82f1afeeb146ac8e74f8b127f1989cec1) wasm gc: new code generator
- [`f4f79f4c8`](https://github.com/konsoletyper/teavm/commit/f4f79f4c8d2a8dbca0c46c22ecdbbe6ab450d99d) gradle: fix compatibility with isolated projects (#1340)
- [`6696c33e9`](https://github.com/konsoletyper/teavm/commit/6696c33e9e12540231e10e5a2b66d0740b66a0a5) wasm gc: when multiple conditions use result of previous comparison, bring the comparison right before the branch
- [`6badc688d`](https://github.com/konsoletyper/teavm/commit/6badc688d3a5cfa542483a2df3fc506616ec9936) classlib: fix returning bytes with high bit set in FileInputStream.read
- [`60628f095`](https://github.com/konsoletyper/teavm/commit/60628f095960f70c90b5f767bcdd15f9528e5690) classlib: support some Collections.unmodifiable*Set/Map methods
- [`f30b56fea`](https://github.com/konsoletyper/teavm/commit/f30b56fea57e4bd86f01210ee73c7db87e0b8b0b) c: fix support of Object's monitor field in GC
- [`28c2c13c5`](https://github.com/konsoletyper/teavm/commit/28c2c13c587d2f79533f9d16f865a2a9ff5157da) classlib: support System.exit and Runtime.exit
- [`c8adc921d`](https://github.com/konsoletyper/teavm/commit/c8adc921d93d96dda708c1d336861b167a91b090) wasm gc: remove leftover that used to generate reflection metadata on each ClassInfo.reflection call (up 6 in total)
- [`9089fe100`](https://github.com/konsoletyper/teavm/commit/9089fe1005f4c1a5087c2f46062754f22801a706) core: deliver all source types when a transition merges domains
- [`678c1b9ce`](https://github.com/konsoletyper/teavm/commit/678c1b9ce6e17cfdf475f6a89a1545d335064f7a) Fix bugs in bound checks insertion
- [`a8a836140`](https://github.com/konsoletyper/teavm/commit/a8a836140098ca684863685973e01e5df626a3aa) Commit forgotten files
- [`da907f428`](https://github.com/konsoletyper/teavm/commit/da907f42871842a1f6412cb79149818c02caf7fc) classlib: add missing `value` field to SuppressWarnings
- [`5c1172cc7`](https://github.com/konsoletyper/teavm/commit/5c1172cc7974e25ccb6b4499ebccc44bf855e76d) Move bound check insertion back to post-inlining phase and include AIIOBE path explicitly
- [`6c9bf8582`](https://github.com/konsoletyper/teavm/commit/6c9bf8582eba3e36e53b45184cb29a0bb929582e) Add bootstrap compiler and wire them in tests
- [`b21acedc5`](https://github.com/konsoletyper/teavm/commit/b21acedc5ca83ee1491bfa48bdfc975eb99e3a4d) bootstrap compiler: fix support for dependency plugins
- [`7ad886665`](https://github.com/konsoletyper/teavm/commit/7ad8866654522a50ba76c03a65930ed8d597175a) Add internal tool to compare Wasm tests
- [`466a74494`](https://github.com/konsoletyper/teavm/commit/466a744940df29fa3586bca499ce73b411c8b8d1) bootstrap compiler: enable more stuff from JCL plugin
- [`1be00d28a`](https://github.com/konsoletyper/teavm/commit/1be00d28aa4e74ac6f0420fddf1f089874056407) bootstrap compiler: add option for source maps, enable source maps and debug info in test compiler
- [`f671f14c4`](https://github.com/konsoletyper/teavm/commit/f671f14c4ffeb8a8088bdf2b0786536c209f3b20) Fix places which produce indeterministic output due to dependency on iteration order over hash tables
- [`896c98a93`](https://github.com/konsoletyper/teavm/commit/896c98a93522a89bd37227fc896d382f89e6b546) bootstrap compiler: setup class source in the same way the normal compiler does
- [`7746d7ab0`](https://github.com/konsoletyper/teavm/commit/7746d7ab0d15f8a702d77ec776873843caf14e77) bootstrap compiler: add more forgotten dependency plugins
- [`1674e86bd`](https://github.com/konsoletyper/teavm/commit/1674e86bdf60d9f62a49b47a35befb635faf5c6a) bootstrap compiler: keep root folder in CDLR during repack
- [`0bb438fe5`](https://github.com/konsoletyper/teavm/commit/0bb438fe5164d6cfdcfa0ee9a51dba5ec9bd646c) bootstrap compiler: skip tests that can't pass due to lack of locales other than english
- [`a553b7a2b`](https://github.com/konsoletyper/teavm/commit/a553b7a2b1f445a1fc4c1157b86d495091c1b84a) bootstrap compiler: make difference between wasm-gc and bootstrap-wasm-gc platform in SkipPlatform/OnlyPlatform annotations
- [`ab0eb85c8`](https://github.com/konsoletyper/teavm/commit/ab0eb85c8cac27f15ff06e11de9705b741b47ca7) wasm gc: flush debug info after rendering function body
- [`ece677902`](https://github.com/konsoletyper/teavm/commit/ece677902147d1f0a8cf16c87e37ac160d4bb13e) bootstrap compiler: use built-in compiler to generate bootstrapped compiler, include debug info and deobfuscator
- [`f44643254`](https://github.com/konsoletyper/teavm/commit/f44643254caac7ecd6adcc88e042a46ed06b16d3) bootstrap compiler: rewrite resource bundle to make it work under bootstrap
- [`70595ed68`](https://github.com/konsoletyper/teavm/commit/70595ed682cf81179a0e1a3f0de54ffb451a06e3) bootstrap compiler: suppress some not passing tests
- [`88a0fba28`](https://github.com/konsoletyper/teavm/commit/88a0fba28169b3b1d944088d1a32d516a317ebe7) bootstrap compiler: remove unnecessary build steps, copied from teavm-javac
- [`6df39ceae`](https://github.com/konsoletyper/teavm/commit/6df39ceae1897750419abfa2ff8905be8a6145f7) bootstrap compiler: make ArchiveReader public
- [`91c09a6c2`](https://github.com/konsoletyper/teavm/commit/91c09a6c2d22e3b221e4686288f64249267b84b1) classlib: fix String.isBlank to accept all whitespace, not only spaces (#1343)
- [`f2d198375`](https://github.com/konsoletyper/teavm/commit/f2d19837547a1a450964086603c30c6d3ed9d5ba) classlib: implement Character.toString(int) (#1334)
- [`802ce1b94`](https://github.com/konsoletyper/teavm/commit/802ce1b942599c58bae18e2a57ce6eecb6694a1a) classlib: fix lookups in empty immutable maps (#1333)
- [`f54efa769`](https://github.com/konsoletyper/teavm/commit/f54efa769746986deee43b0770b919ea124a2e5b) classlib: fix distinct state across successive stream advances (#1342)
- [`95ff4ea45`](https://github.com/konsoletyper/teavm/commit/95ff4ea458f892b891c87bfb0ac833004664f51c) bootstrap compiler: possibly fix a bug in archive builder in windows
- [`ac067a845`](https://github.com/konsoletyper/teavm/commit/ac067a845039c4a84fc71ae820ea71ced16a8d29) classlib: handle %n and %% without consuming Formatter arguments (#1337)
- [`f29a88992`](https://github.com/konsoletyper/teavm/commit/f29a88992eb3174da5cd77da6628f8bcfafa8090) classlib: fix byte writes and flushing in JS/Wasm GC console streams (#1336)
- [`6731eafeb`](https://github.com/konsoletyper/teavm/commit/6731eafeb1df90cb37ed994404dc88f6e9f061fc) classlib: fix wait() after a timeout or an interrupt
- [`52a1b5bf6`](https://github.com/konsoletyper/teavm/commit/52a1b5bf67a01c7932880dbfbc4271fc26fcd0b9) classlib: replace ArrayQueue of notify listeners in object monitor with explicit double-linked list; get rid of expired flag - always remove expired node from the list
- [`ff58324e5`](https://github.com/konsoletyper/teavm/commit/ff58324e56e03d8ff948fa7846402eee924d6818) js: use EventQueue routines within Object's monitor routines instead of Platform.
- [`99bfa1b5b`](https://github.com/konsoletyper/teavm/commit/99bfa1b5b2f56feb03e71a7ee2a446eaf09de443) classlib: wake every thread queued on a monitor
- [`8cd97e4f2`](https://github.com/konsoletyper/teavm/commit/8cd97e4f25ea3c0d011214a4af3114e69dd6c55e) core: release a WeakReference's FinalizationRegistry cell on JS and Wasm GC
- [`06770c556`](https://github.com/konsoletyper/teavm/commit/06770c5565d037423cde0c7e172a6c0774a7f2c6) core: give exception handler phis every version of a variable
- [`0c63ee100`](https://github.com/konsoletyper/teavm/commit/0c63ee1008dea30eccf9be9b1ddeb2215da74ff6) Skip test that does not pass in C BE due to bug in coroutine transformation
- [`e6596d37f`](https://github.com/konsoletyper/teavm/commit/e6596d37f1140c19a3011a6305977732d149d98e) Add compiler benchmark that compiles bootstrap compiler
- [`50afb1c7f`](https://github.com/konsoletyper/teavm/commit/50afb1c7f450f9429ae4d4fecf6cc92f7071f3d4) jso: fix unwrapping of JS -> Java objects when the original JS was a wrapper around Java, passed to JS previously
- [`2aee18dee`](https://github.com/konsoletyper/teavm/commit/2aee18deea0292f392e14834abeb3865e0c218c1) bootstrap compiler: implement SPI as a separate compilation step
- [`66283e4d9`](https://github.com/konsoletyper/teavm/commit/66283e4d95f19471c4978fcb674515c6b14f67a2) gradle: enable parallel sync for IDEA
- [`f00db0213`](https://github.com/konsoletyper/teavm/commit/f00db0213b59d95a1b24974e391e652a2027c222) Commit file, updated by newer version of IDEA
- [`f4e90ab6a`](https://github.com/konsoletyper/teavm/commit/f4e90ab6ab7d252c70cad10fe94346b3e9d4accc) Fix incorrect support (lack of cache invalidation) of entries to exception handlers in repeated field read elimination
- [`87532ab05`](https://github.com/konsoletyper/teavm/commit/87532ab051dac0b34c9d9089af42cfc177aeafa2) classlib: fix constant values in Float and Double
- [`18495abae`](https://github.com/konsoletyper/teavm/commit/18495abae034c0df7284165df89f1f2ebb366c54) wasm: add tests to confirm that new wasm codegen is not affected by issue #1321 (negation works properly for zero)
- [`091b7a61a`](https://github.com/konsoletyper/teavm/commit/091b7a61ae94a44a4a03fc78847ae299ef5345cb) classlib: use native functions per backend to implement Math.log10
- [`a7837f3cf`](https://github.com/konsoletyper/teavm/commit/a7837f3cf9c0a8af91613c360202874e1f794560) Implement assertion removal
- [`3d8ed0974`](https://github.com/konsoletyper/teavm/commit/3d8ed09744f90355eb4abcc27085dc28c435ab1a) Disable asyncs by default. Add a compiler option to enable the explicitly
- [`069769633`](https://github.com/konsoletyper/teavm/commit/0697696331f5353a80d4459a7ff1303e2a18e64c) gradle: add support for assertionRemoved compiler option
- [`366b87b7e`](https://github.com/konsoletyper/teavm/commit/366b87b7edbb7734a088fe4edad002896e122d78) bootstrap compiler: add support for assertionsRemoved flag
- [`327b8a73c`](https://github.com/konsoletyper/teavm/commit/327b8a73cc6d48349401cbd627ac4d9a6c982713) Update DominatorWalker to use CFG-2 version (where 2 graph nodes correspond each basic block) to properly handle exception handlers in optimizations, that rely on it, as null check insertion.
- [`3246819a7`](https://github.com/konsoletyper/teavm/commit/3246819a73b30fab3c2f7377b8c5cd730693b7c3) Cover recent changes in DominatorWalker with tests for cases that are fixed by this improvement
- [`ddf307cf9`](https://github.com/konsoletyper/teavm/commit/ddf307cf9cddf35fd5d1a0c58db8bfbf72772045) Fix expected error wording in async diagnostics test
- [`da91cf789`](https://github.com/konsoletyper/teavm/commit/da91cf78976b9cb126fd677268f0c6a3dfda472a) Fix case in bound check insertion
- [`dde52b305`](https://github.com/konsoletyper/teavm/commit/dde52b305b62934134bc82ea1f47d92efd742396) classlib: add String.lines (#1345)
- [`e54eb98c3`](https://github.com/konsoletyper/teavm/commit/e54eb98c3a5fcec27a148fe0ed8bebd19a96528b) Add checked division and modulo instructions
- [`80152835b`](https://github.com/konsoletyper/teavm/commit/80152835bdfe6aaef5b8774d2ca56b1b592284ad) c: preserve precise types of values restored from fiber stack
- [`d676d4ce5`](https://github.com/konsoletyper/teavm/commit/d676d4ce59ebdea0ed8b3e0400b714be90f6957d) Update benchmarks
- [`8de893b67`](https://github.com/konsoletyper/teavm/commit/8de893b67be3c6995efb46ce7dabcdb642c69659) Run optimizations, that use extended-SSA form with nullness fact, in one shot
- [`12fe3d62b`](https://github.com/konsoletyper/teavm/commit/12fe3d62bfdfadad6897db762765e71007961178) Use PhiUpdater to apply the debug names to SSA form
- [`b02fc1ba1`](https://github.com/konsoletyper/teavm/commit/b02fc1ba1a058d583fc087a849846bc3317ac79a) Add warning comment to TeaVMPlugin
- [`a1c01ca77`](https://github.com/konsoletyper/teavm/commit/a1c01ca77ed21bde2ac5f580e92144e47ebf5f97) Wire local properties to tests module
- [`a8072520e`](https://github.com/konsoletyper/teavm/commit/a8072520e39f655d2d1b3a8dde63634fc5338a40) spi: add extension point to transform resources and byte code
- [`593770eb1`](https://github.com/konsoletyper/teavm/commit/593770eb1ee5c08b0b74fe789f7bac446418f9c8) bootstrap compiler: add support for ResourceTransformer and SubstitutionPolicy extension points
- [`94c548228`](https://github.com/konsoletyper/teavm/commit/94c5482280928ca4f5735fb76a057302875e3415) core: let the points-to analysis see exceptions of null checks inserted later (#1349)
- [`f76a1210b`](https://github.com/konsoletyper/teavm/commit/f76a1210bb1400663612d3d76e33e292a2e2c690) Fix passing options to C compiler in tests module with the new properties system
- [`cb1f6e098`](https://github.com/konsoletyper/teavm/commit/cb1f6e0980157b2cbbce58e88fe0de74cc035029) core: let the dependency analysis see exceptions of division checks inserted later
- [`00c0c9b6d`](https://github.com/konsoletyper/teavm/commit/00c0c9b6d8d8ddc230d97d396cc0b4a2367b6464) Reduce memory consumption by pending types in transitions during points-to analysis
- [`2fd13b1da`](https://github.com/konsoletyper/teavm/commit/2fd13b1da777edb5b24cff807c04d2bb7fa1bf94) compiler benchmarks: add an option to measure peak allocated heap
- [`7c35d97ec`](https://github.com/konsoletyper/teavm/commit/7c35d97ecfd2006a329132b93545db566f2f0765) Update ClassHierarchy logic to keep only set of class's ancestors instead of storing pair-wise relation
- [`bbc601ccb`](https://github.com/konsoletyper/teavm/commit/bbc601ccb78fdb4e66a231cdbe02121ca1409337) Fix points-to analysis not merging certain nodes into domains when they are initially empty
- [`c6a6cea07`](https://github.com/konsoletyper/teavm/commit/c6a6cea070c85342adcfe4cce8d5be012e689f9d) Fix performance issue in PostProcessingClassHolderSource for lazy pipeline
- [`64c9e4bf7`](https://github.com/konsoletyper/teavm/commit/64c9e4bf7c3348d7db97465ba5f73fb1696ba255) Add missing locations to instructions, synthesized by BoundCheckInsertion
- [`534dd7a0f`](https://github.com/konsoletyper/teavm/commit/534dd7a0f8907a7f811bb7c0e62b3209f2e925d0) Fix WeakReference bugs in JS and Wasm, make tests more reliable
- [`3515313a3`](https://github.com/konsoletyper/teavm/commit/3515313a3a04783537596a3b0be362c2537ba822) wasm: fix support of loops without guarding block in CoroutineTransformation
- [`677cf931f`](https://github.com/konsoletyper/teavm/commit/677cf931fd1cef8c357b664cf8f62b485ffd119f) wasm: implement ReferenceQueue.remove
- [`f4ee4817d`](https://github.com/konsoletyper/teavm/commit/f4ee4817d469b180da73a1887849af4b3a82add3) Bump project version
- [`2b290ab64`](https://github.com/konsoletyper/teavm/commit/2b290ab64de3618b7bdeb72dba1361c19f33c5db) Guard division of MIN_VALUE / -1 in compiler code
- [`0c4e98bfa`](https://github.com/konsoletyper/teavm/commit/0c4e98bfa10f1f89e79d42a444ab8a2825eb1309) bootstrap compiler: compile it with strict mode off and assertion stripped
- [`c2b634b26`](https://github.com/konsoletyper/teavm/commit/c2b634b260aba72b1450889e8aab6c18469f8f6a) jso: pass null through @JSByRef arrays (#1350)
- [`aee7f3771`](https://github.com/konsoletyper/teavm/commit/aee7f377174f77b8e122c443775ab824afe1b0c9) Fix FileInputStream.skip and RandomAccessFile.skipBytes on C and for zero-length skips (#1348)
- [`4bdf81d8d`](https://github.com/konsoletyper/teavm/commit/4bdf81d8d786a36e81a341355788c3f6a14571a1) c: keep arrays rooted while runtime code fills them (#1347)
- [`d018d88e5`](https://github.com/konsoletyper/teavm/commit/d018d88e530ae01640f422c238e17b837a19c857) classlib: use HALF_UP rounding for Formatter %f conversions (#1338)
- [`bf5cb278f`](https://github.com/konsoletyper/teavm/commit/bf5cb278fd71df75c8b6425841f1f40c966611a7) wasm gc: preserve externref when merging interop types (#1332)
- [`1f04fbde0`](https://github.com/konsoletyper/teavm/commit/1f04fbde032f2038e44bdeff0c7833635eddcb4f) Fix typos in documentation (#1361)
- [`fa09927ea`](https://github.com/konsoletyper/teavm/commit/fa09927eaa405a6e78d3ed75ba9be5f8f58787fb) Run fiber's internal routines with fiber detached, so that they never try to suspend/resume themselves
- [`f52730cc2`](https://github.com/konsoletyper/teavm/commit/f52730cc2b1d9cfff3087e8ff64cc7fa42abefb1) jso: Add bindings to fetch (#1357)
- [`30631ce5a`](https://github.com/konsoletyper/teavm/commit/30631ce5aebbbf8106527881ffb6643b30b297c5) c: use protected coroutine suspend/resume checks
- [`1111ed866`](https://github.com/konsoletyper/teavm/commit/1111ed8666ce83b8ffc98e0ed901fb50fc3bfe5c) jso: add length constructor to SharedArrayBuffer class (#1367)
- [`e467cda68`](https://github.com/konsoletyper/teavm/commit/e467cda68e32ef6734e33cee844bb74f5638fe11) wasm gc: fix common try/catch size calculation in flow reconstructor
- [`f596e14b5`](https://github.com/konsoletyper/teavm/commit/f596e14b5cdf836505199ae4bb35ff79f42841ee) Remove apparently incorrect loop invariant motion for throwing instructions
- [`7883fc417`](https://github.com/konsoletyper/teavm/commit/7883fc417b19d848feb6b1a06eee753de56a4449) classlib: fix behaviour of ConcurrentHashMap.computeIfAbsent/computeIfPresent on null return values
- [`22d480516`](https://github.com/konsoletyper/teavm/commit/22d480516054a8663dbade744992645bfa9f47a1) Update dev publication script to take HEAD version instead of master
- [`5665364bd`](https://github.com/konsoletyper/teavm/commit/5665364bddcae3578472cbd9476e0fc4a662bfce) wasm gc: fix flow reconstruction for case when catch leads in a loop leads to a block outside the loop and in the same time this catch handler used before the loop
- [`cbe907af6`](https://github.com/konsoletyper/teavm/commit/cbe907af61ab9dbfe7d6bbf84d5e91637dac85af) classlib: fix Character.offsetByCodePoints case with unpaired surrogates
- [`5e8f489fa`](https://github.com/konsoletyper/teavm/commit/5e8f489fa49676ca166bd98625ac8a65dc44c883) classlib: implement Random.nextInt(bound) according to javadoc
- [`acf8807c0`](https://github.com/konsoletyper/teavm/commit/acf8807c0235a9dc6114e2322462436295e8e734) c: keep exceptions of failed checks rooted while they are constructed (#1359)
- [`4b90bf2c9`](https://github.com/konsoletyper/teavm/commit/4b90bf2c94ce5fc0a896ffae8f21c182e32bf355) core: detect suspendable monitors after async methods are collected
- [`4d2726b03`](https://github.com/konsoletyper/teavm/commit/4d2726b03e0ce10ecef2409045e2b23dd9458bbd) Don't pass pre-phi versions to exception handlers in PhiUpdater
- [`7248c2d7d`](https://github.com/konsoletyper/teavm/commit/7248c2d7d92d317b60bc9401536538ff9b7fb542) js: keep grouping of floating-point + and * in generated code (#1376)
- [`59b4bc046`](https://github.com/konsoletyper/teavm/commit/59b4bc0469f9d976e51d50918682a0e9f562bb43) jso: add getUserAgent method to Navigator class (#1379)
- [`5b6241731`](https://github.com/konsoletyper/teavm/commit/5b62417315b88db27f77e3618bc8cc06c909c3f9) Introduce new IR invariant. In strict SSA form we have shortening for exception handling edges. This produces phis with ambiguous inputs in one block. Now we fix this and bind order of phi inputs to order of instructions.
- [`bb93a2b3c`](https://github.com/konsoletyper/teavm/commit/bb93a2b3cad3ff8c461a330a638978f487568a8e) Enforce new IR invariant in BasicBlockSplitter
- [`d3da31677`](https://github.com/konsoletyper/teavm/commit/d3da3167719515d4d9852937c343a4ed0401e298) Enforce new IR invariant in optimizations
- [`38de33b19`](https://github.com/konsoletyper/teavm/commit/38de33b19e24ac0b2ea8e03a61c42753d2acbedf) Enforce new IR invariant in backend-specific transforms
- [`8b8658b82`](https://github.com/konsoletyper/teavm/commit/8b8658b82713fc55933a2cd0fb4991427f7c78fc) Add compiler opt-in checker that verifies IR invariants
- [`75a1f55f8`](https://github.com/konsoletyper/teavm/commit/75a1f55f8b1b7c9eb7a9b23933d5c3e86b1437ec) Unsuppress tests, failing in C backend
- [`f4fce5c68`](https://github.com/konsoletyper/teavm/commit/f4fce5c68463813fa12346a66c426ae172db3621) Remove duplicate phi inputs that NullnessInformationBuilder has left
- [`d4bac254a`](https://github.com/konsoletyper/teavm/commit/d4bac254a970026603407dd53bfe5bac65165ada) ci: enable IR verifier during tests
- [`d4ca79c28`](https://github.com/konsoletyper/teavm/commit/d4ca79c28ec104f502077038707bf52c8212e79f) junit: use shared resource provider across test compilations
- [`484b75680`](https://github.com/konsoletyper/teavm/commit/484b7568042661b5e02ccba5f111916738671c41) Fix test
- [`dab3f6ce6`](https://github.com/konsoletyper/teavm/commit/dab3f6ce6dfcff52f53ac2dd6859e629da6f672f) junit: add a flag to skip JVM runs, use it in CI for backends except Wasm
- [`67194b32a`](https://github.com/konsoletyper/teavm/commit/67194b32a62832aadbfa789312049a404af42b66) js: fix switch statements not being optimized
- [`248a4857c`](https://github.com/konsoletyper/teavm/commit/248a4857c3f718b3ada2debd7e3b7d375d29f6d4) classlib: read properties resource bundles as UTF-8 (#1381)
- [`f4f1fef93`](https://github.com/konsoletyper/teavm/commit/f4f1fef93cbbdeaeb14128ac0df95d885085d4c7) wasm gc: give a type to coroutine wrapper block of a conditional that consumes stack values (#1380)
- [`79a4a9541`](https://github.com/konsoletyper/teavm/commit/79a4a954147ae2c50db1a5ba2b642eaeb979bc84) wasm gc: fix bug in array construction optimizer
- [`5674f80ca`](https://github.com/konsoletyper/teavm/commit/5674f80cafd93beed77e2e39ea93a0c55fd5585f) jso: don't use JSWrapper.unwrap to convert Java to JS object when the source is proven to be pure Java object
- [`80d262305`](https://github.com/konsoletyper/teavm/commit/80d262305a22c71fe03bc2dd11655a09400cd406) Mark methods that take implementation from interfaces from superclass as async family (i.e. methods, calling to which can be suspending)
- [`64297c996`](https://github.com/konsoletyper/teavm/commit/64297c996f8120225aae622b94d937c043db1f02) Bump checkstyle to the most recent version
- [`2163fe02c`](https://github.com/konsoletyper/teavm/commit/2163fe02cd4bc3e31a512ddc0359c238f9618d04) fix(core): handle no-argument main entry points
- [`26b33bb84`](https://github.com/konsoletyper/teavm/commit/26b33bb84c6cec8bf60028e1dcc1179502fe4146) c: support Java 25 main entry points
- [`4dfbc459a`](https://github.com/konsoletyper/teavm/commit/4dfbc459aaf3209e397ad970458ef898a4baced8) c: reuse one spare monitor for uncontended locks

## Conflicting path inventory

- `.github/workflows/ci.yml`
- `classlib/src/main/java/org/teavm/classlib/impl/console/JsConsolePrintStream.java`
- `classlib/src/main/java/org/teavm/classlib/java/lang/TMath.java`
- `classlib/src/main/java/org/teavm/classlib/java/lang/TObject.java`
- `classlib/src/main/java/org/teavm/classlib/java/lang/TThread.java`
- `classlib/src/main/java/org/teavm/classlib/java/lang/ref/TReferenceQueue.java`
- `classlib/src/main/java/org/teavm/classlib/java/util/concurrent/TArrayBlockingQueue.java`
- `classlib/src/main/java/org/teavm/classlib/java/util/concurrent/TConcurrentHashMap.java`
- `classlib/src/main/java/org/teavm/classlib/java/util/stream/impl/TStringLinesStream.java`
- `classlib/src/main/resources/org/teavm/classlib/java/lang/ref/ReferenceQueue.js`
- `core/src/main/java/org/teavm/backend/c/CTarget.java`
- `core/src/main/java/org/teavm/backend/c/runtime/fs/CVirtualFileAccessor.java`
- `core/src/main/java/org/teavm/backend/javascript/JavaScriptTarget.java`
- `core/src/main/java/org/teavm/backend/javascript/intrinsics/ref/WeakReferenceDependencyListener.java`
- `core/src/main/java/org/teavm/backend/javascript/rendering/StatementRenderer.java`
- `core/src/main/java/org/teavm/backend/lowlevel/transform/CoroutineTransformation.java`
- `core/src/main/java/org/teavm/backend/wasm/WasmGCTarget.java`
- `core/src/main/java/org/teavm/backend/wasm/dependencies/WasmGCDependencies.java`
- `core/src/main/java/org/teavm/backend/wasm/generate/methods/WasmGCGenerationContext.java`
- `core/src/main/java/org/teavm/backend/wasm/generate/methods/WasmGCInstructionGenerationVisitor.java`
- `core/src/main/java/org/teavm/backend/wasm/generate/methods/WasmGCMethodGenerator.java`
- `core/src/main/java/org/teavm/backend/wasm/intrinsics/HeapIntrinsic.java`
- `core/src/main/java/org/teavm/backend/wasm/render/WasmBinaryRenderer.java`
- `core/src/main/java/org/teavm/backend/wasm/transformation/ReferenceQueueTemplate.java`
- `core/src/main/java/org/teavm/backend/wasm/transformation/ReferenceQueueTransformation.java`
- `core/src/main/java/org/teavm/backend/wasm/types/PreciseTypeInference.java`
- `core/src/main/java/org/teavm/backend/wasm/types/WasmGCVariableCategoryProvider.java`
- `core/src/main/java/org/teavm/dependency/DependencyAnalyzer.java`
- `core/src/main/java/org/teavm/dependency/DependencyGraphBuilder.java`
- `core/src/main/java/org/teavm/dependency/Transition.java`
- `core/src/main/java/org/teavm/model/analysis/NullnessInformation.java`
- `core/src/main/java/org/teavm/model/optimization/UnusedVariableElimination.java`
- `core/src/main/java/org/teavm/model/optimization/VariableEscapeAnalyzer.java`
- `core/src/main/java/org/teavm/model/transformation/DivisionCheckInsertion.java`
- `core/src/main/java/org/teavm/model/util/AsyncMethodFinder.java`
- `core/src/main/java/org/teavm/model/util/DominatorWalker.java`
- `core/src/main/java/org/teavm/model/util/PhiUpdater.java`
- `core/src/main/java/org/teavm/runtime/Allocator.java`
- `core/src/main/java/org/teavm/runtime/Fiber.java`
- `core/src/main/java/org/teavm/vm/TeaVM.java`
- `core/src/main/js/wasm-gc-runtime/runtime.ts`
- `core/src/main/resources/org/teavm/backend/c/exceptions.h`
- `core/src/main/resources/org/teavm/backend/c/references.c`
- `core/src/main/resources/org/teavm/backend/javascript/exception.js`
- `core/src/test/java/org/teavm/model/analysis/test/NullnessAnalysisTest.java`
- `core/src/test/java/org/teavm/model/optimization/test/UnusedVariableEliminationTest.java`
- `core/src/test/java/org/teavm/model/transformation/BoundCheckInsertionTest.java`
- `platform/src/main/java/org/teavm/platform/Platform.java`
- `platform/src/main/resources/org/teavm/platform/plugin/Platform.js`
- `tests/src/test/java/org/teavm/classlib/impl/console/JsConsolePrintStreamTest.java`
- `tests/src/test/java/org/teavm/classlib/java/io/RandomAccessFileTest.java`
- `tests/src/test/java/org/teavm/classlib/java/lang/ObjectTest.java`
- `tests/src/test/java/org/teavm/classlib/java/lang/StringTest.java`
- `tests/src/test/java/org/teavm/classlib/java/lang/ref/WeakReferenceTest.java`
- `tests/src/test/java/org/teavm/classlib/java/util/CollectionsTest.java`
- `tests/src/test/java/org/teavm/classlib/java/util/concurrent/ArrayBlockingQueueTest.java`
- `tests/src/test/java/org/teavm/classlib/java/util/concurrent/ConcurrentHashMapTest.java`
- `tests/src/test/java/org/teavm/vm/AsyncTest.java`
- `tests/src/test/java/org/teavm/vm/FloatingPointGroupingTest.java`
- `tests/src/test/java/org/teavm/vm/VMTest.java`
- `tools/junit/src/main/java/org/teavm/junit/PropertyNames.java`
- `tools/junit/src/main/java/org/teavm/junit/TeaVMTestExecutionSupport.java`
- `tools/junit/src/main/java/org/teavm/junit/TeaVMTestInfrastructure.java`
- `tools/junit/src/main/java/org/teavm/junit/TestPlatformSupport.java`
