/*
 *  Copyright 2023 ihromant.
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

class TReversedDeque<E> extends TAbstractCollection<E> implements TDeque<E> {
    private final TDeque<E> base;

    TReversedDeque(TDeque<E> base) {
        this.base = base;
    }

    @Override
    public TDeque<E> reversed() {
        return base;
    }

    @Override
    public TIterator<E> iterator() {
        return base.descendingIterator();
    }

    @Override
    public int size() {
        return base.size();
    }

    @Override
    public boolean isEmpty() {
        return base.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return base.contains(o);
    }

    @Override
    public boolean add(E e) {
        base.addFirst(e);
        return true;
    }

    @Override
    public void addFirst(E e) {
        base.addLast(e);
    }

    @Override
    public void addLast(E e) {
        base.addFirst(e);
    }

    @Override
    public void clear() {
        base.clear();
    }

    @Override
    public boolean offerFirst(E e) {
        return base.offerLast(e);
    }

    @Override
    public boolean offerLast(E e) {
        return base.offerFirst(e);
    }

    @Override
    public E pollFirst() {
        return base.pollLast();
    }

    @Override
    public E pollLast() {
        return base.pollFirst();
    }

    @Override
    public E peekFirst() {
        return base.peekLast();
    }

    @Override
    public E peekLast() {
        return base.peekFirst();
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        return base.removeLastOccurrence(o);
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        return base.removeFirstOccurrence(o);
    }

    @Override
    public void push(E e) {
        base.addLast(e);
    }

    @Override
    public E pop() {
        return base.removeLast();
    }

    @Override
    public TIterator<E> descendingIterator() {
        return base.iterator();
    }

    @Override
    public boolean offer(E e) {
        return base.offerFirst(e);
    }

    @Override
    public E remove() {
        return base.removeLast();
    }

    @Override
    public E removeFirst() {
        return base.removeLast();
    }

    @Override
    public E removeLast() {
        return base.removeFirst();
    }

    @Override
    public E poll() {
        return base.pollLast();
    }

    @Override
    public E element() {
        return base.getLast();
    }

    @Override
    public E getFirst() {
        return base.getLast();
    }

    @Override
    public E getLast() {
        return base.getFirst();
    }

    @Override
    public E peek() {
        return base.peekLast();
    }
}
