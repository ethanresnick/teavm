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

import java.util.HashMap;
import java.util.function.Predicate;
import org.teavm.dependency.DependencyAnalyzer;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.Variable;
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
 * through {@link DivisionCheck} first, unless it is a constant other than zero. The same goes for the divisor of
 * {@code Integer} and {@code Long} {@code divideUnsigned} and {@code remainderUnsigned}. The backends do not
 * check the divisor themselves: JS gives 0, Wasm traps and C has undefined behavior.
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

        // A divisor that a block has already checked needs no second check later in the block, as in a / b
        // followed by a % b.
        var checked = new HashMap<Variable, Variable>();
        for (BasicBlock block : program.getBasicBlocks()) {
            checked.clear();
            for (Instruction instruction : block) {
                if (instruction instanceof BinaryInstruction binary) {
                    var type = binary.getOperandType();
                    var divisor = binary.getSecondOperand();
                    if (isIntegerDivision(binary.getOperation(), type)
                            && !isNonZeroConstant(nonZeroConstants, divisor)) {
                        binary.setSecondOperand(checked.computeIfAbsent(divisor, d -> insertCheck(program, binary,
                                d, type == NumericOperandType.LONG)));
                    }
                } else if (instruction instanceof InvokeInstruction invoke
                        && isUnsignedDivision(invoke.getMethod())) {
                    var arguments = invoke.getArguments().toArray(new Variable[0]);
                    if (!isNonZeroConstant(nonZeroConstants, arguments[1])) {
                        var isLong = invoke.getMethod().getClassName().equals(Long.class.getName());
                        arguments[1] = checked.computeIfAbsent(arguments[1], d -> insertCheck(program, invoke, d,
                                isLong));
                        invoke.setArguments(arguments);
                    }
                }
            }
        }
    }

    /**
     * Whether the instruction is an integer division or remainder, which throws ArithmeticException for a zero
     * divisor.
     */
    public static boolean isIntegerDivision(BinaryOperation operation, NumericOperandType type) {
        return (operation == BinaryOperation.DIVIDE || operation == BinaryOperation.MODULO)
                && (type == NumericOperandType.INT || type == NumericOperandType.LONG);
    }

    public static boolean isCheck(MethodReference method) {
        return method.equals(CHECK_INT) || method.equals(CHECK_LONG);
    }

    /**
     * Whether the method is {@code Integer} or {@code Long} {@code divideUnsigned} or {@code remainderUnsigned},
     * which the backends implement with the same division instructions and which throw ArithmeticException for a
     * zero divisor as well.
     */
    public static boolean isUnsignedDivision(MethodReference method) {
        return (method.getName().equals("divideUnsigned") || method.getName().equals("remainderUnsigned"))
                && method.parameterCount() == 2
                && (method.getClassName().equals(Integer.class.getName())
                    || method.getClassName().equals(Long.class.getName()));
    }

    private static boolean isNonZeroConstant(boolean[] nonZeroConstants, Variable variable) {
        return variable.getIndex() < nonZeroConstants.length && nonZeroConstants[variable.getIndex()];
    }

    private static Variable insertCheck(Program program, Instruction division, Variable divisor, boolean isLong) {
        var check = new InvokeInstruction();
        check.setType(InvocationType.SPECIAL);
        check.setMethod(isLong ? CHECK_LONG : CHECK_INT);
        check.setArguments(divisor);
        check.setReceiver(program.createVariable());
        check.setLocation(division.getLocation());
        division.insertPrevious(check);
        return check.getReceiver();
    }
}
