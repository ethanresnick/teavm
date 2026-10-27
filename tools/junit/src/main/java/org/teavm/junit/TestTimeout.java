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
package org.teavm.junit;

/**
 * Per-run timeout for code running in a browser or as a native process, so that one hanging test fails
 * instead of blocking the rest of the test suite. Set in seconds with the {@code teavm.junit.timeout}
 * system property; zero or a negative value disables it.
 */
final class TestTimeout {
    private static final long DEFAULT_SECONDS = 300;

    private TestTimeout() {
    }

    static long millis() {
        var value = System.getProperty(PropertyNames.TIMEOUT);
        long seconds = DEFAULT_SECONDS;
        if (value != null && !value.isBlank()) {
            try {
                seconds = Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid value of " + PropertyNames.TIMEOUT
                        + " (expected a number of seconds): " + value);
            }
        }
        return seconds > 0 ? seconds * 1000 : 0;
    }

    static RuntimeException exception(TestRun run, String reason) {
        return new RuntimeException("Test " + run.getMethod().getDeclaringClass().getName() + "." + run.getName()
                + " timed out on " + run.getGroup().getKind() + ": " + reason + ". The limit is set in seconds "
                + "by the " + PropertyNames.TIMEOUT + " system property.");
    }
}
