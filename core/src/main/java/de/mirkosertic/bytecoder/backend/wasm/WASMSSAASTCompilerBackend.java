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
package de.mirkosertic.bytecoder.backend.wasm;

import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.call;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.currentMemory;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.getLocal;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.i32;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.f32;
import static de.mirkosertic.bytecoder.backend.wasm.ast.ConstExpressions.param;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.mirkosertic.bytecoder.api.EmulatedByRuntime;
import de.mirkosertic.bytecoder.api.Export;
import de.mirkosertic.bytecoder.backend.CompileBackend;
import de.mirkosertic.bytecoder.backend.CompileOptions;
import de.mirkosertic.bytecoder.backend.wasm.ast.Block;
import de.mirkosertic.bytecoder.backend.wasm.ast.Call;
import de.mirkosertic.bytecoder.backend.wasm.ast.ExportableFunction;
import de.mirkosertic.bytecoder.backend.wasm.ast.Exporter;
import de.mirkosertic.bytecoder.backend.wasm.ast.Function;
import de.mirkosertic.bytecoder.backend.wasm.ast.Global;
import de.mirkosertic.bytecoder.backend.wasm.ast.ImportReference;
import de.mirkosertic.bytecoder.backend.wasm.ast.Module;
import de.mirkosertic.bytecoder.backend.wasm.ast.Param;
import de.mirkosertic.bytecoder.backend.wasm.ast.PrimitiveType;
import de.mirkosertic.bytecoder.backend.wasm.ast.Value;
import de.mirkosertic.bytecoder.classlib.Address;
import de.mirkosertic.bytecoder.classlib.MemoryManager;
import de.mirkosertic.bytecoder.core.BytecodeAnnotation;
import de.mirkosertic.bytecoder.core.BytecodeArrayTypeRef;
import de.mirkosertic.bytecoder.core.BytecodeImportedLink;
import de.mirkosertic.bytecoder.core.BytecodeLinkedClass;
import de.mirkosertic.bytecoder.core.BytecodeLinkerContext;
import de.mirkosertic.bytecoder.core.BytecodeMethod;
import de.mirkosertic.bytecoder.core.BytecodeMethodSignature;
import de.mirkosertic.bytecoder.core.BytecodeObjectTypeRef;
import de.mirkosertic.bytecoder.core.BytecodePrimitiveTypeRef;
import de.mirkosertic.bytecoder.core.BytecodeResolvedMethods;
import de.mirkosertic.bytecoder.core.BytecodeTypeRef;
import de.mirkosertic.bytecoder.relooper.Relooper;
import de.mirkosertic.bytecoder.ssa.Program;
import de.mirkosertic.bytecoder.ssa.ProgramGenerator;
import de.mirkosertic.bytecoder.ssa.ProgramGeneratorFactory;
import de.mirkosertic.bytecoder.ssa.RegionNode;
import de.mirkosertic.bytecoder.ssa.TypeRef;
import de.mirkosertic.bytecoder.ssa.Variable;

public class WASMSSAASTCompilerBackend implements CompileBackend<WASMCompileResult> {

    private static class CallSite {
        private final Program program;
        private final RegionNode bootstrapMethod;

        private CallSite(final Program aProgram, final RegionNode aBootstrapMethod) {
            this.program = aProgram;
            this.bootstrapMethod = aBootstrapMethod;
        }
    }

    private final ProgramGeneratorFactory programGeneratorFactory;

    public WASMSSAASTCompilerBackend(final ProgramGeneratorFactory aProgramGeneratorFactory) {
        this.programGeneratorFactory = aProgramGeneratorFactory;
    }

    public static PrimitiveType toType(final TypeRef aType) {
        switch (aType.resolve()) {
        case DOUBLE:
            return PrimitiveType.f32;
        case FLOAT:
            return PrimitiveType.f32;
        default:
            return PrimitiveType.i32;
        }
    }

