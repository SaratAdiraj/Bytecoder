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
package de.mirkosertic.bytecoder.unittest;

import com.sun.net.httpserver.HttpServer;
import de.mirkosertic.bytecoder.allocator.Allocator;
import de.mirkosertic.bytecoder.backend.CompileOptions;
import de.mirkosertic.bytecoder.backend.CompileTarget;
import de.mirkosertic.bytecoder.backend.js.JSCompileResult;
import de.mirkosertic.bytecoder.backend.wasm.WASMCompileResult;
import de.mirkosertic.bytecoder.core.BytecodeMethodSignature;
import de.mirkosertic.bytecoder.core.BytecodeObjectTypeRef;
import de.mirkosertic.bytecoder.core.BytecodePrimitiveTypeRef;
import de.mirkosertic.bytecoder.core.BytecodeTypeRef;
import de.mirkosertic.bytecoder.optimizer.KnownOptimizer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class BytecoderUnitTestRunner extends ParentRunner<FrameworkMethodWithTestOption> {

    private static final Slf4JLogger LOGGER = new Slf4JLogger();
    private final List<TestOption> testOptions;

    private static ChromeDriverService DRIVERSERVICE;
    private static HttpServer TESTSERVER;
    private static final AtomicReference<File> HTTPFILESDIR = new AtomicReference<>();

    public BytecoderUnitTestRunner(final Class aClass) throws InitializationError {
        super(aClass);

        testOptions = new ArrayList<>();
        final BytecoderTestOptions declaredOptions = getTestClass().getJavaClass().getAnnotation(BytecoderTestOptions.class);
        if (declaredOptions != null) {
            if (declaredOptions.includeJVM()) {
                testOptions.add(new TestOption(null, false, false, false));
            }
            if (declaredOptions.value().length == 0 && declaredOptions.includeTestPermutations()) {
                testOptions.add(new TestOption(CompileTarget.BackendType.js, false, false, false));
                testOptions.add(new TestOption(CompileTarget.BackendType.js, false, false, true));
                testOptions.add(new TestOption(CompileTarget.BackendType.js, true, false, false));
                testOptions.add(new TestOption(CompileTarget.BackendType.wasm, false, false, true));
                testOptions.add(new TestOption(CompileTarget.BackendType.wasm, true, false, true));
            } else {
                for (final BytecoderTestOption o : declaredOptions.value()) {
                    testOptions.add(new TestOption(o.backend(), o.preferStackifier(), o.exceptionsEnabled(), o.minify()));
                }
            }
        } else {
            testOptions.add(new TestOption(null, false, false, false));
            testOptions.add(new TestOption(CompileTarget.BackendType.js, false, false, false));
            testOptions.add(new TestOption(CompileTarget.BackendType.js, false, false, true));
            testOptions.add(new TestOption(CompileTarget.BackendType.js, true, false, false));
            testOptions.add(new TestOption(CompileTarget.BackendType.wasm, false, false, true));
            testOptions.add(new TestOption(CompileTarget.BackendType.wasm, true, false, true));
        }
    }

    @Override
    public Description getDescription() {
        final TestClass testClass = getTestClass();
        return Description.createSuiteDescription(testClass.getName(),
                testClass.getJavaClass().getAnnotations());
    }

    @Override
    protected List<FrameworkMethodWithTestOption> getChildren() {
        final List<FrameworkMethodWithTestOption> testMethods = new ArrayList<>();

        final TestClass testClass = getTestClass();
        final Method[] classMethods = testClass.getJavaClass().getDeclaredMethods();
        for (final Method classMethod : classMethods) {
            final Class retClass = classMethod.getReturnType();
            final int length = classMethod.getParameterTypes().length;
            final int modifiers = classMethod.getModifiers();
            if (null == retClass || 0 != length || Modifier.isStatic(modifiers)
                    || !Modifier.isPublic(modifiers) || Modifier.isInterface(modifiers)
                    || Modifier.isAbstract(modifiers)) {
                continue;
            }
            if (!classMethod.isAnnotationPresent(Ignore.class)) {
                final String methodName = classMethod.getName();
                if (methodName.toUpperCase().startsWith("TEST")
                        || null != classMethod.getAnnotation(Test.class)) {
                    for (final TestOption o : testOptions) {
                        testMethods.add(new FrameworkMethodWithTestOption(classMethod, o));
                    }
                }
            }
        }

        return testMethods;
    }

    @Override
    protected Description describeChild(final FrameworkMethodWithTestOption frameworkMethod) {
        final TestClass testClass = getTestClass();
        return Description.createTestDescription(testClass.getJavaClass(), frameworkMethod.getName());
    }

    private void testJVMBackendFrameworkMethod(final FrameworkMethod aFrameworkMethod, final RunNotifier aRunNotifier) {
        if ("".equals(System.getProperty("BYTECODER_DISABLE_JVMTESTS", ""))) {
            final TestClass testClass = getTestClass();
            final Description theDescription = Description.createTestDescription(testClass.getJavaClass(), aFrameworkMethod.getName() + " JVM Target");
            aRunNotifier.fireTestStarted(theDescription);
            try {
                // Simply invoke using reflection
                final Object theInstance = testClass.getJavaClass().getDeclaredConstructor().newInstance();
                final Method theMethod = aFrameworkMethod.getMethod();
                theMethod.invoke(theInstance);

                aRunNotifier.fireTestFinished(theDescription);
            } catch (final Exception e) {
                aRunNotifier.fireTestFailure(new Failure(theDescription, e));
            }
        }
    }

    private static synchronized void initializeSeleniumDriver() throws IOException {
        if (null == DRIVERSERVICE) {

            final String theChromeDriverBinary = System.getenv("CHROMEDRIVER_BINARY");
            if (null == theChromeDriverBinary || theChromeDriverBinary.isEmpty()) {
                throw new RuntimeException("No chromedriver binary found! Please set CHROMEDRIVER_BINARY environment variable!");
            }

            ChromeDriverService.Builder theDriverService = new ChromeDriverService.Builder();
            theDriverService = theDriverService.withVerbose(false).withLogFile(new File("chromedriver.log"));
            theDriverService = theDriverService.usingDriverExecutable(new File(theChromeDriverBinary));

            DRIVERSERVICE = theDriverService.build();
            DRIVERSERVICE.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> DRIVERSERVICE.stop()));
        }
    }

    private static void initializeTestWebServer() throws IOException {
        if (TESTSERVER == null) {
            TESTSERVER = HttpServer.create();
            final int port = Integer.parseInt(System.getProperty("BYTECODER_TESTSERVERPORT", "10000"));
            TESTSERVER.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 20);
            TESTSERVER.createContext("/", httpExchange -> {
                final File filesDir = HTTPFILESDIR.get();
                final String fileName = httpExchange.getRequestURI().getPath();
                final int lastSlash = fileName.lastIndexOf('/');
                final String requestedFileName = fileName.substring(lastSlash + 1);
                final File requestedFile = new File(filesDir, requestedFileName);
                if (requestedFile.exists()) {
                    if (requestedFileName.endsWith(".html")) {
                        httpExchange.getResponseHeaders().add("Content-Type", "text/html");
                    } else if (requestedFileName.endsWith(".js")) {
                        httpExchange.getResponseHeaders().add("Content-Type", "text/javascript");
                    } else if (requestedFileName.endsWith(".wasm")) {
                        httpExchange.getResponseHeaders().add("Content-Type", "application/wasm");
                    } else {
                        httpExchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                    }
                    httpExchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, requestedFile.length());
                    FileUtils.copyFile(requestedFile, httpExchange.getResponseBody());
                } else {
                    httpExchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0);
                }
                httpExchange.close();
            });
            TESTSERVER.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> TESTSERVER.stop(0)));
        }
    }

    private static void initializeWebRoot(final File aFile) {
        HTTPFILESDIR.set(aFile);
    }

    private static URL getTestFileUrl(final File aFile) throws MalformedURLException {
        final String theFileName = aFile.getName();
        final InetSocketAddress theServerAddress = TESTSERVER.getAddress();
        return new URL(String.format("http://%s:%d/%s", theServerAddress.getAddress().getHostAddress(), theServerAddress.getPort(), theFileName));
    }

    private WebDriver newDriverForTest() {
        final ChromeOptions theOptions = new ChromeOptions().setHeadless(true);
        theOptions.addArguments("--js-flags=experimental-wasm-eh");
        theOptions.addArguments("--enable-experimental-wasm-eh");
        theOptions.addArguments("disable-infobars"); // disabling infobars
        theOptions.addArguments("--disable-dev-shm-usage"); // overcome limited resource problems
        theOptions.addArguments("--no-sandbox"); // Bypass OS security model
        theOptions.setExperimentalOption("useAutomationExtension", false);
        final LoggingPreferences theLoggingPreferences = new LoggingPreferences();
        theLoggingPreferences.enable(LogType.BROWSER, Level.ALL);
        theOptions.setCapability(CapabilityType.LOGGING_PREFS, theLoggingPreferences);
        theOptions.setCapability("goog:loggingPrefs", theLoggingPreferences);

        return new RemoteWebDriver(DRIVERSERVICE.getUrl(), theOptions);
    }

    private void copyTestResources(final FrameworkMethod m, final File targetDirectory) throws IOException {
        final TestResource[] t = m.getMethod().getAnnotationsByType(TestResource.class);
        if (t != null) {
            for (final TestResource r : t) {
                final String name = r.value();
                final int p = name.lastIndexOf('/');
                final File theTargetFile = new File(targetDirectory, name.substring(p + 1));
                try (final FileOutputStream fos = new FileOutputStream(theTargetFile)) {
                    IOUtils.copy(m.getDeclaringClass().getResourceAsStream(name), fos);
                }
            }
        }
    }

    private void testJSBackendFrameworkMethod(final FrameworkMethod aFrameworkMethod, final RunNotifier aRunNotifier, final TestOption aTestOption) {
        if ("".equals(System.getProperty("BYTECODER_DISABLE_JSTESTS", ""))) {
            final TestClass testClass = getTestClass();
            final Description theDescription = Description.createTestDescription(testClass.getJavaClass(), aFrameworkMethod.getName() + " " + aTestOption.toDescription());
            aRunNotifier.fireTestStarted(theDescription);

            WebDriver theDriver = null;

            try {
                final CompileTarget theCompileTarget = new CompileTarget(testClass.getJavaClass().getClassLoader(), CompileTarget.BackendType.js);

                final BytecodeMethodSignature theSignature = theCompileTarget.toMethodSignature(aFrameworkMethod.getMethod());

                final BytecodeObjectTypeRef theTestClass = new BytecodeObjectTypeRef(testClass.getName());
                final BytecodeMethodSignature theTestClassConstructorSignature = new BytecodeMethodSignature(BytecodePrimitiveTypeRef.VOID, new BytecodeTypeRef[0]);

                final StringWriter theStrWriter = new StringWriter();
                final PrintWriter theCodeWriter = new PrintWriter(theStrWriter);

                final CompileOptions theOptions = new CompileOptions(LOGGER, true, KnownOptimizer.ALL, true, "bytecoder", 512, 512, aTestOption.isMinify(), aTestOption.isPreferStackifier(), Allocator.linear);
                final JSCompileResult result = (JSCompileResult) theCompileTarget.compile(theOptions, testClass.getJavaClass(), aFrameworkMethod.getName(), theSignature);
                final JSCompileResult.JSContent content = result.getContent()[0];

                theCodeWriter.println(content.asString());

                final String theFilename = result.getMinifier().toClassName(theTestClass) + "." + result.getMinifier().toMethodName(aFrameworkMethod.getName(), theSignature) + "_" + aTestOption.toFilePrefix() + ".html";

                theCodeWriter.println();

                theCodeWriter.println("console.log(\"Starting test\");");
                theCodeWriter.println("bytecoder.bootstrap();");
                theCodeWriter.println("var theTestInstance = " + result.getMinifier().toClassName(theTestClass) + "." + result.getMinifier().toMethodName("$newInstance", theTestClassConstructorSignature) + "();");
                theCodeWriter.println("try {");
                theCodeWriter.println("     theTestInstance." + result.getMinifier().toMethodName(aFrameworkMethod.getName(), theSignature) + "();");
                theCodeWriter.println("     console.log(\"Test finished OK\");");
                theCodeWriter.println("} catch (e) {");
                theCodeWriter.println("     if (e.exception) {");
                theCodeWriter.println("         console.log(\"Test finished with exception. Message = \" + bytecoder.toJSString(e.exception.message));");
                theCodeWriter.println("     } else {");
                theCodeWriter.println("         console.log(\"Test finished with exception.\");");
                theCodeWriter.println("     }");
                theCodeWriter.println("     console.log(e.stack);");
                theCodeWriter.println("}");

                theCodeWriter.flush();

                final File theWorkingDirectory = new File(".");

                initializeTestWebServer();
                initializeSeleniumDriver();

                final File theMavenTargetDir = new File(theWorkingDirectory, "target");
                final File theGeneratedFilesDir = new File(theMavenTargetDir, "bytecoderjs");
                theGeneratedFilesDir.mkdirs();

                copyTestResources(aFrameworkMethod, theGeneratedFilesDir);

                final File theGeneratedFile = new File(theGeneratedFilesDir, theFilename);
                final PrintWriter theWriter = new PrintWriter(theGeneratedFile);
                theWriter.println("<html><body><script>");
                theWriter.println(theStrWriter.toString());
                theWriter.println("</script></body></html>");
                theWriter.flush();
                theWriter.close();

                initializeWebRoot(theGeneratedFile.getParentFile());

                theDriver = newDriverForTest();

                final URL theTestURL = getTestFileUrl(theGeneratedFile);

                theDriver.get(theTestURL.toString());

                final List<LogEntry> theAll = theDriver.manage().logs().get(LogType.BROWSER).getAll();
                if (1 > theAll.size()) {
                    aRunNotifier.fireTestFailure(new Failure(theDescription, new RuntimeException("No console output from browser")));
                }
                for (final LogEntry theEntry : theAll) {
                    LOGGER.info(theEntry.getMessage());
                }
                final LogEntry theLast = theAll.get(theAll.size() - 1);

                if (!theLast.getMessage().contains("Test finished OK")) {
                    aRunNotifier.fireTestFailure(new Failure(theDescription, new RuntimeException("Test did not succeed! Got : " + theLast.getMessage())));
                }

            } catch (final Exception e) {
                aRunNotifier.fireTestFailure(new Failure(theDescription, e));
            } finally {
                if (null != theDriver) {
                    theDriver.close();
                    theDriver.quit();
                }
                aRunNotifier.fireTestFinished(theDescription);
            }
        }
    }

    private void testWASMASTBackendFrameworkMethod(final FrameworkMethod aFrameworkMethod, final RunNotifier aRunNotifier, final TestOption aTestOption) {
        if (!"".equals(System.getProperty("BYTECODER_DISABLE_WASMTESTS_STACKIFY", "")) && aTestOption.isPreferStackifier()) {
            return;
        }
        if (!"".equals(System.getProperty("BYTECODER_DISABLE_WASMTESTS_RELOOP", "")) && !aTestOption.isPreferStackifier()) {
            return;
        }
        if ("".equals(System.getProperty("BYTECODER_DISABLE_WASMTESTS", ""))) {
            final TestClass testClass = getTestClass();
            final Description theDescription = Description.createTestDescription(testClass.getJavaClass(), aFrameworkMethod.getName() + " " + aTestOption.toDescription());
            aRunNotifier.fireTestStarted(theDescription);

            WebDriver theDriver = null;

            try {
                final CompileTarget theCompileTarget = new CompileTarget(testClass.getJavaClass().getClassLoader(), CompileTarget.BackendType.wasm);

                final BytecodeMethodSignature theSignature = theCompileTarget.toMethodSignature(aFrameworkMethod.getMethod());
                final BytecodeObjectTypeRef theTypeRef = new BytecodeObjectTypeRef(testClass.getName());

                final CompileOptions theOptions = new CompileOptions(LOGGER, true, KnownOptimizer.ALL, false, "bytecoder", 512, 512, aTestOption.isMinify(), aTestOption.isPreferStackifier(), Allocator.linear);
                final WASMCompileResult theResult = (WASMCompileResult) theCompileTarget.compile(theOptions, testClass.getJavaClass(), aFrameworkMethod.getName(), theSignature);
                final WASMCompileResult.WASMCompileContent textualContent = theResult.getContent()[0];
                final WASMCompileResult.WASMCompileContent binaryContent = theResult.getContent()[1];
                final WASMCompileResult.WASMCompileContent jsContent = theResult.getContent()[2];
                final WASMCompileResult.WASMCompileContent sourceMapContent = theResult.getContent()[3];

                final String theFileName = theResult.getMinifier().toClassName(theTypeRef) + "." + theResult.getMinifier().toMethodName(aFrameworkMethod.getName(), theSignature) + "_" + aTestOption.toFilePrefix()+  ".html";

                final File theWorkingDirectory = new File(".");

                initializeTestWebServer();
                initializeSeleniumDriver();

                final File theMavenTargetDir = new File(theWorkingDirectory, "target");
                final File theGeneratedFilesDir = new File(theMavenTargetDir, "bytecoderwat");
                theGeneratedFilesDir.mkdirs();

                copyTestResources(aFrameworkMethod, theGeneratedFilesDir);

                final File theGeneratedFile = new File(theGeneratedFilesDir, theFileName);

                final PrintWriter theWriter = new PrintWriter(theGeneratedFile);
                theWriter.println("<html>");
                theWriter.println("    <body>");
                theWriter.println("        <h1>Module code</h1>");
                theWriter.println("        <h1>Compilation result</h1>");
                theWriter.println("        <pre id=\"compileresult\">");
                theWriter.println("        </pre>");
                theWriter.println("        <script>");

                theWriter.println(jsContent.asString());

                theWriter.println("            function compile() {");
                theWriter.println("                console.log('Test started');");
                theWriter.println("                try {");
                theWriter.println("                var features = {\n" +
                        "                         'exceptions' : true,\n" +
                        "                        'mutable_globals' : true,\n" +
                        "                        'sat_float_to_int' : true,\n" +
                        "                        'sign_extension' : true,\n" +
                        "                        'simd' : true,\n" +
                        "                        'threads' : true,\n" +
                        "                        'multi_value' : true,\n" +
                        "                        'tail_call' : true,\n" +
                        "                    };");

                theWriter.println();
                theWriter.print("                    var binaryBuffer = new Uint8Array([");
                try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    binaryContent.writeTo(bos);
                    bos.flush();
                    final byte[] theData = bos.toByteArray();
                    for (int i = 0; i < theData.length; i++) {
                        if (i > 0) {
                            theWriter.print(",");
                        }
                        theWriter.print(theData[i] & 0xFF);
                    }
                }

                theWriter.println("]);");

                theWriter.println("                    console.log('Size of compiled WASM binary is ' + binaryBuffer.length);");
                theWriter.println();
                theWriter.println("                    var theInstantiatePromise = WebAssembly.instantiate(binaryBuffer, bytecoder.imports);");
                theWriter.println("                    theInstantiatePromise.then(");
                theWriter.println("                         function (resolved) {");
                theWriter.println("                             var wasmModule = resolved.module;");
                theWriter.println("                             bytecoder.init(resolved.instance);");
                theWriter.println("                             bytecoder.exports.initMemory(0);");
                theWriter.println("                             console.log(\"Memory initialized\")");
                theWriter.println("                             bytecoder.exports.logMemoryLayout(0);");
                theWriter.println("                             console.log(\"Used memory in bytes \" + bytecoder.exports.usedMem());");
                theWriter.println("                             console.log(\"Free memory in bytes \" + bytecoder.exports.freeMem());");
                theWriter.println("                             bytecoder.exports.bootstrap(0);");
                theWriter.println("                             bytecoder.initializeFileIO();");
                theWriter.println("                             console.log(\"Used memory after bootstrap in bytes \" + bytecoder.exports.usedMem());");
                theWriter.println("                             console.log(\"Free memory after bootstrap in bytes \" + bytecoder.exports.freeMem());");
                theWriter.println("                             bytecoder.exports.logMemoryLayout(0);");
                theWriter.println("                             console.log(\"Creating test instance\")");

                theWriter.print("                             var theTest = bytecoder.exports.newObject(0,");
                theWriter.print(textualContent.getSizeOf(theTypeRef));
                theWriter.print(",");
                theWriter.print(textualContent.getTypeIDFor(theTypeRef));
                theWriter.print(",");
                theWriter.print(textualContent.getVTableIndexOf(theTypeRef));
                theWriter.println(", 0);");
                theWriter.println("                             bytecoder.exports.logMemoryLayout(0);");
                theWriter.println("                             console.log(\"Bootstrapped\")");
                theWriter.println("                             try {");
                theWriter.println("                                 bytecoder.exports.logMemoryLayout(0);");
                theWriter.println("                                 console.log(\"Starting main method\")");
                theWriter.println("                                 bytecoder.exports.main(theTest);");
                theWriter.println("                                 console.log(\"Main finished\")");
                theWriter.println("                                 // bytecoder.exports.logMemoryLayout(0);");
                theWriter.println("                                 // wasmHexDump(bytecoder.runningInstanceMemory);");
                theWriter.println("                                 console.log(\"Test finished OK\")");
                theWriter.println("                             } catch (e) {");
                theWriter.println("                                 console.log(\"Test threw error\")");
                theWriter.println("                                 // bytecoder.exports.logMemoryLayout(0);");
                theWriter.println("                                 // wasmHexDump(bytecoder.runningInstanceMemory);");
                theWriter.println("                                 throw e;");
                theWriter.println("                             }");
                theWriter.println("                         },");
                theWriter.println("                         function (rejected) {");
                theWriter.println("                             console.log(\"Error instantiating webassembly\");");
                theWriter.println("                             console.log(rejected);");
                theWriter.println("                         }");
                theWriter.println("                    );");
                theWriter.println("                } catch (e) {");
                theWriter.println("                    document.getElementById(\"compileresult\").innerText = e.toString();");
                theWriter.println("                    console.log(e.toString());");
                theWriter.println("                    console.log(e.stack);");
                theWriter.println("                    if (bytecoder.runningInstance) {");
                theWriter.println("                         // bytecoder.exports.logMemoryLayout(0);");
                theWriter.println("                         // wasmHexDump(bytecoder.runningInstanceMemory);");
                theWriter.println("                    }");
                theWriter.println("                }");
                theWriter.println("            }");
                theWriter.println();

                theWriter.println("            function wasmHexDump(memory) {");
                theWriter.println("                var theStart = 0;");
                theWriter.println("                console.log('HEX DUMP');");
                theWriter.println("                console.log('=================================================================================');");
                theWriter.println("                for (var i=0;i<200;i++) {");
                theWriter.println("                    var theLine = '' + theStart;");
                theWriter.println("                    while(theLine.length < 15) {");
                theWriter.println("                        theLine+= ' ';");
                theWriter.println("                    }");
                theWriter.println("                    theLine+= ' : ';");
                theWriter.println("                    for (var j=0;j<32;j++) {");
                theWriter.println("                        var theByte = memory[theStart++];");
                theWriter.println("                        var theData = '' + theByte;");
                theWriter.println("                        while(theData.length < 3) {");
                theWriter.println("                            theData = ' ' + theData;");
                theWriter.println("                        }");
                theWriter.println("                        theLine += theData;");
                theWriter.println("                        theLine += ' ';");
                theWriter.println("                    }");
                theWriter.println("                    console.log(theLine);");
                theWriter.println("                }");
                theWriter.println("                console.log('DONE');");
                theWriter.println("            }");
                theWriter.println();
                theWriter.println("            compile();");
                theWriter.println("        </script>");
                theWriter.println("    </body>");
                theWriter.println("</html>");

                theWriter.flush();
                theWriter.close();

                try (final FileOutputStream fos = new FileOutputStream(new File(theGeneratedFilesDir, theResult.getMinifier().toClassName(theTypeRef) + "." + theResult.getMinifier().toMethodName(aFrameworkMethod.getName(), theSignature) + "_" + aTestOption.toFilePrefix() + ".wat"))) {
                    textualContent.writeTo(fos);
                }

                try (final FileOutputStream fos = new FileOutputStream(new File(theGeneratedFilesDir, theResult.getMinifier().toClassName(theTypeRef) + "." + theResult.getMinifier().toMethodName(aFrameworkMethod.getName(), theSignature) + "_" + aTestOption.toFilePrefix() + ".js"))) {
                    jsContent.writeTo(fos);
                }

                try (final FileOutputStream fos = new FileOutputStream(new File(theGeneratedFilesDir, theResult.getMinifier().toClassName(theTypeRef) + "." + theResult.getMinifier().toMethodName(aFrameworkMethod.getName(), theSignature) + "_" + aTestOption.toFilePrefix() + ".wasm"))) {
                    binaryContent.writeTo(fos);
                }

                try (final FileOutputStream fos = new FileOutputStream(new File(theGeneratedFilesDir, theResult.getMinifier().toClassName(theTypeRef) + "." + theResult.getMinifier().toMethodName(aFrameworkMethod.getName(), theSignature) + "_" + aTestOption.toFilePrefix() + ".wasm.map"))) {
                    sourceMapContent.writeTo(fos);
                }

                initializeWebRoot(theGeneratedFile.getParentFile());

                // Invoke test in browser
                theDriver = newDriverForTest();

                final URL theTestURL = getTestFileUrl(theGeneratedFile);

                theDriver.get(theTestURL.toString());

                final long theStart = System.currentTimeMillis();
                boolean theTestSuccedded = false;

                while (!theTestSuccedded && 10 * 1000 > System.currentTimeMillis() - theStart) {
                    final List<LogEntry> theAll = theDriver.manage().logs().get(LogType.BROWSER).getAll();
                    for (final LogEntry theEntry : theAll) {
                        final String theMessage = theEntry.getMessage();
                        System.out.println(theMessage);

                        if (theMessage.contains("Test finished OK")) {
                            theTestSuccedded = true;
                        }
                    }

                    if (!theTestSuccedded) {
                        Thread.sleep(100);
                    }
                }

                if (!theTestSuccedded) {
                    aRunNotifier.fireTestFailure(new Failure(theDescription, new RuntimeException("Test did not succeed!")));
                }

            } catch (final Exception e) {
                aRunNotifier.fireTestFailure(new Failure(theDescription, e));
            } finally {
                if (null != theDriver) {
                    theDriver.close();
                    theDriver.quit();
                }
                aRunNotifier.fireTestFinished(theDescription);
            }
        }
    }

    @Override
    protected void runChild(final FrameworkMethodWithTestOption aFrameworkMethod, final RunNotifier aRunNotifier) {
        final TestOption o = aFrameworkMethod.getTestOption();
        if (o.getBackendType() == null) {
            testJVMBackendFrameworkMethod(aFrameworkMethod, aRunNotifier);
        } else {
            switch (o.getBackendType()) {
                case js:
                    testJSBackendFrameworkMethod(aFrameworkMethod, aRunNotifier, o);
                    break;
                case wasm:
                    testWASMASTBackendFrameworkMethod(aFrameworkMethod, aRunNotifier, o);
                    break;
                default:
                    throw new IllegalStateException("Unsupported backend :" + o.getBackendType());
            }
        }
    }
}