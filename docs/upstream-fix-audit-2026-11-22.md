# Follow-up audit of incomplete upstream fixes

This audit revisits the 139 upstream commits in the integration review, through upstream `4dfbc459a`, and the fork regressions that were not included in the first targeted validation. Clean-upstream experiments use that exact production revision. The comparison branch is the sync PR at `84b557d03`.

The earlier inventory was incomplete. Beyond the subsequently documented remainder-precedence defect, this audit confirms four additional areas: monitor interruption/lifetime, reference-queue waiting, JSBody expression grouping, and concurrent-map null argument validation. These are correctness gaps remaining after the relevant upstream work; this does not mean the upstream commits introduced them or failed to fix their narrower reported symptoms.

## Corrected inventory

| Area | Remaining upstream gap | Sync treatment |
| --- | --- | --- |
| Checked arithmetic | Unsigned division/remainder intrinsics bypass signed check insertion. | Retain unsigned divisor checks. |
| Implicit exception analysis | Optimized class inference can lose check-generated exception types or the type flowing through a null check. | Retain the shared implicit-exception analysis and null-check value propagation. |
| Unmodifiable sorted maps | Reversal loses the sorted wrapper; the covariant `SortedMap.reversed(): SortedMap` API is also absent. | Preserve sorted wrapper type where the underlying reverse is sorted. This does not complete the missing covariant API. |
| Wasm GC coroutine splitting | Original conditional-arm exits can fall through into else after splitting. | Targeted fix and regressions, also submitted as upstream [#1386](https://github.com/konsoletyper/teavm/pull/1386). |
| JavaScript remainder precedence | `a % (b * c)` can become `a % b * c`. | Exact implementation and tests from upstream [#1387](https://github.com/konsoletyper/teavm/pull/1387). |
| Monitor waits — newly confirmed here | Pre-existing interrupts are ignored on entry; completed notified waits retain the lock through the thread. | Retain pre-interrupt checks and completed-handler cleanup. |
| ReferenceQueue.remove — newly confirmed here | Interruption, cleanup ownership, timeout validation, and large timeout handling remain incorrect. | Retain fork interruption and timer handling. |
| JSBody grouping — newly confirmed here | The separate JavaScript AST writer still reassociates addition/multiplication, including string concatenation. | Retain fork AstWriter grouping rules. This is separate from the StatementRenderer remainder correction. |
| ConcurrentHashMap null arguments — newly confirmed here | The repaired compute methods still accept some null keys/functions. | Retain the fork's argument validation. Null callback results are a different case and the upstream result-handling fixes pass. |

## Additional reproduced failures

The failures in this section reproduce in generated JavaScript on clean upstream. Failed methods stop before later backend/configuration runs, so they are not presented as independent Wasm GC reproductions.

### Monitor interruption and retained locks

Upstream `6731eafeb`, `52a1b5bf6`, `99bfa1b5b`, and `ff58324e5` repair wait-list removal, notification, and scheduling. Two further fork regressions fail:

- `ObjectTest.waitThrowsForEarlierInterrupt`: a thread interrupted before calling wait blocks instead of promptly throwing and clearing its interrupt status. The test's watchdog eventually interrupts it; the failure reports `wait() waited`.
- `ObjectTest.notifiedWaitDoesNotKeepItsLock`: an ended thread kept reachable by the test retains its former lock after a notified wait. `NotifyListener.run()` removes the listener from the monitor queue but leaves the listener installed as the thread's interrupt handler. That listener still references the lock and callback.

The earlier `endedWaitsDoNotKeepThreadsAlive` test passes on clean upstream. It asks whether a thread can be collected while its lock stays alive. The newly reproduced retention case asks whether the lock can be collected while the ended thread stays alive. Passing the former does not establish the latter.

### Reference-queue waiting

Upstream `677cf931f` implements queue removal and delivery to waiting consumers. The original validation used WeakReferenceTest but omitted the fork's separate ReferenceQueueTest. Six of its seven bounded/non-hanging cases fail:

| Test | Upstream result |
| --- | --- |
| `removeCanBeInterruptedAgain` | Interrupting the blocked remove produces `Another thread is running`. |
| `currentThreadAfterInterruptingRemove` | The same runtime error occurs when the waiter resumes synchronously inside interruption. |
| `timedOutRemoveDoesNotClearAnotherThreadsInterruptHandler` | After queue removal times out, interrupting a separate sleeping thread no longer wakes it. |
| `removeWithNegativeTimeoutThrows` | `remove(-1)` returns without throwing IllegalArgumentException. |
| `removeWithLongMaxValueTimeoutWaits` | The call ends immediately with null. |
| `removeWithTimeoutBeyondIntRangeWaits` | `remove(3_000_000_000L)` ends immediately with null. |

The ordinary `timedRemoveReturnsNull` case passes. A separate bounded reproducer sets the interrupt status before `remove(20)` and expects InterruptedException; upstream instead returns without throwing. The original unbounded pre-interrupt test blocked the first audit run. Only that run's test worker was stopped, and the bounded reproducer was used for the completed run.

Source review identifies the corresponding problems: direct callback resumption from `interrupted()`, cleanup against `TThread.currentThread()` rather than the waiting thread, missing timeout validation, and unprotected deadline/host-timer arithmetic. Java's [ReferenceQueue contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ref/ReferenceQueue.html#remove(long)) specifies interruption and negative-timeout behavior.

### JSBody expression grouping

Upstream `7248c2d7d` fixes the Java-expression StatementRenderer. JSBody scripts use a separate AstWriter, whose right-operand rules still omit required parentheses for addition and multiplication. Three minimal scripts fail:

| Script and arguments | Required | Upstream |
| --- | --- | --- |
| `a + (b + c)`, with `"x", 1, 2` | `"x3"` | `"x12"` |
| `a + (b + c)`, with `1, 1e16, -1e16` | `1` | `0` |
| `a * (b * c)`, with `1e308, 10, 0.1` | `1e308` | Infinity |

This is independent of the `%` precedence defect. The sync uses the exact AstWriter implementation and renderer tests proposed in upstream [#1388](https://github.com/konsoletyper/teavm/pull/1388); adopting upstream PR #1387's StatementRenderer does not replace this separate fix. Alignment with #1388 was reviewed by diff only; no additional local builds or tests were run.

### Concurrent-map null arguments

Upstream `7883fc417` correctly repairs null *results* from computeIfAbsent/computeIfPresent. Fork regressions for absent insertion, present removal, and compute's null results pass on clean upstream.

Minimal argument-validation regressions nevertheless show that these calls fail to throw NullPointerException:

- `computeIfAbsent(null, k -> "value")`;
- `computeIfPresent(null, (k, v) -> "value")`;
- `computeIfPresent("absent", null)`.

These are contract holes in the same methods, not a failure of the null-result behavior the upstream commit repaired. The larger fork null-argument test cannot be used unchanged on upstream because it also references a missing KeySetView API; the minimal tests avoid that unrelated API gap. See the [ConcurrentHashMap API contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html).

## Findings kept separate

- The bounded `Random.nextInt` regression passes. A broader Random subclass test fails: nextBoolean requests 32 bits through the RandomGenerator default rather than the specified Random default's one-bit draw, and range methods differ in their OpenJDK sequences/call patterns. The range-method sequence differences are not all Java-contract violations. This is recorded as adjacent compatibility behavior, not evidence that upstream's bounded-int fix is incomplete.
- The weak-reference subtype test passes on clean upstream; its integration failure is not reclassified as a standalone upstream bug.
- Negative array checks, missing catch-class handling, broader RandomAccessFile behavior, and decimal round-trip gaps remain separate existing fork work or missing upstream features.
- Timer dependency wiring, the initial unused signed-division failure, the initial missing collection factory, and the multidimensional constructor mismatch were merge mistakes. The local-coalescer assertion followed the temporary coroutine workaround. They are not counted as additional upstream fixes that failed.

## Verification and limits

- Rechecked the full upstream commit inventory and the recorded merge failures, then expanded execution to previously omitted fork regressions and minimal reproductions.
- Upstream's 32 bounds-insertion/nullness cases, including the fork's handler cases, pass. The inspected SSA/dominator work is not implicated by these additional failures.
- Nineteen Formatter cases, fourteen stream-distinct cases, the fork's line-splitting examples/property test, and the two log10 tests pass on clean upstream. The selected null-result computation tests and bounded Random integer test also pass.
- All 17 focused cases pass on the sync branch: eight ReferenceQueue cases, two monitor cases, and seven minimal cases for JSBody grouping, null compute arguments, and pre-interrupted timed removal. These runs use JS and Wasm GC where applicable, optimized/unoptimized compilation, and IR verification.
- Only test fixtures were copied or added to the isolated upstream checkout; production code remained `4dfbc459a`. Some copied test classes were made separately compiled so unrelated missing APIs could not prevent the selected cases from running.
- This is not exhaustive execution of every test/backend. Native C, bootstrap self-hosting, and full application acceptance were not rerun in this follow-up. No absence-of-bugs claim is made for the unexecuted paths.

The additional reproduced cases are already covered by retained fork production fixes. This follow-up changes the review record, not the sync's production implementation.

## Reference-queue cleanup and scope clarification (2026-11-23)

A source-only follow-up confirms that upstream's generated `supply` invokes the real queue's `reportNext` and then `supplyImpl`; the Java template no longer invokes a template method. Remove the unused template `reportNext` stub, copy exclusion and invocation-remapping adapter. This does not remove the real waiter-delivery hook. No additional builds or tests were run.

The remaining enqueue-related differences need narrower descriptions: the C call to `teavm_reference_clear` is behavior-equivalent to the prior assignment with the current helper, and JS/Wasm manual enqueue still does not deliver the reference to the queue (the corresponding test explicitly skips those backends). Calling the overridable Java `clear()` from `enqueue()` also differs from the documented direct-clearing semantics, so the fork's implementation should not be submitted unchanged as a complete enqueue fix. The actual-subtype dependency propagation is retained, but the existing subtype regression passed on clean upstream; that change needs an isolated failing reproducer before being presented as a standalone upstream bug fix.

## Interrupt-handler dispatch regression (2026-11-23)

Upstream [#1391](https://github.com/konsoletyper/teavm/pull/1391) submits the fork's existing detach-before-dispatch ordering in `Thread.interrupt`. Its new `ThreadInterruptTest.secondInterruptReachesReplacementHandler` directly exercises the implementation's callback lifecycle on the JVM: the first handler installs a replacement, and the second interrupt must reach that replacement. With only the test on upstream `4dfbc459a`, it fails (expected one invocation, got zero); it passes with the fix. The identical test is added to the fork and passes there. The focused upstream classlib style checks pass. This is a deterministic internal regression, not an end-to-end claim about a particular blocking operation.

## Full CI integration follow-up (2026-11-23)

The first full CI run at `d85b188a8` found merge gaps outside the earlier targeted checks. These are integration issues, not additional independently established upstream bugs:

- Direct compiler fixtures in AsyncMethodFinderTest, HostModuleIsolationTest and ReflectiveInitializationCompilationTest had not opted into upstream's now-disabled-by-default async support. Enable it explicitly for fixtures whose programs intentionally suspend, retaining the classpath and async-family assertions.
- The new Wasm generator cast a missing-class array's bottom reference type to a composite wrapper while unwrapping/loading an element. Obtain the physical array layout from the class-info provider, consistently with allocation of unwrapped locals. Keep the bottom reference representation and ordinary null-check/trap semantics. The existing ArraysOfMissing regression exposed this in FULL optimization.
- Upstream's shared Fiber roots bring object-stack growth into every Wasm compilation. Generic Arrays.copyOf then linked reflective Array.newInstance and invalidated all six standalone array-constructor regression preconditions. Grow the known Object[] stack with a direct allocation and System.arraycopy instead. Preserve the original no-reflective-allocation assertions, and add a 64-value/null identity check across stack growth and popping.
- The merge replaced five existing native-C exclusions with AsyncEnabled rather than retaining both annotations: ClassTest.instanceCreatedThroughReflectionAsync, MethodTest.asyncReflection/asyncReflectionWithSeveralParameters, ConstructorTest.asyncConstructor, and VMTest.asyncWait. Restore the fork's exclusions for its pre-existing unsupported async reflection/class-initializer cases. No new native-C async-reflection support is claimed, and the diagnostic tests for unsupported reflective async initialization remain active.

The five focused compiler-test classes pass locally (67 methods), with JS/Wasm selected where applicable; relevant core/test style checks pass. Their prior failures were reproduced before the fixes. The subsequent native-C annotation restoration was reviewed by source comparison with fork master and is awaiting remote CI, following the owner's instruction to resume source/remote-CI-only work. One original C shard also failed before testing because Maven Central returned HTTP 429. The next complete CI run must pass before merge; the original failed run is not sufficient validation.

## Native-C nested-loop integration follow-up (2026-11-23)

The next full run (`45164724725`) reached the C shard previously blocked by Maven's rate limit. Eleven retained nested-loop coroutine regressions failed because the merge had lost the independent five-line phi-placement fix from fork commit `8bf2e0eb3` (#108). Upstream's ordered exception-handler phi changes do not replace this fix: an incoming value feeding a phi of a different variable does not merge that value for later uses. Restore the guard while retaining upstream's exception-edge algorithm. Existing depth-two/depth-three loops, downward and upward loops, long/double values, conditions and try/catch cases provide regression coverage. Validation is through remote CI only at the owner's request.