    @Override
    public WASMCompileResult generateCodeFor(
            final CompileOptions aOptions, final BytecodeLinkerContext aLinkerContext, final Class aEntryPointClass, final String aEntryPointMethodName, final BytecodeMethodSignature aEntryPointSignatue) {

        // Link required mamory management code
        final BytecodeLinkedClass theArrayClass = aLinkerContext.resolveClass(BytecodeObjectTypeRef.fromRuntimeClass(Array.class));

        final BytecodeLinkedClass theManagerClass = aLinkerContext.resolveClass(BytecodeObjectTypeRef.fromRuntimeClass(MemoryManager.class));

        theManagerClass.resolveStaticMethod("freeMem", new BytecodeMethodSignature(BytecodePrimitiveTypeRef.LONG, new BytecodeTypeRef[0]));
        theManagerClass.resolveStaticMethod("usedMem", new BytecodeMethodSignature(BytecodePrimitiveTypeRef.LONG, new BytecodeTypeRef[0]));

        theManagerClass.resolveStaticMethod("free", new BytecodeMethodSignature(BytecodePrimitiveTypeRef.VOID, new BytecodeTypeRef[] {BytecodeObjectTypeRef.fromRuntimeClass(
                Address.class)}));
        theManagerClass.resolveStaticMethod("malloc", new BytecodeMethodSignature(BytecodeObjectTypeRef.fromRuntimeClass(
                Address.class), new BytecodeTypeRef[] {BytecodePrimitiveTypeRef.INT}));
        theManagerClass.resolveStaticMethod("newObject", new BytecodeMethodSignature(BytecodeObjectTypeRef.fromRuntimeClass(
                Address.class), new BytecodeTypeRef[] {BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT}));
        theManagerClass.resolveStaticMethod("newArray", new BytecodeMethodSignature(BytecodeObjectTypeRef.fromRuntimeClass(
                Address.class), new BytecodeTypeRef[] {BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT}));
        theManagerClass.resolveStaticMethod("newArray", new BytecodeMethodSignature(BytecodeObjectTypeRef.fromRuntimeClass(
                Address.class), new BytecodeTypeRef[] {BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT, BytecodePrimitiveTypeRef.INT}));

        final BytecodeLinkedClass theStringClass = aLinkerContext.resolveClass(BytecodeObjectTypeRef.fromRuntimeClass(String.class));
        if (!theStringClass.resolveConstructorInvocation(new BytecodeMethodSignature(BytecodePrimitiveTypeRef.VOID, new BytecodeTypeRef[] {new BytecodeArrayTypeRef(BytecodePrimitiveTypeRef.BYTE, 1)}))) {
            throw new IllegalStateException("No matching constructor!");
        }

        final Module module = new Module();

        // We need some Runtime Imports
        final Function floatRemainder = module.getImports().importFunction(new ImportReference("math", "float_rem"),
                "float_remainder", Arrays.asList(param("p1", PrimitiveType.f32), param("p2", PrimitiveType.f32)), PrimitiveType.f32);

        // We need a memory
        module.getMems().newMemory(512, 512).exportAs("memory");

        // Also import functions first
        aLinkerContext.linkedClasses().forEach(aEntry -> {

            if (aEntry.targetNode().getBytecodeClass().getAccessFlags().isInterface()) {
                return;
            }
            if (Objects.equals(aEntry.edgeType().objectTypeRef(), BytecodeObjectTypeRef.fromRuntimeClass(Address.class))) {
                return;
            }

            final BytecodeResolvedMethods theMethodMap = aEntry.targetNode().resolvedMethods();
            theMethodMap.stream().forEach(aMethodMapEntry -> {

                // Only add implementation methods
                if (!(aMethodMapEntry.getProvidingClass() == aEntry.targetNode())) {
                    return;
                }

                final BytecodeMethod t = aMethodMapEntry.getValue();
                final BytecodeMethodSignature theSignature = t.getSignature();

                if (t.getAccessFlags().isNative()) {
                    if (null != aMethodMapEntry.getProvidingClass().getBytecodeClass().getAttributes()
                            .getAnnotationByType(EmulatedByRuntime.class.getName())) {
                        return;
                    }

                    final BytecodeImportedLink theLink = aMethodMapEntry.getProvidingClass().linkfor(t);

                    final String methodName = WASMWriterUtils.toMethodName(aMethodMapEntry.getProvidingClass().getClassName(), t.getName(), theSignature);
                    final ImportReference importReference = new ImportReference(theLink.getModuleName(), theLink.getLinkName());

                    final List<Param> params = new ArrayList<>();
                    params.add(param("thisRef",toType(TypeRef.Native.REFERENCE)));
                    for (int i = 0; i < theSignature.getArguments().length; i++) {
                        final BytecodeTypeRef theParamType = theSignature.getArguments()[i];
                        params.add(param("p" + (i + 1), toType(TypeRef.toType(theParamType))));
                    }

                    if (!theSignature.getReturnType().isVoid()) {
                        final Function imported = module.getImports().importFunction(
                                importReference,
                                methodName,
                                params,
                                toType(TypeRef.toType(theSignature.getReturnType())));
                    } else {
                        final Function imported = module.getImports().importFunction(
                                importReference,
                                methodName,
                                params);
                    }
                }
            });
        });

        // Initialize memory layout for classes and instances
        final WASMMemoryLayouter theMemoryLayout = new WASMMemoryLayouter(aLinkerContext);

        // Now everything else
        aLinkerContext.linkedClasses().forEach(aEntry -> {

            final BytecodeLinkedClass theLinkedClass = aEntry.targetNode();

            if (Objects.equals(aEntry.edgeType().objectTypeRef(), BytecodeObjectTypeRef.fromRuntimeClass(Address.class))) {
                return;
            }
            if (null != theLinkedClass.getBytecodeClass().getAttributes().getAnnotationByType(EmulatedByRuntime.class.getName())) {
                return;
            }

            final Set<BytecodeObjectTypeRef> theStaticReferences = new HashSet<>();

            final BytecodeResolvedMethods theMethodMap = theLinkedClass.resolvedMethods();
            theMethodMap.stream().forEach(aMethodMapEntry -> {

                final BytecodeMethod theMethod = aMethodMapEntry.getValue();
                final BytecodeMethodSignature theSignature = theMethod.getSignature();

                // If the method is provided by the runtime, we do not need to generate the implementation
                if (null != theMethod.getAttributes().getAnnotationByType(EmulatedByRuntime.class.getName())) {
                    return;
                }

                // Do not generate code for abstract methods
                if (theMethod.getAccessFlags().isAbstract()) {
                    return;
                }

                if (theMethod.getAccessFlags().isNative()) {
                    // Already written
                    return;
                }

                if (!(aMethodMapEntry.getProvidingClass() == theLinkedClass)) {
                    // Skip methods not implemented here
                    // Skip methods not implemented in this class
                    // But include static methods, as they are inherited from the base classes
                    if (aMethodMapEntry.getValue().getAccessFlags().isStatic() && !aMethodMapEntry.getValue().isClassInitializer()) {

                        // We need to create a delegate function here
                        if (!theMethodMap.isImplementedBy(aMethodMapEntry.getValue(), theLinkedClass)) {

                            final List<Param> params = new ArrayList<>();

                            params.add(param("UNUSED",toType(TypeRef.Native.REFERENCE)));

                            for (int i = 0; i < theSignature.getArguments().length; i++) {
                                params.add(param("p" + i, toType(TypeRef.toType(theSignature.getArguments()[i]))));
                            }

                            if (!theSignature.getReturnType().isVoid()) {
                                final PrimitiveType returnType = toType(TypeRef.toType(theSignature.getReturnType()));

                                final ExportableFunction function = module.getFunctions().newFunction(
                                        WASMWriterUtils
                                                .toMethodName(theLinkedClass.getClassName(), theMethod.getName(), theSignature),
                                        params,
                                        returnType
                                );

                                final List<Value> callValues = new ArrayList<>();
                                for (final Param p : params) {
                                    callValues.add(getLocal(p));
                                }
                                final Call call = call(function, callValues);
                                function.flow.ret(call);
                            } else {

                                final ExportableFunction function = module.getFunctions().newFunction(
                                        WASMWriterUtils
                                                .toMethodName(theLinkedClass.getClassName(), theMethod.getName(), theSignature),
                                        params
                                );

                                final List<Value> callValues = new ArrayList<>();
                                for (final Param p : params) {
                                    callValues.add(getLocal(p));
                                }
                                function.flow.voidCall(function, callValues);
                            }
                        }
                    }
                    return;
                }

                final ProgramGenerator theGenerator = programGeneratorFactory.createFor(aLinkerContext);
                final Program theSSAProgram = theGenerator.generateFrom(aMethodMapEntry.getProvidingClass().getBytecodeClass(), theMethod);

                //Run optimizer
                aOptions.getOptimizer().optimize(theSSAProgram.getControlFlowGraph(), aLinkerContext);

                final List<Param> params = new ArrayList<>();
                if (theMethod.getAccessFlags().isStatic()) {
                    params.add(param("UNUSED",toType(TypeRef.Native.REFERENCE)));
                }
                for (final Program.Argument theArgument : theSSAProgram.getArguments()) {
                    final Variable theVariable = theArgument.getVariable();
                    params.add(param(theVariable.getName() ,toType(theVariable.resolveType())));
                }
                final ExportableFunction instanceFunction;
                if (theSignature.getReturnType().isVoid()) {
                    instanceFunction = module.getFunctions().newFunction(WASMWriterUtils.toMethodName(theLinkedClass.getClassName(), theMethod.getName(), theSignature),
                            params);
                } else {
                    final PrimitiveType returnType = toType(TypeRef.toType(theSignature.getReturnType()));
                    instanceFunction = module.getFunctions().newFunction(WASMWriterUtils.toMethodName(theLinkedClass.getClassName(), theMethod.getName(), theSignature),
                            params,
                            returnType);
                }

                // Check if there is an export defined
                final BytecodeAnnotation theExport = theMethod.getAttributes().getAnnotationByType(Export.class.getName());
                if (null != theExport) {
                    instanceFunction.exportAs(theExport.getElementValueByName("value").stringValue());
                }

                theStaticReferences.addAll(theSSAProgram.getStaticReferences());

                // Try to reloop it!
                try {
                    final Relooper theRelooper = new Relooper();
                    final Relooper.Block theReloopedBlock = theRelooper.reloop(theSSAProgram.getControlFlowGraph());

                    // TODO: Write real programm logic here
                    instanceFunction.flow.unreachable();
                } catch (final Exception e) {
                    throw new IllegalStateException("Error relooping cfg", e);
                }
            });

            final String theClassName = WASMWriterUtils.toClassName(aEntry.edgeType().objectTypeRef());

            if (!theLinkedClass.getBytecodeClass().getAccessFlags().isInterface()) {
                // TODO: Write rtti stuff here
            }

            // TODO: Write class init check and class initializer logic here
        });

        final Global stackTop = module.getGlobals().newMutableGlobal("STACKTOP", PrimitiveType.i32, i32.c(-1));

        // TODO: Write general functions and bootstrap code here
        {
            final ExportableFunction bootstrap = module.getFunctions().newFunction("bootstrap", Collections.emptyList());
            bootstrap.flow.setGlobal(stackTop, i32.sub(i32.mul(currentMemory(), i32.c(65536)), i32.c(1)));

            bootstrap.exportAs("bootstrap");

            // Comparison of two f32
            {
                final Param p1 = param("p1", PrimitiveType.f32);
                final Param p2 = param("p2", PrimitiveType.f32);
                final ExportableFunction compareValueF32 = module.getFunctions().newFunction("compareValueF32", Arrays.asList(p1, p2), PrimitiveType.i32);
                final Block b1 = compareValueF32.flow.block("b1");
                b1.flow.branchIff(b1, f32.ne(getLocal(p1), getLocal(p2)));
                b1.flow.ret(i32.c(0));
                final Block b2 = compareValueF32.flow.block("b2");
                b2.flow.branchIff(b2, f32.ge(getLocal(p1), getLocal(p2)));
                b2.flow.ret(i32.c(-1));
                compareValueF32.flow.ret(i32.c(1));
            }
        }

        // Main function must be exported
        {
            final ExportableFunction mainFunction = module.getFunctions().firstByLabel(WASMWriterUtils
                    .toMethodName(BytecodeObjectTypeRef.fromRuntimeClass(aEntryPointClass), aEntryPointMethodName,
                            aEntryPointSignatue));
            mainFunction.exportAs("main");
        }

        final StringWriter theStringWriter = new StringWriter();
        try {
            final PrintWriter theWriter = new PrintWriter(theStringWriter);

            final Exporter exporter = new Exporter();
            exporter.export(module, theWriter);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        return new WASMCompileResult(
                new WASMCompileResult.WASMCompileContent(theMemoryLayout, aLinkerContext, new ArrayList<>(), theStringWriter.toString()));
    }
}