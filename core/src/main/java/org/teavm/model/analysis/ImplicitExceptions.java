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
package org.teavm.model.analysis;

import java.util.List;
import java.util.function.Consumer;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.InvocationType;

/**
 * The exceptions that the instructions of a block can throw from the checks that the backends insert, after
 * dependency analysis, for null values, array indexes, casts and negative array sizes. No instruction creates
 * these exceptions, but analyses must deliver them to the block's handlers.
 */
public final class ImplicitExceptions {
    public static final String NULL_POINTER = NullPointerException.class.getName();
    public static final String ARRAY_INDEX_OUT_OF_BOUNDS = ArrayIndexOutOfBoundsException.class.getName();
    public static final String CLASS_CAST = ClassCastException.class.getName();
    public static final String NEGATIVE_ARRAY_SIZE = NegativeArraySizeException.class.getName();

    private ImplicitExceptions() {
    }

    /**
     * Passes each exception class that the block's instructions can throw implicitly to the consumer, once.
     */
    public static void read(BasicBlockReader block, Consumer<String> consumer) {
        var reader = new Reader();
        block.readAllInstructions(reader);
        if (reader.nullPointer) {
            consumer.accept(NULL_POINTER);
        }
        if (reader.arrayIndexOutOfBounds) {
            consumer.accept(ARRAY_INDEX_OUT_OF_BOUNDS);
        }
        if (reader.classCast) {
            consumer.accept(CLASS_CAST);
        }
        if (reader.negativeArraySize) {
            consumer.accept(NEGATIVE_ARRAY_SIZE);
        }
    }

    private static class Reader extends AbstractInstructionReader {
        boolean nullPointer;
        boolean arrayIndexOutOfBounds;
        boolean classCast;
        boolean negativeArraySize;

        @Override
        public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
            classCast |= !weak;
        }

        @Override
        public void raise(VariableReader exception) {
            nullPointer = true;
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
            negativeArraySize = true;
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType,
                List<? extends VariableReader> dimensions) {
            negativeArraySize = true;
        }

        @Override
        public void getField(VariableReader receiver, VariableReader instance, FieldReference field,
                ValueType fieldType) {
            nullPointer |= instance != null;
        }

        @Override
        public void putField(VariableReader instance, FieldReference field, VariableReader value,
                ValueType fieldType) {
            nullPointer |= instance != null;
        }

        @Override
        public void arrayLength(VariableReader receiver, VariableReader array) {
            nullPointer = true;
        }

        @Override
        public void cloneArray(VariableReader receiver, VariableReader array) {
            nullPointer = true;
        }

        @Override
        public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
            nullPointer = true;
        }

        @Override
        public void getElement(VariableReader receiver, VariableReader array, VariableReader index,
                ArrayElementType elementType) {
            arrayIndexOutOfBounds = true;
        }

        @Override
        public void putElement(VariableReader array, VariableReader index, VariableReader value,
                ArrayElementType elementType) {
            arrayIndexOutOfBounds = true;
        }

        @Override
        public void invoke(VariableReader receiver, VariableReader instance, MethodReference method,
                List<? extends VariableReader> arguments, InvocationType type) {
            // A constructor's receiver is the object just created.
            nullPointer |= instance != null && !method.getName().equals("<init>");
        }

        @Override
        public void nullCheck(VariableReader receiver, VariableReader value) {
            nullPointer = true;
        }

        @Override
        public void monitorEnter(VariableReader objectRef) {
            nullPointer = true;
        }

        @Override
        public void monitorExit(VariableReader objectRef) {
            nullPointer = true;
        }

        @Override
        public void boundCheck(VariableReader receiver, VariableReader index, VariableReader array,
                boolean lower) {
            arrayIndexOutOfBounds = true;
        }
    }
}
