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
package de.mirkosertic.bytecoder.backend.wasm.ast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GlobalsSection implements ModuleSection {

    private final List<Global> globals;
    private final ExportsSection exports;

    GlobalsSection(final ExportsSection exportsSection) {
        this.globals = new ArrayList<>();
        this.exports = exportsSection;
    }

    public Global newMutableGlobal(final String name, final PrimitiveType type, final Value initializer) {
        final Global global = new Global(exports, name, type, true, initializer);
        globals.add(global);
        return global;
    }

    public Global newConstantGlobal(final String name, final PrimitiveType type, final Value initializer) {
        final Global global = new Global(exports, name, type, false, initializer);
        globals.add(global);
        return global;
    }

    @Override
    public void writeTo(final TextWriter textWriter) throws IOException {
        for (final Global global : globals) {
            global.writeTo(textWriter);
        }
    }

    public void writeTo(final BinaryWriter binaryWriter) throws IOException {
        try (final BinaryWriter.SectionWriter writer = binaryWriter.globalsSection()) {
            writer.writeUnsignedLeb128(globals.size());
            for (final Global global : globals) {
                global.writeTo(writer, globals);
            }
        }
    }

    public List<Global> globalsIndex() {
        return globals;
    }
}
