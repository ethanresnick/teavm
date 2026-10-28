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
package org.teavm.backend.javascript.rendering;

import com.carrotsearch.hppc.ObjectIntHashMap;
import com.carrotsearch.hppc.ObjectIntMap;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.IntFunction;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.ast.ControlFlowEntry;
import org.teavm.ast.MethodNode;
import org.teavm.ast.RegularMethodNode;
import org.teavm.ast.analysis.LocationGraphBuilder;
import org.teavm.ast.decompilation.DecompilationException;
import org.teavm.ast.decompilation.Decompiler;
import org.teavm.backend.javascript.ExportedDeclaration;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.spi.GeneratedBy;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.InjectedBy;
import org.teavm.backend.javascript.templating.JavaScriptTemplateFactory;
import org.teavm.cache.AstCacheEntry;
import org.teavm.cache.AstDependencyExtractor;
import org.teavm.cache.CacheStatus;
import org.teavm.cache.MethodNodeCache;
import org.teavm.common.ServiceRepository;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.model.util.AsyncMethodFinder;
import org.teavm.model.util.ClassInitializationErrorDependencyListener;
import org.teavm.model.util.ClassInitializationLockDependencyListener;
import org.teavm.runtime.ClassInitializationLock;
import org.teavm.vm.RenderingException;
import org.teavm.vm.TeaVMProgressFeedback;

public class Renderer implements RenderingManager {
    // Linked only when the program has an @Async method, which an async initializer implies
    private static final MethodReference INITIALIZATION_ENTER = ClassInitializationLockDependencyListener.ENTER;
    private static final MethodReference INITIALIZATION_EXIT = ClassInitializationLockDependencyListener.EXIT;
    public static final int SECTION_STRING_POOL = 0;
    public static final int SECTION_METADATA = 1;

    private final SourceWriter writer;
    private final ListableClassReaderSource classSource;
    private final ClassReaderSource originalClassSource;
    private final ClassLoader classLoader;
    private final Properties properties = new Properties();
    private final ServiceRepository services;
    private final Set<MethodReference> asyncMethods;
    private RenderingContext context;
    private List<PostponedFieldInitializer> postponedFieldInitializers = new ArrayList<>();
    private IntFunction<TeaVMProgressFeedback> progressConsumer = p -> TeaVMProgressFeedback.CONTINUE;
    private MethodBodyRenderer methodBodyRenderer;
    private Map<String, Generator> generatorCache = new HashMap<>();
    private Map<MethodReference, Generator> generators;
    private MethodNodeCache astCache;
    private CacheStatus cacheStatus;
    private JavaScriptTemplateFactory templateFactory;
    private boolean threadLibraryUsed;
    private AstDependencyExtractor dependencyExtractor = new AstDependencyExtractor();
    private List<ExportedDeclaration> exports;
    private String entryPoint;
    private VariableNameGenerator variableNameGenerator;

    public static final MethodDescriptor CLINIT_METHOD = new MethodDescriptor("<clinit>", ValueType.VOID);

    public Renderer(SourceWriter writer, SourceWriter metadataWriter, Set<MethodReference> asyncMethods,
            RenderingContext context, Diagnostics diagnostics, Map<MethodReference, Generator> generators,
            MethodNodeCache astCache, CacheStatus cacheStatus, JavaScriptTemplateFactory templateFactory,
            List<ExportedDeclaration> exports, String entryPoint) {
        this.writer = writer;
        this.classSource = context.getClassSource();
        this.originalClassSource = context.getInitialClassSource();
        this.classLoader = context.getClassLoader();
        this.services = context.getServices();
        this.asyncMethods = new HashSet<>(asyncMethods);
        this.context = context;
        variableNameGenerator = new VariableNameGenerator(context.isMinifying());
        methodBodyRenderer = new MethodBodyRenderer(context, diagnostics, context.isMinifying(), asyncMethods,
                writer, metadataWriter, variableNameGenerator);
        this.generators = generators;
        this.astCache = astCache;
        this.cacheStatus = cacheStatus;
        this.templateFactory = templateFactory;
        this.exports = exports;
        this.entryPoint = entryPoint;
    }

    @Override
    public SourceWriter getWriter() {
        return writer;
    }

    @Override
    public String getEntryPoint() {
        return entryPoint;
    }

    @Override
    public void exportMethod(MethodReference method, String alias) {
        exports.add(new ExportedDeclaration(w -> w.appendMethod(method), n -> n.methodName(method), alias));
    }

    @Override
    public void exportClass(String className, String alias) {
        exports.add(new ExportedDeclaration(w -> w.appendClass(className), n -> n.className(className), alias));
    }

    @Override
    public void exportFunction(String functionName, String alias) {
        exports.add(new ExportedDeclaration(w -> w.appendFunction(functionName),
                n -> n.functionName(functionName), alias));
    }

    public boolean isThreadLibraryUsed() {
        return threadLibraryUsed;
    }

    @Override
    public ListableClassReaderSource getClassSource() {
        return classSource;
    }

