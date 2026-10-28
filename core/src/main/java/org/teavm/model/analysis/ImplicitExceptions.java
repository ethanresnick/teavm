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

import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.InvocationType;

/**
 * The exceptions that the instructions of a block can throw from the checks that the backends insert, after
 * dependency analysis, for null values, array indexes, casts and negative array sizes, and the errors of the class
 * initializers that they trigger. No instruction creates these exceptions, but analyses must deliver them to the
 * block's handlers.
 */
public final class ImplicitExceptions {
    public static final String NULL_POINTER = NullPointerException.class.getName();
    public static final String ARRAY_INDEX_OUT_OF_BOUNDS = ArrayIndexOutOfBoundsException.class.getName();
    public static final String CLASS_CAST = ClassCastException.class.getName();
    public static final String NEGATIVE_ARRAY_SIZE = NegativeArraySizeException.class.getName();
    private static final String EXCEPTION_IN_INITIALIZER = ExceptionInInitializerError.class.getName();
    private static final String NO_CLASS_DEF_FOUND = NoClassDefFoundError.class.getName();
    private static final MethodDescriptor CLINIT = new MethodDescriptor("<clinit>", void.class);

    private ImplicitExceptions() {
    }

    /**
     * Passes each exception class that the block's instructions can throw implicitly to the consumer, once.
     */
    public static void read(BasicBlockReader block, Consumer<String> consumer) {
        read(block, false, consumer);
    }

    /**
     * Reads an SSA block, using only facts established earlier in this block. Phi inputs and facts from other
     * blocks are deliberately not inferred. In an instance method, SSA variable zero is the non-null receiver.
     */
    public static void read(BasicBlockReader block, boolean instanceMethod, Consumer<String> consumer) {
        var reader = new Reader();
        if (instanceMethod) {
            reader.nonNull.set(0);
        }
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

    /**
     * Passes to the consumer, once each, the classes whose initialization the block's instructions can trigger, as
     * dependency analysis sees them: the owner of a static field, the class of a method that is not called
     * virtually (the backends initialize it on entry to its static methods and constructors), and the class of an
     * explicit initialization. {@link #readInitializers} adds their superclasses. Keep this in line with the
     * initClass calls of the dependency package's AbstractInstructionAnalyzer and DependencyGraphBuilder.
     */
    public static void readInitializedClasses(BasicBlockReader block, Consumer<String> consumer) {
        var reader = new InitializedClassReader();
        block.readAllInstructions(reader);
        reader.classes.forEach(consumer);
    }

    /**
     * Passes to the consumer the class initializers that initializing the class can run: its own and those of its
     * superclasses. What they throw reaches the code that triggers the initialization.
     */
    public static void readInitializers(ClassReaderSource classSource, String className,
            Consumer<MethodReference> consumer) {
        classSource.getAncestorClasses(className)
                .filter(cls -> cls.getMethod(CLINIT) != null)
                .forEach(cls -> consumer.accept(new MethodReference(cls.getName(), CLINIT)));
    }

    /**
     * Passes to the consumer the errors that the backends throw when a class initializer throws the given type:
     * ExceptionInInitializerError the first time, unless the type is an {@link Error}, which they rethrow as it is,
     * and NoClassDefFoundError on every later use of the class. No initializer declares these errors.
     */
    public static void readInitializerFailure(ClassHierarchy hierarchy, ValueType thrownType,
            Consumer<String> consumer) {
        if (!hierarchy.isSuperType(ValueType.object(Error.class.getName()), thrownType, false)) {
            consumer.accept(EXCEPTION_IN_INITIALIZER);
        }
        consumer.accept(NO_CLASS_DEF_FOUND);
    }

    private static class InitializedClassReader extends AbstractInstructionReader {
        final Set<String> classes = new LinkedHashSet<>();

        @Override
        public void initClass(String className) {
            classes.add(className);
        }

        @Override
        public void getField(VariableReader receiver, VariableReader instance, FieldReference field,
                ValueType fieldType) {
            if (instance == null) {
                classes.add(field.getClassName());
            }
        }

        @Override
        public void putField(VariableReader instance, FieldReference field, VariableReader value,
                ValueType fieldType) {
            if (instance == null) {
                classes.add(field.getClassName());
            }
        }

        @Override
        public void invoke(VariableReader receiver, VariableReader instance, MethodReference method,
                List<? extends VariableReader> arguments, InvocationType type) {
            if (instance == null || type == InvocationType.SPECIAL) {
                classes.add(method.getClassName());
            }
        }
    }

    private static class Reader extends AbstractInstructionReader {
        boolean nullPointer;
        boolean arrayIndexOutOfBounds;
        boolean classCast;
        boolean negativeArraySize;
        final BitSet nonNegative = new BitSet();
        final BitSet nonNull = new BitSet();

        @Override
        public void create(VariableReader receiver, String type) {
            nonNull.set(receiver.getIndex());
        }

        @Override
        public void stringConstant(VariableReader receiver, String value) {
            nonNull.set(receiver.getIndex());
        }

        @Override
        public void classConstant(VariableReader receiver, ValueType value) {
            nonNull.set(receiver.getIndex());
        }

        @Override
        public void integerConstant(VariableReader receiver, int cst) {
            nonNegative.set(receiver.getIndex(), cst >= 0);
        }

        @Override
        public void assign(VariableReader receiver, VariableReader assignee) {
            nonNegative.set(receiver.getIndex(), nonNegative.get(assignee.getIndex()));
            nonNull.set(receiver.getIndex(), nonNull.get(assignee.getIndex()));
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
            classCast |= !weak;
        }

        @Override
        public void raise(VariableReader exception) {
            nullPointer |= !nonNull.get(exception.getIndex());
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
            negativeArraySize |= !nonNegative.get(size.getIndex());
            nonNull.set(receiver.getIndex());
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType,
                List<? extends VariableReader> dimensions) {
            nonNull.set(receiver.getIndex());
            for (var dimension : dimensions) {
                negativeArraySize |= !nonNegative.get(dimension.getIndex());
            }
        }

        @Override
        public void getField(VariableReader receiver, VariableReader instance, FieldReference field,
                ValueType fieldType) {
            nullPointer |= instance != null && !nonNull.get(instance.getIndex());
        }

        @Override
        public void putField(VariableReader instance, FieldReference field, VariableReader value,
                ValueType fieldType) {
            nullPointer |= instance != null && !nonNull.get(instance.getIndex());
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
            nullPointer |= instance != null && !method.getName().equals("<init>")
                    && !nonNull.get(instance.getIndex());
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
