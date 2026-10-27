/*
 *  Copyright 2026 Alexey Andreev.
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
package org.teavm.classlib.java.util.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import org.junit.jupiter.api.Test;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
public class LinkedBlockingDequeTest {
    // LinkedBlockingDeque.addAll: "Attempts to addAll of a deque to itself result in IllegalArgumentException."
    // Throws: "IllegalArgumentException - if the collection is this deque"
    @Test
    public void addAllOfItselfThrows() {
        for (int size = 0; size <= 2; ++size) {
            LinkedBlockingDeque<Integer> deque = new LinkedBlockingDeque<>();
            List<Integer> expected = new ArrayList<>();
            for (int i = 0; i < size; ++i) {
                deque.add(i);
                expected.add(i);
            }
            assertThrows(IllegalArgumentException.class, () -> deque.addAll(deque));
            assertEquals(expected, new ArrayList<>(deque));
        }
    }

    @Test
    public void addAllOfCopyAppends() {
        LinkedBlockingDeque<Integer> deque = new LinkedBlockingDeque<>(List.of(1, 2));
        assertTrue(deque.addAll(new ArrayList<>(deque)));
        assertEquals(List.of(1, 2, 1, 2), new ArrayList<>(deque));
    }
}
