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
package org.teavm.model.analysis.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.teavm.model.BasicBlock;
import org.teavm.model.FieldReference;
import org.teavm.model.Incoming;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ImplicitExceptions;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class ImplicitExceptionsTest {
    @Test
    public void knownNonnegativeLengthCannotThrowNegativeArraySize() {
        assertFalse(arrayExceptions(0).contains(ImplicitExceptions.NEGATIVE_ARRAY_SIZE));
        assertFalse(arrayExceptions(Integer.MAX_VALUE).contains(ImplicitExceptions.NEGATIVE_ARRAY_SIZE));
    }

    @Test
    public void negativeAndUnknownLengthsCanThrow() {
        assertTrue(arrayExceptions(-1).contains(ImplicitExceptions.NEGATIVE_ARRAY_SIZE));
        assertTrue(arrayExceptions(null).contains(ImplicitExceptions.NEGATIVE_ARRAY_SIZE));
    }

    @Test
    public void receiverIsNonNullOnlyInAnInstanceMethod() {
        var program = new Program();
        var instance = program.createVariable();
        var result = program.createVariable();
        var block = program.createBasicBlock();
        var get = new GetFieldInstruction();
        get.setInstance(instance);
        get.setReceiver(result);
        get.setField(new FieldReference("Example", "value"));
        get.setFieldType(ValueType.INTEGER);
        block.add(get);
        assertFalse(exceptions(block, true).contains(ImplicitExceptions.NULL_POINTER));
        assertTrue(exceptions(block, false).contains(ImplicitExceptions.NULL_POINTER));
    }

    @Test
    public void freshAllocationCannotBeNull() {
        var program = new Program();
        program.createVariable();
        var instance = program.createVariable();
        var result = program.createVariable();
        var block = program.createBasicBlock();
        var create = new ConstructInstruction();
        create.setType("Example");
        create.setReceiver(instance);
        block.add(create);
        var get = new GetFieldInstruction();
        get.setInstance(instance);
        get.setReceiver(result);
        get.setField(new FieldReference("Example", "value"));
        get.setFieldType(ValueType.INTEGER);
        block.add(get);
        assertFalse(exceptions(block, false).contains(ImplicitExceptions.NULL_POINTER));
    }

    @Test
    public void unknownDimensionIsNotInferredFromAnotherBlock() {
        var program = new Program();
        program.createVariable();
        var size = program.createVariable();
        var known = program.createVariable();
        var result = program.createVariable();
        var predecessor = program.createBasicBlock();
        var constant = new IntegerConstantInstruction();
        constant.setConstant(0);
        constant.setReceiver(size);
        predecessor.add(constant);
        var block = program.createBasicBlock();
        var localConstant = new IntegerConstantInstruction();
        localConstant.setConstant(1);
        localConstant.setReceiver(known);
        block.add(localConstant);
        var create = new ConstructMultiArrayInstruction();
        create.setItemType(ValueType.INTEGER);
        create.getDimensions().add(known);
        create.getDimensions().add(size);
        create.setReceiver(result);
        block.add(create);
        assertTrue(exceptions(block, false).contains(ImplicitExceptions.NEGATIVE_ARRAY_SIZE));
    }

    @Test
    public void assignedDimensionsCoverKnownAndUnknownSigns() {
        var values = new Integer[] { null, Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE };
        for (var first : values) {
            for (var second : values) {
                var program = new Program();
                program.createVariable();
                var block = program.createBasicBlock();
                var create = new ConstructMultiArrayInstruction();
                create.setItemType(ValueType.INTEGER);
                create.setReceiver(program.createVariable());
                for (var value : new Integer[] { first, second }) {
                    var dimension = program.createVariable();
                    if (value != null) {
                        var constant = new IntegerConstantInstruction();
                        constant.setReceiver(dimension);
                        constant.setConstant(value);
                        block.add(constant);
                    }
                    for (int depth = 0; depth < 4; ++depth) {
                        var assignment = new AssignInstruction();
                        assignment.setAssignee(dimension);
                        dimension = program.createVariable();
                        assignment.setReceiver(dimension);
                        block.add(assignment);
                    }
                    create.getDimensions().add(dimension);
                }
                block.add(create);
                assertEquals("dimensions " + first + ", " + second,
                        first == null || first < 0 || second == null || second < 0,
                        exceptions(block, false).contains(ImplicitExceptions.NEGATIVE_ARRAY_SIZE));
            }
        }
    }

    @Test
    public void arithmeticIsUnknownEvenWithNonnegativeInputs() {
        var program = new Program();
        program.createVariable();
        var block = program.createBasicBlock();
        var left = program.createVariable();
        var right = program.createVariable();
        var sum = program.createVariable();
        var maximum = new IntegerConstantInstruction();
        maximum.setReceiver(left);
        maximum.setConstant(Integer.MAX_VALUE);
        block.add(maximum);
        var one = new IntegerConstantInstruction();
        one.setReceiver(right);
        one.setConstant(1);
        block.add(one);
        var add = new BinaryInstruction(BinaryOperation.ADD, NumericOperandType.INT);
        add.setFirstOperand(left);
        add.setSecondOperand(right);
        add.setReceiver(sum);
        block.add(add);
        var create = new ConstructArrayInstruction();
        create.setReceiver(program.createVariable());
        create.setItemType(ValueType.INTEGER);
        create.setSize(sum);
        block.add(create);
        assertTrue(exceptions(block, false).contains(ImplicitExceptions.NEGATIVE_ARRAY_SIZE));
    }

    @Test
    public void nullAndNonNullConstantsRemainDistinctThroughAssignments() {
        for (int kind = 0; kind < 4; ++kind) {
            var program = new Program();
            program.createVariable();
            var block = program.createBasicBlock();
            var value = program.createVariable();
            if (kind == 0) {
                var constant = new NullConstantInstruction();
                constant.setReceiver(value);
                block.add(constant);
            } else if (kind == 1) {
                var constant = new StringConstantInstruction();
                constant.setConstant("");
                constant.setReceiver(value);
                block.add(constant);
            } else if (kind == 2) {
                var constant = new ClassConstantInstruction();
                constant.setConstant(ValueType.object("java.lang.Object"));
                constant.setReceiver(value);
                block.add(constant);
            }
            for (int depth = 0; depth < 4; ++depth) {
                var assignment = new AssignInstruction();
                assignment.setAssignee(value);
                value = program.createVariable();
                assignment.setReceiver(value);
                block.add(assignment);
            }
            var get = new GetFieldInstruction();
            get.setInstance(value);
            get.setReceiver(program.createVariable());
            get.setField(new FieldReference("Example", "value"));
            get.setFieldType(ValueType.INTEGER);
            block.add(get);
            assertEquals("constant kind " + kind, kind == 0 || kind == 3,
                    exceptions(block, false).contains(ImplicitExceptions.NULL_POINTER));
        }
    }

    @Test
    public void phiReceiverDoesNotInheritIncomingNonNullFact() {
        var program = new Program();
        program.createVariable();
        var left = program.createBasicBlock();
        var right = program.createBasicBlock();
        var block = program.createBasicBlock();
        var known = program.createVariable();
        var create = new ConstructInstruction();
        create.setType("Example");
        create.setReceiver(known);
        left.add(create);
        var unknown = program.createVariable();
        var phi = new Phi();
        var receiver = program.createVariable();
        phi.setReceiver(receiver);
        var first = new Incoming();
        first.setSource(left);
        first.setValue(known);
        phi.getIncomings().add(first);
        var second = new Incoming();
        second.setSource(right);
        second.setValue(unknown);
        phi.getIncomings().add(second);
        block.getPhis().add(phi);
        var get = new GetFieldInstruction();
        get.setInstance(receiver);
        get.setReceiver(program.createVariable());
        get.setField(new FieldReference("Example", "value"));
        get.setFieldType(ValueType.INTEGER);
        block.add(get);
        assertTrue(exceptions(block, false).contains(ImplicitExceptions.NULL_POINTER));
    }

    @Test
    public void arrayLengthUsesOnlyKnownNonNullOperands() {
        assertSiblingNullCheck(0);
    }

    @Test
    public void arrayCloneUsesOnlyKnownNonNullOperands() {
        assertSiblingNullCheck(1);
    }

    @Test
    public void arrayUnwrapUsesOnlyKnownNonNullOperands() {
        assertSiblingNullCheck(2);
    }

    @Test
    public void explicitNullCheckUsesOnlyKnownNonNullOperands() {
        assertSiblingNullCheck(3);
    }

    @Test
    public void monitorEnterUsesOnlyKnownNonNullOperands() {
        assertSiblingNullCheck(4);
    }

    @Test
    public void monitorExitUsesOnlyKnownNonNullOperands() {
        assertSiblingNullCheck(5);
    }

    private void assertSiblingNullCheck(int operation) {
        for (boolean known : new boolean[] { false, true }) {
            var program = new Program();
            program.createVariable();
            var block = program.createBasicBlock();
            var value = program.createVariable();
            var receiver = program.createVariable();
            if (known) {
                var size = program.createVariable();
                var constant = new IntegerConstantInstruction();
                constant.setReceiver(size);
                constant.setConstant(0);
                block.add(constant);
                var create = new ConstructArrayInstruction();
                create.setReceiver(value);
                create.setItemType(ValueType.INTEGER);
                create.setSize(size);
                block.add(create);
            }
            switch (operation) {
                case 0: {
                    var instruction = new ArrayLengthInstruction();
                    instruction.setArray(value);
                    instruction.setReceiver(receiver);
                    block.add(instruction);
                    break;
                }
                case 1: {
                    var instruction = new CloneArrayInstruction();
                    instruction.setArray(value);
                    instruction.setReceiver(receiver);
                    block.add(instruction);
                    break;
                }
                case 2: {
                    var instruction = new UnwrapArrayInstruction(ArrayElementType.INT);
                    instruction.setArray(value);
                    instruction.setReceiver(receiver);
                    block.add(instruction);
                    break;
                }
                case 3: {
                    var instruction = new NullCheckInstruction();
                    instruction.setValue(value);
                    instruction.setReceiver(receiver);
                    block.add(instruction);
                    break;
                }
                case 4: {
                    var instruction = new MonitorEnterInstruction();
                    instruction.setObjectRef(value);
                    block.add(instruction);
                    break;
                }
                case 5: {
                    var instruction = new MonitorExitInstruction();
                    instruction.setObjectRef(value);
                    block.add(instruction);
                    break;
                }
                default:
                    throw new AssertionError();
            }
            assertEquals("operation " + operation + ", known " + known, !known,
                    exceptions(block, false).contains(ImplicitExceptions.NULL_POINTER));
        }
    }

    @Test
    public void raisingKnownNonNullExceptionDoesNotInventNullPointerException() {
        for (boolean known : new boolean[] { false, true }) {
            var program = new Program();
            program.createVariable();
            var block = program.createBasicBlock();
            var value = program.createVariable();
            if (known) {
                var create = new ConstructInstruction();
                create.setType("java.lang.IllegalStateException");
                create.setReceiver(value);
                block.add(create);
            }
            var raise = new RaiseInstruction();
            raise.setException(value);
            block.add(raise);
            assertEquals(!known, exceptions(block, false).contains(ImplicitExceptions.NULL_POINTER));
        }
    }

    private Set<String> arrayExceptions(Integer length) {
        var program = new Program();
        program.createVariable();
        var size = program.createVariable();
        var result = program.createVariable();
        var block = program.createBasicBlock();
        if (length != null) {
            var constant = new IntegerConstantInstruction();
            constant.setConstant(length);
            constant.setReceiver(size);
            block.add(constant);
        }
        var create = new ConstructArrayInstruction();
        create.setSize(size);
        create.setItemType(ValueType.INTEGER);
        create.setReceiver(result);
        block.add(create);
        return exceptions(block, false);
    }

    private Set<String> exceptions(BasicBlock block, boolean instanceMethod) {
        var result = new HashSet<String>();
        ImplicitExceptions.read(block, instanceMethod, result::add);
        return result;
    }
}