    @Override
    public ClassReaderSource getOriginalClassSource() {
        return originalClassSource;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public Properties getProperties() {
        Properties properties = new Properties();
        properties.putAll(this.properties);
        return properties;
    }

    public void setProgressConsumer(IntFunction<TeaVMProgressFeedback> progressConsumer) {
        this.progressConsumer = progressConsumer;
    }

    public void setProperties(Properties properties) {
        this.properties.clear();
        this.properties.putAll(properties);
    }

    public void renderStringPool() throws RenderingException {
        if (context.getStringPool().isEmpty()) {
            return;
        }
        writer.markSectionStart(SECTION_STRING_POOL);
        writer.appendFunction("$rt_stringPool").append("([");
        for (int i = 0; i < context.getStringPool().size(); ++i) {
            if (i > 0) {
                writer.append(',').ws();
            }
            RenderingUtil.writeString(writer, context.getStringPool().get(i));
        }
        writer.append("]);").newLine();
        writer.markSectionEnd();
    }

    public void renderStringConstants() throws RenderingException {
        for (PostponedFieldInitializer initializer : postponedFieldInitializers) {
            writer.markSectionStart(SECTION_STRING_POOL);
            writer.appendStaticField(initializer.field).ws().append("=").ws();
            context.constantToString(writer, initializer.value);
            writer.append(";").softNewLine();
            writer.markSectionEnd();
        }
    }

    public void renderReflection() {

    }

    public void renderCompatibilityStubs() throws RenderingException {
        renderJavaStringToString();
        renderJavaObjectToString();
        renderTeaVMClass();
    }

    private void renderJavaStringToString() {
        writer.appendClass("java.lang.String").append(".prototype.toString").ws().append("=").ws()
                .append("function()").ws().append("{").indent().softNewLine();
        writer.append("return ").appendFunction("$rt_ustr").append("(this);").softNewLine();
        writer.outdent().append("};").newLine();
        writer.appendClass("java.lang.String").append(".prototype.valueOf").ws().append("=").ws()
            .appendClass("java.lang.String").append(".prototype.toString;").softNewLine();
    }

    private void renderJavaObjectToString() {
        writer.appendClass("java.lang.Object").append(".prototype.toString").ws().append("=").ws()
                .append("function()").ws().append("{").indent().softNewLine();
        writer.append("return ").appendFunction("$rt_ustr").append("(")
                .appendMethod(Object.class, "toString", String.class).append("(this));")
                .softNewLine();
        writer.outdent().append("};").newLine();
    }

    private void renderTeaVMClass() {
        writer.appendClass("java.lang.Object").append(".prototype.__teavm_class__").ws().append("=").ws()
                .append("function()").ws().append("{").indent().softNewLine();
        writer.append("return ").appendFunction("$dbg_class").append("(this);").softNewLine();
        writer.outdent().append("};").newLine();
    }

    public boolean render(ListableClassHolderSource classes, boolean isFriendlyToDebugger) {
        var sequence = new ArrayList<ClassHolder>();
        var visited = new HashSet<String>();
        for (String className : classes.getClassNames()) {
            orderClasses(classes, className, visited, sequence);
        }

        var asyncFinder = new AsyncMethodFinder(context.getDependencyInfo().getCallGraph(),
                context.getDependencyInfo());
        asyncFinder.find(classes);
        asyncMethods.addAll(asyncFinder.getAsyncMethods());
        var splitMethods = new HashSet<>(asyncMethods);
        splitMethods.addAll(asyncFinder.getAsyncFamilyMethods());

        var decompiler = new Decompiler(classes, splitMethods, isFriendlyToDebugger);

        int index = 0;
        for (var cls : sequence) {
            writer.markClassStart(cls.getName());
            renderDeclaration(cls);
            renderMethodBodies(cls, decompiler);
            writer.markClassEnd();
            if (progressConsumer.apply(1000 * ++index / sequence.size()) == TeaVMProgressFeedback.CANCEL) {
                return false;
            }
        }
        renderClassMetadata(sequence);
        return true;
    }

    private void orderClasses(ClassHolderSource classes, String className, Set<String> visited,
            List<ClassHolder> order) {
        if (!visited.add(className)) {
            return;
        }
        ClassHolder cls = classes.get(className);
        if (cls == null) {
            return;
        }
        if (cls.getParent() != null) {
            orderClasses(classes, cls.getParent(), visited, order);
        }
        for (String iface : cls.getInterfaces()) {
            orderClasses(classes, iface, visited, order);
        }
        order.add(cls);
    }

    private void renderDeclaration(ClassHolder cls) throws RenderingException {
        List<FieldHolder> nonStaticFields = new ArrayList<>();
        List<FieldHolder> staticFields = new ArrayList<>();
        for (FieldHolder field : cls.getFields()) {
            if (field.getModifiers().contains(ElementModifier.STATIC)) {
                staticFields.add(field);
            } else {
                nonStaticFields.add(field);
            }
        }

        if (nonStaticFields.isEmpty() && !cls.getName().equals("java.lang.Object")) {
            renderShortClassFunctionDeclaration(cls);
        } else {
            renderFullClassFunctionDeclaration(cls, nonStaticFields);
        }

        for (FieldHolder field : staticFields) {
            Object value = field.getInitialValue();
            if (value == null) {
                value = getDefaultValue(field.getType());
            }
            FieldReference fieldRef = new FieldReference(cls.getName(), field.getName());
            if (value instanceof String) {
                context.lookupString((String) value);
                postponedFieldInitializers.add(new PostponedFieldInitializer(fieldRef, (String) value));
                value = null;
            }

            writer.startVariableDeclaration().appendStaticField(fieldRef);
            context.constantToString(writer, value);
            writer.endDeclaration();
        }
    }

    private void renderFullClassFunctionDeclaration(ClassReader cls, List<FieldHolder> nonStaticFields) {
        boolean thisAliased = false;
        writer.startFunctionDeclaration().appendClass(cls.getName()).append("()").ws().append("{")
                .indent().softNewLine();
        if (nonStaticFields.size() > 1) {
            thisAliased = true;
            writer.append("let a").ws().append("=").ws().append("this;").ws();
        }
        if (!cls.readModifiers().contains(ElementModifier.INTERFACE)
                && cls.getParent() != null) {
            writer.appendClass(cls.getParent()).append(".call(").append(thisAliased ? "a" : "this")
                    .append(");").softNewLine();
        }
        for (FieldHolder field : nonStaticFields) {
            Object value = field.getInitialValue();
            if (value == null) {
                value = getDefaultValue(field.getType());
            }
            FieldReference fieldRef = new FieldReference(cls.getName(), field.getName());
            writer.append(thisAliased ? "a" : "this").append(".").appendField(fieldRef).ws()
                    .append("=").ws();
            context.constantToString(writer, value);
            writer.append(";").softNewLine();
        }

        if (cls.getName().equals("java.lang.Object")) {
            writer.append("this.$id$").ws().append('=').ws().append("0;").softNewLine();
        }

        writer.outdent().append("}");
        writer.endDeclaration();
    }

    private void renderShortClassFunctionDeclaration(ClassReader cls) {
        writer.startVariableDeclaration().appendClass(cls.getName())
                .appendFunction("$rt_classWithoutFields").append("(");
        if (cls.hasModifier(ElementModifier.INTERFACE)) {
            writer.append("0");
        } else if (!cls.getParent().equals("java.lang.Object")) {
            writer.appendClass(cls.getParent());
        }
        writer.append(")").endDeclaration();
    }

    private void renderMethodBodies(ClassHolder cls, Decompiler decompiler) {
        writer.emitClass(cls.getName());

        MethodReader clinit = classSource.get(cls.getName()).getMethod(CLINIT_METHOD);

        if (clinit != null && context.isDynamicInitializer(cls.getName())) {
            renderCallClinit(clinit, cls);
        }

        var needsInitializers = !cls.hasModifier(ElementModifier.INTERFACE)
            && !cls.hasModifier(ElementModifier.ABSTRACT);
        for (var method : cls.getMethods()) {
            if (!filterMethod(method)) {
                continue;
            }
            var isFunction = context.isForcedFunction(method.getReference());
            if (isFunction) {
                writer.startFunctionDeclaration();
            } else {
                writer.startVariableDeclaration();
            }
            renderBody(method, decompiler, isFunction);
            writer.endDeclaration();
            if (needsInitializers && !method.hasModifier(ElementModifier.STATIC)
                    && method.getName().equals("<init>")) {
                renderInitializer(method);
            }
        }

        writer.emitClass(null);
    }

    private boolean filterMethod(MethodReader method) {
        if (method.hasModifier(ElementModifier.ABSTRACT)) {
            return false;
        }
        if (method.getAnnotations().get(InjectedBy.class.getName()) != null
                || context.getInjector(method.getReference()) != null) {
            return false;
        }
        if (!method.hasModifier(ElementModifier.NATIVE) && method.getProgram() == null) {
            return false;
        }
        return true;
    }

    private void renderCallClinit(MethodReader clinit, ClassReader cls) {
        if (asyncMethods.contains(clinit.getReference())) {
            renderAsyncCallClinit(clinit, cls);
            return;
        }

        writer.startVariableDeclaration().appendClassInit(cls.getName());
        writer.append("()").sameLineWs().append("=>").ws().append("{").softNewLine().indent();
        renderEraseClinit(cls);

        writer.append("try").ws().append("{").indent().softNewLine();
        writer.appendMethod(new MethodReference(cls.getName(), clinit.getDescriptor()))
                .append("();").softNewLine();
        writer.outdent().append("}").ws().append("catch").ws().append("($$e)").ws().append("{").indent()
                .softNewLine();
        if (ClassInitializationErrorDependencyListener.canFail(context.getDependencyInfo(), cls.getName())) {
            writer.append("let $cause").ws().append("=").ws().appendFunction("$rt_saveClinitError")
                    .append("($$e);").softNewLine();
            writer.appendClassInit(cls.getName()).ws().append("=").ws().append("()").ws().append("=>").ws()
                    .appendFunction("$rt_failedClinitCause").append("($cause);").softNewLine();
        } else {
            writer.appendClassInit(cls.getName()).ws().append("=").ws().appendFunction("$rt_failedClinit")
                    .append(";").softNewLine();
        }
        writer.append("throw ").appendFunction("$rt_clinitError").append("($$e);").softNewLine();
        writer.outdent().append("}").softNewLine();

        writer.outdent().append("}").endDeclaration();
    }

    // An initializer that can suspend follows JLS 12.4.2, steps 2 and 3: while it runs, another thread that uses
    // the class waits until it ends (ClassInitializationLock). A recursive use on the same thread returns at once.
    // A waiting thread resumes through the same function, so this function is never replaced: a state field
    // records whether the initializer ended (1) or failed (2).
    private void renderAsyncCallClinit(MethodReader clinit, ClassReader cls) {
        var ptr = context.pointerName();
        var stateField = new FieldReference(cls.getName(), "$_teavm_clinitState_$");
        writer.startVariableDeclaration().appendStaticField(stateField).append("0").endDeclaration();
        FieldReference errorField = null;
        if (ClassInitializationErrorDependencyListener.canFail(context.getDependencyInfo(), cls.getName())) {
            errorField = new FieldReference(cls.getName(), "$_teavm_clinitError_$");
            writer.startVariableDeclaration().appendStaticField(errorField).append("null").endDeclaration();
        }

        writer.startVariableDeclaration().appendClassInit(cls.getName());
        writer.append("()").sameLineWs().append("=>").ws().append("{").softNewLine().indent();

        writer.append("let ").append(ptr).ws().append("=").ws().append("0,").ws().append("$initError")
                .ws().append("=").ws().append("null,").ws().append("$initState;").softNewLine();
        writer.append("if").ws().append("(").appendFunction("$rt_resuming").append("())").ws().append("{")
                .indent().softNewLine();
        writer.append("let $T").ws().append("=").ws().appendFunction("$rt_nativeThread").append("();")
                .softNewLine();
        writer.append("$initError").ws().append("=").ws().append("$T.pop();").softNewLine();
        writer.append(ptr).ws().append("=").ws().append("$T.pop();").softNewLine();
        // A space, not ws(): the minified output drops ws(), and "elseif" is not JavaScript
        writer.outdent().append("}").ws().append("else ");
        renderReturnIfInitialized(stateField, errorField);

        renderAsyncPrologue(writer, context);

        writer.append("case 0:").indent().softNewLine();
        writer.append("$initState").ws().append("=").ws().appendMethod(INITIALIZATION_ENTER).append("(");
        context.constantToString(writer, cls.getName());
        writer.append(");").softNewLine();
        renderBreakIfSuspending();
        writer.append("if").ws().append("($initState").ws().append("===").ws()
                .append(String.valueOf(ClassInitializationLock.WAITED)).append(")").ws();
        renderReturnIfInitialized(stateField, errorField);
        writer.append("if").ws().append("($initState").ws().append("===").ws()
                .append(String.valueOf(ClassInitializationLock.RECURSIVE)).append(")").ws().append("return;")
                .softNewLine();
        writer.append(ptr).ws().append("=").ws().append("1;").softNewLine();

        writer.outdent().append("case 1:").indent().softNewLine();
        writer.append("try").ws().append("{").indent().softNewLine();
        writer.appendMethod(new MethodReference(cls.getName(), clinit.getDescriptor()))
                .append("();").softNewLine();
        writer.outdent().append("}").ws().append("catch").ws().append("($$e)").ws().append("{").indent()
                .softNewLine();
        if (errorField != null) {
            writer.appendStaticField(errorField).ws().append("=").ws().appendFunction("$rt_saveClinitError")
                    .append("($$e);").softNewLine();
        }
        writer.appendStaticField(stateField).ws().append("=").ws().append("2;").softNewLine();
        writer.append("$initError").ws().append("=").ws().append("$$e;").softNewLine();
        writer.outdent().append("}").softNewLine();
        renderBreakIfSuspending();
        writer.append("if").ws().append("($initError").ws().append("===").ws().append("null)").ws();
        writer.appendStaticField(stateField).ws().append("=").ws().append("1;").softNewLine();
        writer.append(ptr).ws().append("=").ws().append("2;").softNewLine();

        writer.outdent().append("case 2:").indent().softNewLine();
        writer.appendMethod(INITIALIZATION_EXIT).append("(");
        context.constantToString(writer, cls.getName());
        writer.append(");").softNewLine();
        renderBreakIfSuspending();
        writer.append("if").ws().append("($initError").ws().append("!==").ws().append("null)").ws()
                .append("throw ").appendFunction("$rt_clinitError").append("($initError);").softNewLine();
        writer.append("return;").softNewLine().outdent();

        renderAsyncEpilogue(writer);
        writer.append("let $T").ws().append("=").ws().appendFunction("$rt_nativeThread").append("();")
                .softNewLine();
        writer.append("$T.push(").append(ptr).append(");").softNewLine();
        writer.append("$T.push($initError);").softNewLine();

        writer.outdent().append("}").endDeclaration();
    }

    // Returns when the initializer ended, and throws NoClassDefFoundError when it failed
    private void renderReturnIfInitialized(FieldReference stateField, FieldReference errorField) {
        writer.append("if").ws().append("(").appendStaticField(stateField).ws().append("!==").ws().append("0)")
                .ws().append("{").indent().softNewLine();
        writer.append("if").ws().append("(").appendStaticField(stateField).ws().append("===").ws().append("2)").ws();
        if (errorField != null) {
            writer.appendFunction("$rt_failedClinitCause").append("(").appendStaticField(errorField).append(");")
                    .softNewLine();
        } else {
            writer.appendFunction("$rt_failedClinit").append("();").softNewLine();
        }
        writer.append("return;").softNewLine();
        writer.outdent().append("}").softNewLine();
    }

    private void renderBreakIfSuspending() {
        writer.append("if").ws().append("(").appendFunction("$rt_suspending").append("())").ws().append("{")
                .indent().softNewLine();
        writer.append("break ").append(context.mainLoopName()).append(";").softNewLine();
        writer.outdent().append("}").softNewLine();
    }

    private void renderEraseClinit(ClassReader cls) {
        writer.appendClassInit(cls.getName()).ws().append("=").ws()
                .appendFunction("$rt_eraseClinit").append("(")
                .appendClass(cls.getName()).append(");").softNewLine();
    }

    private void renderClassMetadata(List<? extends ClassReader> classReaders) {
        ClassMetadataRequirements metadataRequirements = new ClassMetadataRequirements(context.getDependencyInfo());

        writer.markSectionStart(SECTION_METADATA);

        writer.appendFunction("$rt_packages").append("([");
        ObjectIntMap<String> packageIndexes = generatePackageMetadata(classReaders, metadataRequirements);
        writer.append("]);").newLine();

        for (int i = 0; i < classReaders.size(); i += 50) {
            int j = Math.min(i + 50, classReaders.size());
            renderClassMetadataPortion(classReaders.subList(i, j), packageIndexes, metadataRequirements);
        }

        writer.markSectionEnd();
    }

    private void renderClassMetadataPortion(List<? extends ClassReader> classes, ObjectIntMap<String> packageIndexes,
            ClassMetadataRequirements metadataRequirements) {
        writer.appendFunction("$rt_metadata").append("([");
        boolean first = true;
        for (var cls : classes) {
            if (!first) {
                writer.append(',').softNewLine();
            }
            first = false;
            writer.emitClass(cls.getName());
            writer.appendClass(cls.getName()).append(",").ws();

            var className = cls.getName();
            var requiredMetadata = metadataRequirements.getInfo(className);
            if (requiredMetadata.name()) {
                int dotIndex = className.lastIndexOf('.') + 1;
                String packageName = className.substring(0, dotIndex);
                className = className.substring(dotIndex);
                writer.append("\"").append(RenderingUtil.escapeString(className)).append("\"").append(",").ws();
                writer.append(String.valueOf(packageIndexes.getOrDefault(packageName, -1)));
            } else {
                writer.append("0");
            }
            writer.append(",").ws();

            if (cls.getParent() != null) {
                writer.appendClass(cls.getParent());
            } else {
                writer.append("0");
            }
            writer.append(',').ws();
            writer.append("[");
            var interfaces = new ArrayList<>(cls.getInterfaces());
            for (int i = 0; i < interfaces.size(); ++i) {
                String iface = interfaces.get(i);
                if (i > 0) {
                    writer.append(",").ws();
                }
                writer.appendClass(iface);
            }
            writer.append("],").ws();

            var modifiers = ElementModifier.encodeModifiers(cls);
            writer.append(modifiers).append(',').ws();

            if (!requiredMetadata.enclosingClass() && !requiredMetadata.declaringClass()
                    && !requiredMetadata.simpleName()) {
                writer.append("0");
            } else {
                writer.append('[');
                if (requiredMetadata.enclosingClass() && cls.getOwnerName() != null) {
                    writer.appendClass(cls.getOwnerName());
                } else {
                    writer.append('0');
                }
                writer.append(',');
                if (requiredMetadata.declaringClass() && cls.getDeclaringClassName() != null) {
                    writer.appendClass(cls.getDeclaringClassName());
                } else {
                    writer.append('0');
                }
                writer.append(',');
                if (requiredMetadata.simpleName() && cls.getSimpleName() != null) {
                    writer.append("\"").append(RenderingUtil.escapeString(cls.getSimpleName()))
                            .append("\"");
                } else {
                    writer.append('0');
                }
                writer.append(']');
            }
            writer.append(",").ws();

            MethodReader clinit = classSource.get(cls.getName()).getMethod(CLINIT_METHOD);
            if (clinit != null && context.isDynamicInitializer(cls.getName())) {
                writer.append("()").sameLineWs().append("=>").ws().appendClassInit(cls.getName())
                        .append("()");
            } else {
                writer.append('0');
            }
            writer.append(',').ws();

            Map<MethodDescriptor, MethodReference> virtualMethods = new LinkedHashMap<>();
            collectMethodsToCopyFromInterfaces(classSource.get(cls.getName()), virtualMethods);
            for (var method : cls.getMethods()) {
                if (filterMethod(method) && !method.readModifiers().contains(ElementModifier.STATIC)
                        && method.getLevel() != AccessLevel.PRIVATE) {
                    virtualMethods.put(method.getDescriptor(), method.getReference());
                }
            }

            renderVirtualDeclarations(virtualMethods.values());
            writer.emitClass(null);
        }
        writer.append("]);").newLine();
    }

    private ObjectIntMap<String> generatePackageMetadata(List<? extends ClassReader> classes,
            ClassMetadataRequirements metadataRequirements) {
        PackageNode root = new PackageNode(null);

        for (var cls : classes) {
            var requiredMetadata = metadataRequirements.getInfo(cls.getName());
            if (!requiredMetadata.name()) {
                continue;
            }

            var className = cls.getName();
            int dotIndex = className.lastIndexOf('.');
            if (dotIndex < 0) {
                continue;
            }

            addPackageName(root, className.substring(0, dotIndex));
        }

        ObjectIntMap<String> indexes = new ObjectIntHashMap<>();
        writePackageStructure(root, -1, "", indexes);
        writer.softNewLine();
        return indexes;
    }

    private int writePackageStructure(PackageNode node, int startIndex, String prefix, ObjectIntMap<String> indexes) {
        int index = startIndex;
        for (PackageNode child : node.children.values()) {
            if (index >= 0) {
                writer.append(",").ws();
            }
            writer.append(String.valueOf(startIndex)).append(",").ws()
                    .append("\"").append(RenderingUtil.escapeString(child.name)).append("\"");
            String fullName = prefix + child.name + ".";
            ++index;
            indexes.put(fullName, index);
            index = writePackageStructure(child, index, fullName, indexes);
        }
        return index;
    }

    static class PackageNode {
        String name;
        Map<String, PackageNode> children = new HashMap<>();

        PackageNode(String name) {
            this.name = name;
        }
    }

    private void addPackageName(PackageNode node, String name) {
        String[] parts = name.split("\\.");
        for (String part : parts) {
            node = node.children.computeIfAbsent(part, PackageNode::new);
        }
    }

    private void collectMethodsToCopyFromInterfaces(ClassReader cls, Map<MethodDescriptor, MethodReference> target) {
        Set<MethodDescriptor> implementedMethods = new HashSet<>();
        ClassReader superclass = cls;
        while (superclass != null) {
            for (MethodReader method : superclass.getMethods()) {
                if (method.getLevel() != AccessLevel.PRIVATE && !method.hasModifier(ElementModifier.STATIC)
                        && !method.hasModifier(ElementModifier.ABSTRACT)
                        && !method.getName().equals("<init>")) {
                    implementedMethods.add(method.getDescriptor());
                }
            }
            superclass = superclass.getParent() != null ? classSource.get(superclass.getParent()) : null;
        }

        Set<String> visitedClasses = new HashSet<>();
        superclass = cls;
        while (superclass != null) {
            for (String ifaceName : superclass.getInterfaces()) {
                ClassReader iface = classSource.get(ifaceName);
                if (iface != null) {
                    collectMethodsToCopyFromInterfacesImpl(iface, target, implementedMethods, visitedClasses);
                }
            }
            superclass = superclass.getParent() != null ? classSource.get(superclass.getParent()) : null;
        }
    }

    private void collectMethodsToCopyFromInterfacesImpl(ClassReader cls, Map<MethodDescriptor, MethodReference> target,
            Set<MethodDescriptor> implementedMethods, Set<String> visitedClasses) {
        if (!visitedClasses.add(cls.getName())) {
            return;
        }

        for (String ifaceName : cls.getInterfaces()) {
            ClassReader iface = classSource.get(ifaceName);
            if (iface != null) {
                collectMethodsToCopyFromInterfacesImpl(iface, target, implementedMethods, visitedClasses);
            }
        }

        for (MethodReader method : cls.getMethods()) {
            if (!method.hasModifier(ElementModifier.STATIC)
                    && !method.hasModifier(ElementModifier.ABSTRACT)) {
                MethodDescriptor descriptor = method.getDescriptor();
                if (!implementedMethods.contains(descriptor)) {
                    target.put(descriptor, method.getReference());
                }
            }
        }
    }

    private static Object getDefaultValue(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            ValueType.Primitive primitive = (ValueType.Primitive) type;
            switch (primitive.getKind()) {
                case BOOLEAN:
                    return false;
                case BYTE:
                    return (byte) 0;
                case SHORT:
                    return (short) 0;
                case INTEGER:
                    return 0;
                case CHARACTER:
                    return '\0';
                case LONG:
                    return 0L;
                case FLOAT:
                    return 0F;
                case DOUBLE:
                    return 0.0;
            }
        }
        return null;
    }

