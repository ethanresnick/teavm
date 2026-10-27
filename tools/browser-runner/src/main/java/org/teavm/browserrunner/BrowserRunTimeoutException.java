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
package org.teavm.browserrunner;

/**
 * Thrown by {@link BrowserRunner#runTest(BrowserRunDescriptor, BrowserOutputListener)} when a run takes
 * longer than the timeout set by {@link BrowserRunner#setTestTimeout(long)}. By the time it's thrown,
 * the runner has already restarted the browser, so it's ready for the next run.
 */
public class BrowserRunTimeoutException extends RuntimeException {
    public BrowserRunTimeoutException(String message) {
        super(message);
    }
}
