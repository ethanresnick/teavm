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

public final class TStringJoiner {
    private final String delimiter;
    private final String prefix;
    private final String suffix;
    // Null until setEmptyValue is called; then toString uses it while no element exists.
    private String emptyValue;
    // The added elements separated by the delimiter, without prefix and suffix.
    // Null while no element exists, so that an added empty string still counts as an element.
    private StringBuilder joined;

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
        // String.valueOf gives "null" for null, and a String argument itself, which StringBuilder copies fastest.
        appendElement(String.valueOf(newElement));
        return this;
    }

    public TStringJoiner merge(TStringJoiner other) {
        if (other == null) {
            throw new NullPointerException();
        }
        if (other.joined != null) {
            // Take the string first, so a merge of this joiner into itself reads a stable snapshot.
            appendElement(other.joined.toString());
        }
        return this;
    }

    private void appendElement(String element) {
        if (joined == null) {
            joined = new StringBuilder();
        } else {
            joined.append(delimiter);
        }
        joined.append(element);
    }

    public int length() {
        if (joined == null) {
            return emptyValue != null ? emptyValue.length() : prefix.length() + suffix.length();
        }
        return prefix.length() + joined.length() + suffix.length();
    }

    @Override
    public String toString() {
        if (joined == null) {
            return emptyValue != null ? emptyValue : prefix + suffix;
        }
        if (prefix.isEmpty() && suffix.isEmpty()) {
            return joined.toString();
        }
        return new StringBuilder(length()).append(prefix).append(joined).append(suffix).toString();
    }
}