    private void renderInitializer(MethodReader method) {
        MethodReference ref = method.getReference();
        writer.emitMethod(ref.getDescriptor());
        writer.startVariableDeclaration().appendInit(ref);
        if (ref.parameterCount() != 1) {
            writer.append("(");
        }
        for (int i = 0; i < ref.parameterCount(); ++i) {
            if (i > 0) {
                writer.append(",").ws();
            }
            writer.append(variableNameForInitializer(i));
        }
        if (ref.parameterCount() != 1) {
            writer.append(")");
        }
        writer.sameLineWs().append("=>").ws().append("{").softNewLine().indent();

        String instanceName = variableNameForInitializer(ref.parameterCount());
        writer.append("let " + instanceName).ws().append("=").ws().append("new ").appendClass(
                ref.getClassName()).append("();").softNewLine();
        writer.appendMethod(ref).append("(" + instanceName);
        for (int i = 0; i < ref.parameterCount(); ++i) {
            writer.append(",").ws();
            writer.append(variableNameForInitializer(i));
        }
        writer.append(");").softNewLine();
        writer.append("return " + instanceName + ";").softNewLine();
        writer.outdent().append("}").endDeclaration();
        writer.emitMethod(null);
    }

    private String variableNameForInitializer(int index) {
        return context.isMinifying() ? variableNameGenerator.minifiedVariableName(index) : "var_" + index;
    }

