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
package org.teavm.classlib.java.util;

import java.util.ArrayList;
import java.util.List;

public final class TStringJoiner {
    private final String delimiter;
    private final String prefix;
    private final String suffix;
    // Null until setEmptyValue is called; then toString uses it while no element exists.
    private String emptyValue;
    private final List<String> elements = new ArrayList<>();

    public TStringJoiner(CharSequence delimiter) {
        this(delimiter, "", "");
    }

    public TStringJoiner(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        if (delimiter == null || prefix == null || suffix == null) {
            throw new NullPointerException();
        }
        this.delimiter = delimiter.toString();
        this.prefix = prefix.toString();
        this.suffix = suffix.toString();
    }

    public TStringJoiner setEmptyValue(CharSequence emptyValue) {
        if (emptyValue == null) {
            throw new NullPointerException();
        }
        this.emptyValue = emptyValue.toString();
        return this;
    }

    public TStringJoiner add(CharSequence newElement) {
        elements.add(String.valueOf(newElement));
        return this;
    }

    public TStringJoiner merge(TStringJoiner other) {
        if (other == null) {
            throw new NullPointerException();
        }
        if (!other.elements.isEmpty()) {
            // Join first, so a merge of this joiner into itself reads a stable snapshot.
            elements.add(other.joinElements());
        }
        return this;
    }

    public int length() {
        return toString().length();
    }

    @Override
    public String toString() {
        if (elements.isEmpty()) {
            return emptyValue != null ? emptyValue : prefix + suffix;
        }
        return prefix + joinElements() + suffix;
    }

    private String joinElements() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(elements.get(i));
        }
        return sb.toString();
    }
}
