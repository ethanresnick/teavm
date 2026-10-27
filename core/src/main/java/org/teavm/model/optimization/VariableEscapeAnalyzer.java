/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.model.optimization;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.*;
import org.teavm.model.instructions.*;
import org.teavm.model.optimization.UnusedVariableElimination.ImplicitExceptions;
import org.teavm.model.transformation.DivisionCheckInsertion;

public final class VariableEscapeAnalyzer {
    private VariableEscapeAnalyzer() {
    }

    /**
     * @param implicitExceptions which instructions that can throw by themselves to mark as escaping, so that they
     *                           are kept even when nothing uses their result.
     */
    public static boolean[] findEscapingVariables(Program program, ImplicitExceptions implicitExceptions) {
        boolean[] escaping = new boolean[program.variableCount()];
        InstructionAnalyzer analyzer = new InstructionAnalyzer(escaping, implicitExceptions);
        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            for (Instruction insn : block) {
                insn.acceptVisitor(analyzer);
            }
        }
        analyzer.markArraysOfPossiblyNegativeSize();
        analyzer.markDivisionsByPossibleZero();
        return escaping;
    }

    private static class InstructionAnalyzer extends AbstractInstructionVisitor {
        private boolean[] escaping;
        private ImplicitExceptions implicitExceptions;
        private boolean[] nonNegativeConstant;
        private List<Instruction> arrayAllocations;
        private boolean[] nonZeroConstant;
        private List<BinaryInstruction> divisions;

        InstructionAnalyzer(boolean[] escaping, ImplicitExceptions implicitExceptions) {
            this.escaping = escaping;
            this.implicitExceptions = implicitExceptions;
            if (implicitExceptions != ImplicitExceptions.REMOVE) {
                nonNegativeConstant = new boolean[escaping.length];
                arrayAllocations = new ArrayList<>();
            }
            if (implicitExceptions == ImplicitExceptions.KEEP_ALL) {
                nonZeroConstant = new boolean[escaping.length];
                divisions = new ArrayList<>();
            }
        }

        // As with array sizes, a divisor may be defined in a later block. A division by a non-zero constant can't
        // throw.
        void markDivisionsByPossibleZero() {
            if (divisions == null) {
                return;
            }
            for (var insn : divisions) {
                if (!nonZeroConstant[insn.getSecondOperand().getIndex()]) {
                    escaping[insn.getReceiver().getIndex()] = true;
                }
            }
        }

        // A size may be defined in a block that comes later than the allocation, so the sizes are checked after
        // all instructions are visited. An allocation of a non-negative constant size can't throw.
        void markArraysOfPossiblyNegativeSize() {
            if (arrayAllocations == null) {
                return;
            }
            for (Instruction insn : arrayAllocations) {
                Variable receiver;
                List<Variable> sizes;
                if (insn instanceof ConstructArrayInstruction) {
                    receiver = ((ConstructArrayInstruction) insn).getReceiver();
                    sizes = List.of(((ConstructArrayInstruction) insn).getSize());
                } else {
                    receiver = ((ConstructMultiArrayInstruction) insn).getReceiver();
                    sizes = ((ConstructMultiArrayInstruction) insn).getDimensions();
                }
                for (Variable size : sizes) {
                    if (!nonNegativeConstant[size.getIndex()]) {
                        escaping[receiver.getIndex()] = true;
                        break;
                    }
                }
            }
        }

        private void keep(Variable receiver, ImplicitExceptions minimalMode) {
            if (implicitExceptions.compareTo(minimalMode) >= 0) {
                escaping[receiver.getIndex()] = true;
            }
        }

        @Override
        public void visit(IntegerConstantInstruction insn) {
            if (nonNegativeConstant != null && insn.getConstant() >= 0) {
                nonNegativeConstant[insn.getReceiver().getIndex()] = true;
            }
            if (nonZeroConstant != null && insn.getConstant() != 0) {
                nonZeroConstant[insn.getReceiver().getIndex()] = true;
            }
        }

        @Override
        public void visit(LongConstantInstruction insn) {
            if (nonZeroConstant != null && insn.getConstant() != 0) {
                nonZeroConstant[insn.getReceiver().getIndex()] = true;
            }
        }

        @Override
        public void visit(BinaryInstruction insn) {
            if (divisions != null && DivisionCheckInsertion.isIntegerDivision(insn.getOperation(),
                    insn.getOperandType())) {
                divisions.add(insn);
            }
        }

        @Override
        public void visit(ConstructArrayInstruction insn) {
            if (arrayAllocations != null) {
                arrayAllocations.add(insn);
            }
        }

        @Override
        public void visit(ConstructMultiArrayInstruction insn) {
            if (arrayAllocations != null) {
                arrayAllocations.add(insn);
            }
        }

        @Override
        public void visit(CloneArrayInstruction insn) {
            keep(insn.getReceiver(), ImplicitExceptions.KEEP_ALL);
        }

        @Override
        public void visit(ArrayLengthInstruction insn) {
            keep(insn.getReceiver(), ImplicitExceptions.KEEP_UNCHECKED);
        }

        @Override
        public void visit(GetElementInstruction insn) {
            keep(insn.getReceiver(), ImplicitExceptions.KEEP_UNCHECKED);
        }

        @Override
        public void visit(GetFieldInstruction insn) {
            if (insn.getInstance() != null) {
                keep(insn.getReceiver(), ImplicitExceptions.KEEP_ALL);
            }
        }

        @Override
        public void visit(BranchingInstruction insn) {
            escaping[insn.getOperand().getIndex()] = true;
        }

        @Override
        public void visit(BinaryBranchingInstruction insn) {
            escaping[insn.getFirstOperand().getIndex()] = true;
            escaping[insn.getSecondOperand().getIndex()] = true;
        }

        @Override
        public void visit(SwitchInstruction insn) {
            escaping[insn.getCondition().getIndex()] = true;
        }

        @Override
        public void visit(ExitInstruction insn) {
            if (insn.getValueToReturn() != null) {
                escaping[insn.getValueToReturn().getIndex()] = true;
            }
        }

        @Override
        public void visit(RaiseInstruction insn) {
            escaping[insn.getException().getIndex()] = true;
        }

        @Override
        public void visit(PutFieldInstruction insn) {
            if (insn.getInstance() != null) {
                escaping[insn.getInstance().getIndex()] = true;
            }
            escaping[insn.getValue().getIndex()] = true;
        }

        @Override
        public void visit(PutElementInstruction insn) {
            escaping[insn.getArray().getIndex()] = true;
            escaping[insn.getIndex().getIndex()] = true;
            escaping[insn.getValue().getIndex()] = true;
        }

        @Override
        public void visit(InvokeInstruction insn) {
            if (insn.getInstance() != null) {
                escaping[insn.getInstance().getIndex()] = true;
            }
            for (Variable arg : insn.getArguments()) {
                escaping[arg.getIndex()] = true;
            }
        }

        @Override
        public void visit(InvokeDynamicInstruction insn) {
            if (insn.getInstance() != null) {
                escaping[insn.getInstance().getIndex()] = true;
            }
            for (Variable arg : insn.getArguments()) {
                escaping[arg.getIndex()] = true;
            }
        }

        @Override
        public void visit(MonitorEnterInstruction insn) {
            escaping[insn.getObjectRef().getIndex()] = true;
        }

        @Override
        public void visit(MonitorExitInstruction insn) {
            escaping[insn.getObjectRef().getIndex()] = true;
        }

        @Override
        public void visit(BoundCheckInstruction insn) {
            if (insn.getArray() != null) {
                escaping[insn.getArray().getIndex()] = true;
            }
            if (insn.getReceiver() != null) {
                escaping[insn.getReceiver().getIndex()] = true;
            }
        }

        @Override
        public void visit(CastInstruction insn) {
            if (!insn.isWeak()) {
                keep(insn.getReceiver(), ImplicitExceptions.KEEP_UNCHECKED);
            }
        }

        @Override
        public void visit(NullCheckInstruction insn) {
            if (insn.getValue() != null) {
                escaping[insn.getValue().getIndex()] = true;
            }
            if (insn.getReceiver() != null) {
                escaping[insn.getReceiver().getIndex()] = true;
            }
        }
    }
}
