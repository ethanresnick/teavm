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
package org.teavm.model.optimization.test;

import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Test;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.ListingParseUtils;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.optimization.UnusedVariableElimination;
import org.teavm.model.optimization.UnusedVariableElimination.ImplicitExceptions;
import org.teavm.model.text.ListingBuilder;

public class UnusedVariableEliminationTest {
    private static final String PREFIX = "model/optimization/unused-variable-elimination/";

    @Test
    public void removeAll() {
        check(parse("implicitExceptions.original"), "remove", ImplicitExceptions.REMOVE);
    }

    @Test
    public void keepUnchecked() {
        check(parse("implicitExceptions.original"), "keepUnchecked", ImplicitExceptions.KEEP_UNCHECKED);
    }

    @Test
    public void keepAll() {
        check(parse("implicitExceptions.original"), "keepAll", ImplicitExceptions.KEEP_ALL);
    }

    @Test
    public void constantSizeDefinedInLaterBlock() {
        check(parse("constantSizeDefinedInLaterBlock.original"), "constantSizeDefinedInLaterBlock",
                ImplicitExceptions.KEEP_ALL);
    }

    // Weak casts are inserted by the compiler where the type is already known, so they can't fail.
    @Test
    public void weakCastRemoved() {
        Program program = parse("weakCast.original");
        forEachInstruction(program, insn -> {
            if (insn instanceof CastInstruction) {
                ((CastInstruction) insn).setWeak(true);
            }
        });
        check(program, "weakCast", ImplicitExceptions.KEEP_ALL);
    }

    // The listing syntax has no negative literals, so @minusOne is written as 1.
    private static Program parse(String name) {
        Program program = ListingParseUtils.parseFromResource(PREFIX + name + ".txt");
        forEachInstruction(program, insn -> {
            if (insn instanceof IntegerConstantInstruction
                    && "minusOne".equals(((IntegerConstantInstruction) insn).getReceiver().getLabel())) {
                ((IntegerConstantInstruction) insn).setConstant(-1);
            }
        });
        return program;
    }

    private static void forEachInstruction(Program program, Consumer<Instruction> action) {
        for (BasicBlock block : program.getBasicBlocks()) {
            for (Instruction insn : block) {
                action.accept(insn);
            }
        }
    }

    private void check(Program program, String expected, ImplicitExceptions mode) {
        Program expectedProgram = parse(expected + ".expected");
        MethodHolder method = new MethodHolder("test", ValueType.VOID);
        new UnusedVariableElimination(mode).optimize(method, program);
        Assert.assertEquals(new ListingBuilder().buildListing(expectedProgram, ""),
                new ListingBuilder().buildListing(program, ""));
    }
}
