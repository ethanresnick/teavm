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
package org.teavm.model.transformation;

import java.util.function.Predicate;
import org.teavm.dependency.DependencyAnalyzer;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.runtime.DivisionCheck;

/**
 * Makes an integer division or remainder by zero throw ArithmeticException, as Java requires. The divisor goes
 * through {@link DivisionCheck} first, unless it is a constant other than zero. The backends do not check the
 * divisor themselves: JS gives 0, Wasm traps and C has undefined behavior.
 */
public class DivisionCheckInsertion {
    public static final MethodReference CHECK_INT = new MethodReference(DivisionCheck.class, "checkDivisor",
            int.class, int.class);
    public static final MethodReference CHECK_LONG = new MethodReference(DivisionCheck.class, "checkDivisor",
            long.class, long.class);

    private Predicate<MethodReference> methodFilter;

    public DivisionCheckInsertion(Predicate<MethodReference> methodFilter) {
        this.methodFilter = methodFilter;
    }

    /**
     * Links the checks. A target that runs this transformation must call it.
     */
    public static void contributeDependencies(DependencyAnalyzer dependencyAnalyzer) {
        dependencyAnalyzer.setIntegerDivisionChecked(true);
        dependencyAnalyzer.linkMethod(CHECK_INT).use();
        dependencyAnalyzer.linkMethod(CHECK_LONG).use();
    }

    public void transformProgram(Program program, MethodReference methodReference) {
        if (!methodFilter.test(methodReference) || program.basicBlockCount() == 0) {
            return;
        }
        var nonZeroConstants = new boolean[program.variableCount()];
        for (BasicBlock block : program.getBasicBlocks()) {
            for (Instruction instruction : block) {
                if (instruction instanceof IntegerConstantInstruction constant) {
                    nonZeroConstants[constant.getReceiver().getIndex()] = constant.getConstant() != 0;
                } else if (instruction instanceof LongConstantInstruction constant) {
                    nonZeroConstants[constant.getReceiver().getIndex()] = constant.getConstant() != 0;
                }
            }
        }

        for (BasicBlock block : program.getBasicBlocks()) {
            for (Instruction instruction : block) {
                if (!(instruction instanceof BinaryInstruction binary)) {
                    continue;
                }
                if (binary.getOperation() != BinaryOperation.DIVIDE
                        && binary.getOperation() != BinaryOperation.MODULO) {
                    continue;
                }
                var type = binary.getOperandType();
                if (type != NumericOperandType.INT && type != NumericOperandType.LONG) {
                    continue;
                }
                var divisor = binary.getSecondOperand();
                if (divisor.getIndex() < nonZeroConstants.length && nonZeroConstants[divisor.getIndex()]) {
                    continue;
                }

                var check = new InvokeInstruction();
                check.setType(InvocationType.SPECIAL);
                check.setMethod(type == NumericOperandType.INT ? CHECK_INT : CHECK_LONG);
                check.setArguments(divisor);
                check.setReceiver(program.createVariable());
                check.setLocation(binary.getLocation());
                binary.insertPrevious(check);
                binary.setSecondOperand(check.getReceiver());
            }
        }
    }
}
