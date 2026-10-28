/*
 *  Copyright 2026 Ethan Resnick.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib;

/** Runtime helpers that the ported jsr166 classes call in place of JDK classes that TeaVM lacks. */
public final class EmulSupport {
    private EmulSupport() {
    }

    /** Same contract as {@code ForkJoinPool.ManagedBlocker}. */
    public interface ManagedBlocker {
        boolean block() throws InterruptedException;

        boolean isReleasable();
    }

    /**
     * Replaces {@code ForkJoinPool.managedBlock}. Outside a ForkJoinPool the JDK blocks the caller until the
     * blocker is releasable, and this runtime has no ForkJoinPool.
     */
    public static void managedBlock(ManagedBlocker blocker) throws InterruptedException {
        while (!blocker.isReleasable()) {
            if (blocker.block()) {
                return;
            }
        }
    }

    /**
     * Replaces {@code Reference.reachabilityFence} in the ported classes. The ports call it only so that a finalizer
     * cannot run while a call is in progress, and TeaVM does not run finalizers, so it does nothing. It is not a
     * general reachability fence.
     */
    public static void reachabilityFence(Object ref) {
    }
}
