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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.teavm.model.BasicBlock;
import org.teavm.model.FieldReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ImplicitExceptions;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;

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
