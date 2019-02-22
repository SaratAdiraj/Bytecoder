/*
 * Copyright 2018 Mirko Sertic
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
package de.mirkosertic.bytecoder.ssa;

import de.mirkosertic.bytecoder.core.BytecodeOpcodeAddress;

public class MinExpression extends Expression {

    private final TypeRef targetType;

    public MinExpression(final BytecodeOpcodeAddress aAddress, final TypeRef aTargetType, final Value aLeftValue, final Value aRightValue) {
        super(aAddress);
        targetType = aTargetType;
        receivesDataFrom(aLeftValue);
        receivesDataFrom(aRightValue);
    }

    @Override
    public TypeRef resolveType() {
        return targetType;
    }
}