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
package de.mirkosertic.bytecoder.ssa;

public class Expression extends Value {

    private String comment;

    public <T extends Expression> T withComment(String aComment) {
        comment = aComment;
        return (T) this;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public TypeRef resolveType() {
        return TypeRef.Native.VOID;
    }

    public <T extends Value> T resolveFirstArgument() {
        return (T) consumedValues(ConsumptionType.ARGUMENT).get(0);
    }

    public <T extends Value> T resolveSecondArgument() {
        return (T) consumedValues(ConsumptionType.ARGUMENT).get(1);
    }

    public <T extends Value> T resolveThirdArgument() {
        return (T) consumedValues(ConsumptionType.ARGUMENT).get(2);
    }
}
