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

import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.call;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.currentMemory;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.f32;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.getGlobal;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.getLocal;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.i32;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.param;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

public class ModuleTest {

    @Test
    public void testSimpleCase() throws IOException {
        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();
        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testSimpleCaseBinary() throws IOException {
        final Module module = new Module();
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testSimpleCase.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testSimpleCase.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());

    }

    @Test
    public void testSimpleFunction() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final ExportableFunction function = functionsContent.newFunction("label", Collections.singletonList(param("p1", PrimitiveType.i32)), PrimitiveType.i32);

        function.flow.ret(i32.c(42));
        function.exportAs("expfunction");

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func (param i32) (result i32)))" + System.lineSeparator()
                + "    (func $label (type $t0) (param $p1 i32) (result i32)" + System.lineSeparator()
                + "        (return (i32.const 42)))" + System.lineSeparator()
                + "    (export \"expfunction\" (func $label))" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testSimpleFunctionBinary() throws IOException {

        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final ExportableFunction function = functionsContent.newFunction("label", Collections.singletonList(param("p1", PrimitiveType.i32)), PrimitiveType.i32);

        function.flow.ret(i32.c(42));
        function.exportAs("expfunction");

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testSimpleFunction.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testSimpleFunction.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testWithMemory() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();

        final Memory memory = module.getMems().newMemory(10, 20);

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        Assert.assertEquals("(module " + System.lineSeparator()
                + "    (memory $mem0 10 20)" + System.lineSeparator()
                + "    )", strWriter.toString());
    }

    @Test
    public void testWithMemoryBinary() throws IOException {
        final Module module = new Module();

        final Memory memory = module.getMems().newMemory(10, 20);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testWithMemory.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testWithMemory.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testWithExportedMemory() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();

        final Memory memory = module.getMems().newMemory(10, 20);
        memory.exportAs("exported");

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        Assert.assertEquals("(module " + System.lineSeparator()
                + "    (memory $mem0 10 20)" + System.lineSeparator()
                + "    (export \"exported\" (memory $mem0))" + System.lineSeparator()
                + "    )", strWriter.toString());
    }

    @Test
    public void testWithExportedMemoryBinary() throws IOException {

        final Module module = new Module();

        final Memory memory = module.getMems().newMemory(10, 20);
        memory.exportAs("exported");

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (final FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testWithExportedMemory.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testWithExportedMemory.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testFunctionImport() throws IOException {
        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();
        final Function function = module.getImports().importFunction(new ImportReference("mod","obj"),"label", PrimitiveType.i32);

        try (final TextWriter writer = new TextWriter(pw)) {
            module.writeTo(writer);
        }

        Assert.assertEquals("(module " + System.lineSeparator()
                + "    (type $t0 (func (result i32)))" + System.lineSeparator()
                + "    (import \"mod\" \"obj\" (func $label (type $t0)))" + System.lineSeparator()
                + "    )", strWriter.toString());
    }

    @Test
    public void testFunctionImportBinary() throws IOException {
        final Module module = new Module();
        final Function function = module.getImports().importFunction(new ImportReference("mod","obj"),"label", PrimitiveType.i32);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testFunctionImport.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testFunctionImport.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testLocalAccess() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);

        function.flow.ret(getLocal(tempLocal));
        function.exportAs("expfunction");

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func (param i32) (param i32) (result i32)))" + System.lineSeparator()
                + "    (func $label (type $t0) (param $p1 i32) (param $p2 i32) (result i32)" + System.lineSeparator()
                + "        (local $loc i32)" + System.lineSeparator()
                + "        (return (get_local $loc)))" + System.lineSeparator()
                + "    (export \"expfunction\" (func $label))" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testLocalAccessBinary() throws IOException {
        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);
        function.flow.ret(getLocal(tempLocal));
        function.exportAs("expfunction");

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testLocalAccess.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testLocalAccess.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testBlock() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);

        final Block block = function.flow.block("outer");
        block.flow.ret(getLocal(tempLocal));
        function.flow.unreachable();
        function.exportAs("expfunction");

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func (param i32) (param i32) (result i32)))" + System.lineSeparator()
                + "    (func $label (type $t0) (param $p1 i32) (param $p2 i32) (result i32)" + System.lineSeparator()
                + "        (local $loc i32)" + System.lineSeparator()
                + "        (block $outer" + System.lineSeparator()
                + "            (return (get_local $loc))" + System.lineSeparator()
                + "            )" + System.lineSeparator()
                + "        (unreachable))" + System.lineSeparator()
                + "    (export \"expfunction\" (func $label))" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testBlockBinary() throws IOException {
        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);

        final Block block = function.flow.block("outer");
        block.flow.ret(getLocal(tempLocal));
        function.flow.unreachable();
        function.exportAs("expfunction");

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testBlockBinary.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testBlockBinary.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testIf() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);

        final Block block = function.flow.block("outer");
        function.flow.unreachable();

        final Iff ifExp = block.flow.iff("label", i32.eq(i32.c(10), i32.c(20)));
        ifExp.flow.ret(getLocal(tempLocal));

        function.exportAs("expfunction");

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func (param i32) (param i32) (result i32)))" + System.lineSeparator()
                + "    (func $label (type $t0) (param $p1 i32) (param $p2 i32) (result i32)" + System.lineSeparator()
                + "        (local $loc i32)" + System.lineSeparator()
                + "        (block $outer" + System.lineSeparator()
                + "            (if $label" + System.lineSeparator()
                + "                (i32.eq (i32.const 10) (i32.const 20))" + System.lineSeparator()
                + "                (return (get_local $loc)))" + System.lineSeparator()
                + "            )" + System.lineSeparator()
                + "        (unreachable))" + System.lineSeparator()
                + "    (export \"expfunction\" (func $label))" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testIfBinary() throws IOException {
        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);

        final Block block = function.flow.block("outer");
        function.flow.unreachable();

        final Iff ifExp = block.flow.iff("lab", i32.eq(i32.c(10), i32.c(20)));
        ifExp.flow.branch(block);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testIf.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testIf.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }


    @Test
    public void testBlockBranch() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);

        final Block block = function.flow.block("outer");
        block.flow.branch(block);
        function.flow.unreachable();
        function.exportAs("expfunction");

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func (param i32) (param i32) (result i32)))" + System.lineSeparator()
                + "    (func $label (type $t0) (param $p1 i32) (param $p2 i32) (result i32)" + System.lineSeparator()
                + "        (local $loc i32)" + System.lineSeparator()
                + "        (block $outer" + System.lineSeparator()
                + "            (br $outer)" + System.lineSeparator()
                + "            )" + System.lineSeparator()
                + "        (unreachable))" + System.lineSeparator()
                + "    (export \"expfunction\" (func $label))" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testBlockBranchBinary() throws IOException {
        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);

        final Block block = function.flow.block("outer");
        block.flow.branch(block);
        function.flow.unreachable();
        function.exportAs("expfunction");

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testBlockBranch.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testBlockBranch.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testBlockBranchIf() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);

        final Block block = function.flow.block("outer");
        block.flow.branchIff(block, i32.c(42));
        function.flow.unreachable();
        function.exportAs("expfunction");

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func (param i32) (param i32) (result i32)))" + System.lineSeparator()
                + "    (func $label (type $t0) (param $p1 i32) (param $p2 i32) (result i32)" + System.lineSeparator()
                + "        (local $loc i32)" + System.lineSeparator()
                + "        (block $outer" + System.lineSeparator()
                + "            (br_if $outer " + System.lineSeparator()
                + "                (i32.const 42))" + System.lineSeparator()
                + "            )" + System.lineSeparator()
                + "        (unreachable))" + System.lineSeparator()
                + "    (export \"expfunction\" (func $label))" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testBlockBranchIfBinary() throws IOException {
        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local tempLocal = function.localByLabel("loc", PrimitiveType.i32);

        final Block block = function.flow.block("outer");
        block.flow.branchIff(block, i32.c(42));
        function.flow.unreachable();
        function.exportAs("expfunction");

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (final FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testBlockBranchIf.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testBlockBranchIf.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testTableWithFunction() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        function.flow.unreachable();
        function.toTable();

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator() +
                "    (type $t0 (func (param i32) (param i32) (result i32)))" + System.lineSeparator() +
                "    (table 1 anyfunc)" + System.lineSeparator() +
                "    (elem (i32.const 0) $label)" + System.lineSeparator() +
                "    (func $label (type $t0) (param $p1 i32) (param $p2 i32) (result i32)" + System.lineSeparator() +
                "        (unreachable))" + System.lineSeparator() +
                "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testTableWithFunctionBinary() throws IOException {

        final Module module = new Module();
        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        function.flow.unreachable();
        function.toTable();

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (final FileOutputStream fos = new FileOutputStream("D:\\source\\idea_projects\\Bytecoder\\core\\src\\test\\resources\\de\\mirkosertic\\bytecoder\\backend\\wasm\\ast\\testTableWithFunction.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testTableWithFunction.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testGlobals() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();

        final Global g1 = module.getGlobals().newConstantGlobal("constant", PrimitiveType.i32, i32.c(42));
        final Global g2 = module.getGlobals().newMutableGlobal("mutable", PrimitiveType.i32, i32.c(21));

        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        function.flow.ret(getGlobal(g1));

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func (param i32) (param i32) (result i32)))" + System.lineSeparator()
                + "    (global $constant i32 (i32.const 42))" + System.lineSeparator()
                + "    (global $mutable (mut i32) (i32.const 21))" + System.lineSeparator()
                + "    (func $label (type $t0) (param $p1 i32) (param $p2 i32) (result i32)" + System.lineSeparator()
                + "        (return (get_global $constant)))" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testGlobalsBinary() throws IOException {

        final Module module = new Module();

        final Global g1 = module.getGlobals().newConstantGlobal("constant", PrimitiveType.i32, i32.c(42));
        final Global g2 = module.getGlobals().newMutableGlobal("mutable", PrimitiveType.i32, i32.c(21));

        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        function.flow.ret(getGlobal(g1));

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (final FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testGlobalsBinary.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testGlobalsBinary.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testIntegerMath() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();

        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local loc1 = function.localByLabel("local1", PrimitiveType.i32);
        function.flow.setLocal(loc1, i32.c(100));
        function.flow.ret(i32.add(getLocal(loc1), i32.c(200)));

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func (param i32) (param i32) (result i32)))" + System.lineSeparator()
                + "    (func $label (type $t0) (param $p1 i32) (param $p2 i32) (result i32)" + System.lineSeparator()
                + "        (local $local1 i32)" + System.lineSeparator()
                + "        (set_local $local1 (i32.const 100))" + System.lineSeparator()
                + "        (return (i32.add (get_local $local1) (i32.const 200))))" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testIntegerMathBinary() throws IOException {

        final Module module = new Module();

        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local loc1 = function.localByLabel("local1", PrimitiveType.i32);
        function.flow.setLocal(loc1, i32.c(100));
        function.flow.ret(i32.add(getLocal(loc1), i32.c(200)));

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (final FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testIntegerMath.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testIntegerMath.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testCall() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();

        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local loc1 = function.localByLabel("local1", PrimitiveType.i32);
        function.flow.ret(call(function, Arrays.asList(getLocal(p1), getLocal(p2))));

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func (param i32) (param i32) (result i32)))" + System.lineSeparator()
                + "    (func $label (type $t0) (param $p1 i32) (param $p2 i32) (result i32)" + System.lineSeparator()
                + "        (local $local1 i32)" + System.lineSeparator()
                + "        (return (call $label (get_local $p1) (get_local $p2))))" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testCallBinary() throws IOException {

        final Module module = new Module();

        final FunctionsSection functionsContent = module.getFunctions();
        final Param p1 = param("p1", PrimitiveType.i32);
        final Param p2 = param("p2", PrimitiveType.i32);
        final ExportableFunction function = functionsContent.newFunction("label", Arrays
                .asList(p1, p2), PrimitiveType.i32);

        final Local loc1 = function.localByLabel("local1", PrimitiveType.i32);
        function.flow.ret(call(function, Arrays.asList(getLocal(p1), getLocal(p2))));

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (final FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testCall.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testCall.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testMemoryInit() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();

        final Global stackTop = module.getGlobals().newMutableGlobal("STACKTOP", PrimitiveType.i32, i32.c(-1));
        final ExportableFunction bootstrap = module.getFunctions().newFunction("bootstrap", Collections.emptyList());
        bootstrap.flow.setGlobal(stackTop, i32.sub(i32.mul(currentMemory(), i32.c(65536)), i32.c(1)));

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func))" + System.lineSeparator()
                + "    (global $STACKTOP (mut i32) (i32.const -1))" + System.lineSeparator()
                + "    (func $bootstrap (type $t0)" + System.lineSeparator()
                + "        (set_global $STACKTOP (i32.sub (i32.mul (current_memory) (i32.const 65536)) (i32.const 1)))" + System.lineSeparator()
                + "        )" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testMemoryInitBinary() throws IOException {

        final Module module = new Module();

        final Global stackTop = module.getGlobals().newMutableGlobal("STACKTOP", PrimitiveType.i32, i32.c(-1));
        final ExportableFunction bootstrap = module.getFunctions().newFunction("bootstrap", Collections.emptyList());
        bootstrap.flow.setGlobal(stackTop, i32.sub(i32.mul(currentMemory(), i32.c(65536)), i32.c(1)));

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (final FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testMemoryInit.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testMemoryInit.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testSimpleF32() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();

        final Param p1 = param("p1", PrimitiveType.f32);
        final Param p2 = param("p2", PrimitiveType.f32);
        final ExportableFunction compareValueF32 = module.getFunctions().newFunction("compareValueF32", Arrays.asList(p1, p2), PrimitiveType.i32);
        final Block b1 = compareValueF32.flow.block("b1");
        b1.flow.branchIff(b1, ConstExpressions.f32.ne(getLocal(p1), getLocal(p2)));
        b1.flow.ret(i32.c(0));
        final Block b2 = compareValueF32.flow.block("b2");
        b2.flow.branchIff(b2, ConstExpressions.f32.ge(getLocal(p1), getLocal(p2)));
        b2.flow.ret(i32.c(-1));
        compareValueF32.flow.ret(i32.c(1));

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator() +
                "    (type $t0 (func (param f32) (param f32) (result i32)))" + System.lineSeparator() +
                "    (func $compareValueF32 (type $t0) (param $p1 f32) (param $p2 f32) (result i32)" + System.lineSeparator() +
                "        (block $b1" + System.lineSeparator() +
                "            (br_if $b1 " + System.lineSeparator() +
                "                (f32.ne (get_local $p1) (get_local $p2)))" + System.lineSeparator() +
                "            (return (i32.const 0))" + System.lineSeparator() +
                "            )" + System.lineSeparator() +
                "        (block $b2" + System.lineSeparator() +
                "            (br_if $b2 " + System.lineSeparator() +
                "                (f32.ge (get_local $p1) (get_local $p2)))" + System.lineSeparator() +
                "            (return (i32.const -1))" + System.lineSeparator() +
                "            )" + System.lineSeparator() +
                "        (return (i32.const 1)))" + System.lineSeparator() +
                "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testSimpleF32Binary() throws IOException {

        final Module module = new Module();

        final Param p1 = param("p1", PrimitiveType.f32);
        final Param p2 = param("p2", PrimitiveType.f32);
        final ExportableFunction compareValueF32 = module.getFunctions().newFunction("compareValueF32", Arrays.asList(p1, p2), PrimitiveType.i32);
        final Block b1 = compareValueF32.flow.block("b1");
        b1.flow.branchIff(b1, ConstExpressions.f32.ne(getLocal(p1), getLocal(p2)));
        b1.flow.ret(i32.c(0));
        final Block b2 = compareValueF32.flow.block("b2");
        b2.flow.branchIff(b2, ConstExpressions.f32.ge(getLocal(p1), getLocal(p2)));
        b2.flow.ret(i32.c(-1));
        compareValueF32.flow.ret(i32.c(1));

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (final FileOutputStream fos = new FileOutputStream("D:\\source\\idea_projects\\Bytecoder\\core\\src\\test\\resources\\de\\mirkosertic\\bytecoder\\backend\\wasm\\ast\\testSimpleF32.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testSimpleF32.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    public void testIntegerMathComplete() throws IOException {

        final StringWriter strWriter = new StringWriter();
        final PrintWriter pw = new PrintWriter(strWriter);

        final Module module = new Module();

        final ExportableFunction testFunction = module.getFunctions().newFunction("testFunction", Collections.emptyList());
        Local local = testFunction.localByLabel("loc1", PrimitiveType.i32);
        testFunction.flow.setLocal(local, i32.add(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.and(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.clz(i32.c(10)));
        testFunction.flow.setLocal(local, i32.ctz(i32.c(10)));
        testFunction.flow.setLocal(local, i32.div_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.div_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.eq(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.eqz(i32.c(10)));
        testFunction.flow.setLocal(local, i32.ge_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.ge_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.gt_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.gt_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.le_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.le_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.lt_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.lt_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.mul(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.ne(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.or(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.popcount(i32.c(10)));
        testFunction.flow.setLocal(local, i32.reinterpretF32(f32.c(10.0f)));
        testFunction.flow.setLocal(local, i32.rem_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.rem_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.rotl(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.rotr(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.shl(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.shr_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.shr_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.sub(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.trunc_sF32(f32.c(10.2f)));
        testFunction.flow.setLocal(local, i32.trunc_uF32(f32.c(10.2f)));
        testFunction.flow.setLocal(local, i32.xor(i32.c(10), i32.c(20)));

        final Exporter exporter = new Exporter();
        exporter.export(module, pw);

        final String expected = "(module " + System.lineSeparator()
                + "    (type $t0 (func))" + System.lineSeparator()
                + "    (func $testFunction (type $t0)" + System.lineSeparator()
                + "        (local $loc1 i32)" + System.lineSeparator()
                + "        (set_local $loc1 (i32.add (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.and (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.clz (i32.const 10)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.ctz (i32.const 10)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.div_s (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.div_u (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.eq (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.eqz (i32.const 10)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.ge_s (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.ge_u (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.gt_s (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.gt_u (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.le_s (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.le_u (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.lt_s (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.lt_u (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.mul (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.ne (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.or (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.popcnt (i32.const 10)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.reinterpret/f32 (f32.const 10.0)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.rem_s (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.rem_u (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.rotl (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.rotr (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.shl (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.shr_s (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.shr_u (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.sub (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.trunc_s/f32 (f32.const 10.2)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.trunc_u/f32 (f32.const 10.2)))" + System.lineSeparator()
                + "        (set_local $loc1 (i32.xor (i32.const 10) (i32.const 20)))" + System.lineSeparator()
                + "        )" + System.lineSeparator()
                + "    )";
        Assert.assertEquals(expected, strWriter.toString());
    }

    @Test
    public void testIntegerMathCompleteBinary() throws IOException {

        final Module module = new Module();

        final ExportableFunction testFunction = module.getFunctions().newFunction("testFunction", Collections.emptyList());
        Local local = testFunction.localByLabel("loc1", PrimitiveType.i32);
        testFunction.flow.setLocal(local, i32.add(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.and(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.clz(i32.c(10)));
        testFunction.flow.setLocal(local, i32.ctz(i32.c(10)));
        testFunction.flow.setLocal(local, i32.div_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.div_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.eq(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.eqz(i32.c(10)));
        testFunction.flow.setLocal(local, i32.ge_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.ge_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.gt_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.gt_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.le_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.le_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.lt_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.lt_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.mul(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.ne(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.or(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.popcount(i32.c(10)));
        testFunction.flow.setLocal(local, i32.reinterpretF32(f32.c(10.0f)));
        testFunction.flow.setLocal(local, i32.rem_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.rem_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.rotl(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.rotr(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.shl(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.shr_s(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.shr_u(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.sub(i32.c(10), i32.c(20)));
        testFunction.flow.setLocal(local, i32.trunc_sF32(f32.c(10.2f)));
        testFunction.flow.setLocal(local, i32.trunc_uF32(f32.c(10.2f)));
        testFunction.flow.setLocal(local, i32.xor(i32.c(10), i32.c(20)));

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final Exporter exporter = new Exporter();
        exporter.export(module, bos);

        //try (final FileOutputStream fos = new FileOutputStream("/home/sertic/Development/Projects/Bytecoder/core/src/test/resources/de/mirkosertic/bytecoder/backend/wasm/ast/testIntegerMathComplete.wasm")) {
        //    exporter.export(module, fos);
        //}

        final byte[] expected = IOUtils.toByteArray(getClass().getResource("testIntegerMathComplete.wasm"));
        Assert.assertArrayEquals(expected, bos.toByteArray());
    }

}