    private void renderVirtualDeclarations(Collection<MethodReference> methods) {
        if (methods.stream().noneMatch(this::isVirtual)) {
            writer.append('0');
            return;
        }

        writer.append("[");
        boolean first = true;
        for (MethodReference method : methods) {
            if (!isVirtual(method)) {
                continue;
            }
            writer.emitMethod(method.getDescriptor());
            if (!first) {
                writer.append(",").ws();
            }
            first = false;
            emitVirtualDeclaration(method);
            writer.emitMethod(null);
        }
        writer.append("]");
    }

    private void emitVirtualDeclaration(MethodReference ref) {
        String methodName = context.getNaming().instanceMethodName(ref.getDescriptor());
        writer.append("\"").append(methodName).append("\"");
        writer.append(",").ws();
        emitVirtualFunctionWrapper(ref);
    }

    private void emitVirtualFunctionWrapper(MethodReference method) {
        var forced = context.isForcedFunction(method);
        if (forced) {
            writer.appendFunction("$rt_wrapFunctionVararg").append("(").appendMethod(method).append(")");
            return;
        }
        if (method.parameterCount() <= 4 && !forced) {
            writer.appendFunction("$rt_wrapFunction" + method.parameterCount());
            writer.append("(").appendMethod(method).append(")");
            return;
        }

        writer.append("function(");
        List<String> args = new ArrayList<>();
        for (int i = 1; i <= method.parameterCount(); ++i) {
            args.add(variableNameForInitializer(i));
        }
        for (int i = 0; i < args.size(); ++i) {
            if (i > 0) {
                writer.append(",").ws();
            }
            writer.append(args.get(i));
        }
        writer.append(")").ws().append("{").ws();
        if (method.getDescriptor().getResultType() != ValueType.VOID) {
            writer.append("return ");
        }
        writer.appendMethod(method).append("(");
        writer.append("this");
        for (String arg : args) {
            writer.append(",").ws().append(arg);
        }
        writer.append(");").ws().append("}");
    }

