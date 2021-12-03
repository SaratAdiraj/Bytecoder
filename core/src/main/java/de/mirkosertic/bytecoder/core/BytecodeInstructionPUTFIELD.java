/*
 * Copyright 2017 Mirko Sertic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mirkosertic.bytecoder.core;

public class BytecodeInstructionPUTFIELD extends BytecodeInstruction {

    private final int poolIndex;
    private final BytecodeConstantPool constantPool;

    public BytecodeInstructionPUTFIELD(final BytecodeOpcodeAddress aOpcodeIndex, final int aPoolIndex, final BytecodeConstantPool aConstantPool) {
        super(aOpcodeIndex);
        poolIndex = aPoolIndex;
        constantPool = aConstantPool;
    }

    public BytecodeFieldRefConstant getFieldRefConstant() {
        return (BytecodeFieldRefConstant) constantPool.constantByIndex(poolIndex - 1);
    }

    public BytecodeObjectTypeRef referencedType() {
        final BytecodeFieldRefConstant fieldRef = getFieldRefConstant();
        final BytecodeClassinfoConstant classInfo = fieldRef.getClassIndex().getClassConstant();
        return BytecodeObjectTypeRef.fromUtf8Constant(classInfo.getConstant());
    }

    public BytecodeUtf8Constant referencedField() {
        final BytecodeFieldRefConstant fieldRef = getFieldRefConstant();
        final BytecodeNameIndex theName = fieldRef.getNameAndTypeIndex().getNameAndType().getNameIndex();
        return theName.getName();
    }
}
