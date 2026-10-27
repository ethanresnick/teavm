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
package org.teavm.classlib.java.lang.reflect;

import org.teavm.classlib.PlatformDetector;
import org.teavm.classlib.java.lang.TClass;
import org.teavm.classlib.java.lang.TIllegalAccessException;
import org.teavm.classlib.java.lang.TIllegalArgumentException;
import org.teavm.runtime.reflect.ClassInfoUtil;
import org.teavm.runtime.reflect.MethodInfo;
import org.teavm.runtime.reflect.ModifiersInfo;

public class TMethod extends TExecutable implements TMember {
    private TType genericReturnType;

    public TMethod(TClass<?> declaringClass, MethodInfo info) {
        super(declaringClass, info);
    }

    @Override
    public String getName() {
        return methodInfo.name().getStringObject();
    }

    public TClass<?> getReturnType() {
        return (TClass<?>) (Object) ClassInfoUtil.resolve(methodInfo.returnType()).classObject();
    }

    public TType getGenericReturnType() {
        if (genericReturnType == null) {
            var reflection = methodInfo.reflection();
            if (reflection == null || reflection.genericReturnType() == null) {
                genericReturnType = getReturnType();
            } else {
                genericReturnType = TGenericTypeFactory.create(this, reflection.genericReturnType());
            }
        }
        return genericReturnType;
    }

    public boolean isDefault() {
        // A public, non-abstract, non-static method of an interface.
        int flags = getModifiers() & (TModifier.PUBLIC | TModifier.ABSTRACT | TModifier.STATIC);
        return flags == TModifier.PUBLIC && getDeclaringClass().isInterface();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendModifiers(sb);
        sb.append(getReturnType().getTypeName()).append(' ');
        appendMemberName(sb);
        appendParameters(sb, getParameterTypes(), false);
        appendThrows(sb);
        return sb.toString();
    }

    public String toGenericString() {
        StringBuilder sb = new StringBuilder();
        appendModifiers(sb);
        TTypeVariable<?>[] typeParams = getTypeParameters();
        if (typeParams.length > 0) {
            sb.append('<');
            for (int i = 0; i < typeParams.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendTypeParameter(sb, typeParams[i]);
            }
            sb.append("> ");
        }
        sb.append(getGenericReturnType().getTypeName()).append(' ');
        appendMemberName(sb);
        appendParameters(sb, getGenericParameterTypes(), isVarArgs());
        appendThrows(sb);
        return sb.toString();
    }

    // With varargsSugar, the last parameter prints as "T..." instead of "T[]".
    private static void appendParameters(StringBuilder sb, TType[] params, boolean varargsSugar) {
        sb.append('(');
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String name = params[i].getTypeName();
            if (varargsSugar && i == params.length - 1 && name.endsWith("[]")) {
                sb.append(name, 0, name.length() - 2).append("...");
            } else {
                sb.append(name);
            }
        }
        sb.append(')');
    }

    private void appendThrows(StringBuilder sb) {
        TClass<?>[] exceptions = getExceptionTypes();
        for (int i = 0; i < exceptions.length; i++) {
            sb.append(i == 0 ? " throws " : ",").append(exceptions[i].getTypeName());
        }
    }

    // Appends the method modifiers (JLS 8.4.3) in JLS order, each followed by a space.
    // "default" goes after "abstract". Other flag bits, such as bridge and varargs, never print.
    private void appendModifiers(StringBuilder sb) {
        int mods = getModifiers();
        appendIf(sb, mods, TModifier.PUBLIC, "public");
        appendIf(sb, mods, TModifier.PROTECTED, "protected");
        appendIf(sb, mods, TModifier.PRIVATE, "private");
        appendIf(sb, mods, TModifier.ABSTRACT, "abstract");
        if (isDefault()) {
            sb.append("default ");
        }
        appendIf(sb, mods, TModifier.STATIC, "static");
        appendIf(sb, mods, TModifier.FINAL, "final");
        appendIf(sb, mods, TModifier.SYNCHRONIZED, "synchronized");
        appendIf(sb, mods, TModifier.NATIVE, "native");
        appendIf(sb, mods, TModifier.STRICT, "strictfp");
    }

    private static void appendIf(StringBuilder sb, int mods, int flag, String word) {
        if ((mods & flag) != 0) {
            sb.append(word).append(' ');
        }
    }

    private void appendMemberName(StringBuilder sb) {
        sb.append(getDeclaringClass().getTypeName()).append('.').append(getName());
    }

    // Writes "T", or "T extends A & B" when the bounds say more than Object.
    private static void appendTypeParameter(StringBuilder sb, TTypeVariable<?> variable) {
        sb.append(variable.getName());
        TType[] bounds = variable.getBounds();
        if (bounds.length == 1 && bounds[0] == (Object) Object.class) {
            return;
        }
        for (int i = 0; i < bounds.length; i++) {
            sb.append(i == 0 ? " extends " : " & ").append(bounds[i].getTypeName());
        }
    }

    public Object invoke(Object obj, Object... args) throws TIllegalAccessException, TIllegalArgumentException,
            TInvocationTargetException {
        var actualCount = args != null ? args.length : 0;
        if (actualCount != methodInfo.parameterCount()) {
            throw new TIllegalArgumentException();
        }

        if ((methodInfo.modifiers() & ModifiersInfo.STATIC) == 0) {
            if (!declaringClass.isInstance(obj)) {
                throw new TIllegalArgumentException();
            }
        }
        validateArgs(args);
        // Only the C backend initializes the class here. On JS and Wasm GC a static method initializes its class
        // itself, at a suspend point when the initializer can suspend; this call could not suspend
        // (ClassInfo.initialize is an intrinsic that AsyncMethodFinder never marks async).
        if (PlatformDetector.isC() && (methodInfo.modifiers() & ModifiersInfo.STATIC) != 0) {
            declaringClass.initialize();
        }

        return methodInfo.call(obj, args);
    }

    public boolean isBridge() {
        return (methodInfo.modifiers() & ModifiersInfo.BRIDGE) != 0;
    }
}