    private void renderBody(MethodHolder method, Decompiler decompiler, boolean isFunction) {
        MethodReference ref = method.getReference();
        writer.emitMethod(ref.getDescriptor());

        writer.appendMethod(ref);
        if (method.hasModifier(ElementModifier.NATIVE)) {
            renderNativeBody(method, classSource, isFunction);
        } else {
            renderRegularBody(method, decompiler, isFunction);
        }

        writer.outdent().append("}");
        writer.emitMethod(null);
    }

    private void renderNativeBody(MethodHolder method, ClassReaderSource classes, boolean isFunction) {
        var reference = method.getReference();
        var generator = generators.get(reference);
        if (generator == null) {
            AnnotationHolder annotHolder = method.getAnnotations().get(GeneratedBy.class.getName());
            if (annotHolder == null) {
                throw new DecompilationException("Method " + method.getOwnerName() + "." + method.getDescriptor()
                        + " is native, but no " + GeneratedBy.class.getName() + " annotation found");
            }
            ValueType annotValue = annotHolder.getValues().get("value").getJavaClass();
            String generatorClassName = ((ValueType.Object) annotValue).getClassName();
            generator = generatorCache.computeIfAbsent(generatorClassName,
                    name -> createGenerator(name, method, classes));
        }

        var async = asyncMethods.contains(reference);
        renderMethodPrologue(reference, method.getModifiers(), isFunction);
        methodBodyRenderer.renderNative(generator, async, reference);
        threadLibraryUsed |= methodBodyRenderer.isThreadLibraryUsed();
    }

