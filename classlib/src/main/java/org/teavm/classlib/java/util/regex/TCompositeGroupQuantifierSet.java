/*
 *  Copyright 2014 Alexey Andreev.
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
/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * @author Nikolay A. Kuznetsov
 */
package org.teavm.classlib.java.util.regex;

/**
 * Composite (i.e. {n,m}) quantifier node for groups ("(X){n,m}")
 *
 * @author Nikolay A. Kuznetsov
 */
class TCompositeGroupQuantifierSet extends TGroupQuantifierSet {

    protected TQuantifier quantifier;

    int setCounter;

    public TCompositeGroupQuantifierSet(TQuantifier quant, TAbstractSet innerSet, TAbstractSet next, int type,
            int setCounter) {
        super(innerSet, next, type);
        this.quantifier = quant;
        this.setCounter = setCounter;
    }

    /*
     * The enter counter holds the number of iterations done so far. Every call restores the value it was entered
     * with before it returns, and the counter is 0 while the rest of the pattern runs, so that a group quantified
     * again from outside, as in ((ab){2})+, counts its iterations anew.
     */
    @Override
    public int matches(int stringIndex, CharSequence testString, TMatchResultImpl matchResult) {
        int enterCounter = matchResult.getEnterCounter(setCounter);
        if (isDone(enterCounter, matchResult)) {
            return matchNext(enterCounter, stringIndex, testString, matchResult);
        }
        int nextIndex = matchInner(enterCounter, stringIndex, testString, matchResult);
        if (nextIndex >= 0) {
            return nextIndex;
        }
        return enterCounter >= quantifier.min() ? matchNext(enterCounter, stringIndex, testString, matchResult) : -1;
    }

    /**
     * No more iterations: the maximum is reached, or the last iteration matched the empty string. Before the first
     * iteration, the group may still hold the capture of an earlier attempt, so it is not asked.
     */
    final boolean isDone(int enterCounter, TMatchResultImpl matchResult) {
        return enterCounter >= quantifier.max() || (enterCounter > 0 && !innerSet.hasConsumed(matchResult));
    }

    final int matchInner(int enterCounter, int stringIndex, CharSequence testString, TMatchResultImpl matchResult) {
        matchResult.setEnterCounter(setCounter, enterCounter + 1);
        int nextIndex = innerSet.matches(stringIndex, testString, matchResult);
        matchResult.setEnterCounter(setCounter, enterCounter);
        return nextIndex;
    }

    final int matchNext(int enterCounter, int stringIndex, CharSequence testString, TMatchResultImpl matchResult) {
        if (enterCounter == 0) {
            return next.matches(stringIndex, testString, matchResult);
        }
        matchResult.setEnterCounter(setCounter, 0);
        int nextIndex = next.matches(stringIndex, testString, matchResult);
        matchResult.setEnterCounter(setCounter, enterCounter);
        return nextIndex;
    }

    public void reset() {
        quantifier.resetCounter();
    }

    @Override
    protected String getName() {
        return quantifier.toString();
    }

    void setQuantifier(TQuantifier quant) {
        this.quantifier = quant;
    }

    @Override
    void describe(TPatternWriter writer) {
        writer.create(this, TCompositeGroupQuantifierSet.class, "compositeGroupQuantifierSet", quantifier, innerSet,
                type, setCounter);
    }
}