    private Generator createGenerator(String name, MethodHolder method, ClassReaderSource classes) {
        Class<?> generatorClass;
        try {
            generatorClass = Class.forName(name, true, context.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new DecompilationException("Error instantiating generator " + name
                    + " for native method " + method.getOwnerName() + "." + method.getDescriptor());
        }

        var constructors = generatorClass.getConstructors();
        if (constructors.length != 1) {
            throw new DecompilationException("Error instantiating generator " + name
                    + " for native method " + method.getOwnerName() + "." + method.getDescriptor());
        }

        var constructor = constructors[0];
        var parameterTypes = constructor.getParameterTypes();
        var arguments = new Object[parameterTypes.length];
        for (var i = 0; i < arguments.length; ++i) {
            var parameterType = parameterTypes[i];
            if (parameterType.equals(ClassReaderSource.class)) {
                arguments[i] = classes;
            } else if (parameterType.equals(Properties.class)) {
                arguments[i] = context.getProperties();
            } else if (parameterType.equals(DependencyInfo.class)) {
                arguments[i] = context.getDependencyInfo();
            } else if (parameterType.equals(ServiceRepository.class)) {
                arguments[i] = context.getServices();
            } else if (parameterType.equals(JavaScriptTemplateFactory.class)) {
                arguments[i] = templateFactory;
            } else {
                var service = context.getServices().getService(parameterType);
                if (service == null) {
                    throw new DecompilationException("Error instantiating generator " + name
                            + " for native method " + method.getOwnerName() + "." + method.getDescriptor() + ". "
                            + "Its constructor requires " + parameterType + " as its parameter #" + (i + 1)
                            + " which is not available.");
                }
            }
        }

        try {
            return (Generator) constructor.newInstance(arguments);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new DecompilationException("Error instantiating generator " + name
                    + " for native method " + method.getOwnerName() + "." + method.getDescriptor(), e);
        }
    }

    private void renderRegularBody(MethodHolder method, Decompiler decompiler, boolean isFunction) {
        MethodReference reference = method.getReference();
        MethodNode node;
        var async = asyncMethods.contains(reference);
        if (async) {
            node = decompileAsync(decompiler, method);
        } else {
            var entry = decompileRegular(decompiler, method);
            node = entry.method;
        }

        methodBodyRenderer.setCurrentMethod(node);
        renderMethodPrologue(method.getReference(), method.getModifiers(), isFunction);
        methodBodyRenderer.render(node, async);
        threadLibraryUsed |= methodBodyRenderer.isThreadLibraryUsed();
    }

    private void renderMethodPrologue(MethodReference reference, Set<ElementModifier> modifier, boolean isFunction) {
        methodBodyRenderer.renderParameters(reference, modifier, isFunction);
        if (!isFunction) {
            writer.sameLineWs().append("=>");
        }
        writer.ws().append("{").indent().softNewLine();
    }

    private AstCacheEntry decompileRegular(Decompiler decompiler, MethodHolder method) {
        if (astCache == null) {
            return decompileRegularCacheMiss(decompiler, method);
        }

        AstCacheEntry entry = !cacheStatus.isStaleMethod(method.getReference())
                ? astCache.get(method.getReference(), cacheStatus)
                : null;
        if (entry == null) {
            entry = decompileRegularCacheMiss(decompiler, method);
            RegularMethodNode finalNode = entry.method;
            astCache.store(method.getReference(), entry, () -> dependencyExtractor.extract(finalNode));
        }
        return entry;
    }

    private AstCacheEntry decompileRegularCacheMiss(Decompiler decompiler, MethodHolder method) {
        RegularMethodNode node = decompiler.decompileRegular(method);
        ControlFlowEntry[] cfg = LocationGraphBuilder.build(node.getBody());
        return new AstCacheEntry(node, cfg);
    }

    private AsyncMethodNode decompileAsync(Decompiler decompiler, MethodHolder method) {
        if (astCache == null) {
            return decompiler.decompileAsync(method);
        }

        AsyncMethodNode node = !cacheStatus.isStaleMethod(method.getReference())
                ? astCache.getAsync(method.getReference(), cacheStatus)
                : null;
        if (node == null) {
            node = decompiler.decompileAsync(method);
            AsyncMethodNode finalNode = node;
            astCache.storeAsync(method.getReference(), node, () -> dependencyExtractor.extract(finalNode));
        }
        return node;
    }

    static void renderAsyncPrologue(SourceWriter writer, RenderingContext context) {
        writer.append(context.mainLoopName()).append(":").ws().append("while").ws().append("(true)")
                .ws().append("{").ws();
        writer.append("switch").ws().append("(").append(context.pointerName()).append(")").ws()
                .append('{').softNewLine();
    }

    static void renderAsyncEpilogue(SourceWriter writer) {
        writer.append("default:").ws().appendFunction("$rt_invalidPointer").append("();").softNewLine();
        writer.append("}}").softNewLine();
    }

    @Override
    public <T> T getService(Class<T> type) {
        return services.getService(type);
    }

    private static class PostponedFieldInitializer {
        FieldReference field;
        String value;

        PostponedFieldInitializer(FieldReference field, String value) {
            this.field = field;
            this.value = value;
        }
    }

    private boolean isVirtual(MethodReference method) {
        return context.isVirtual(method);
    }
}
