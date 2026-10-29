/*
 *  Copyright 2018 Alexey Andreev.
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
package org.teavm.backend.c.analyze;

import org.teavm.backend.c.runtime.CResources;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.interop.DelegateTo;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class CDependencyListener extends AbstractDependencyListener implements ClassHolderTransformer {
    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method) {
        if (method.getReference().getClassName().equals(CResources.class.getName())
                && method.getReference().getName().equals("acquireResources")) {
            var create = agent.linkMethod(new MethodReference(CResources.class, "create", String.class,
                    int.class, int.class, CResources.Resource.class));
            create.addLocation(new CallLocation(method.getReference())).use();
            create.propagate(1, String.class);
            method.getResult().propagate(agent.getType(ValueType.parse(CResources.Resource[].class)));
            create.getResult().connect(method.getResult().getArrayItem());
        }
        AnnotationReader delegateAnnot = method.getMethod().getAnnotations().get(DelegateTo.class.getName());
        if (delegateAnnot != null) {
            String delegateMethodName = delegateAnnot.getValue("value").getString();
            ClassReader cls = agent.getClassSource().get(method.getReference().getClassName());
            for (MethodReader delegate : cls.getMethods()) {
                if (delegate.getName().equals(delegateMethodName)) {
                    if (delegate != method.getMethod()) {
                        MethodDependency delegateDep = agent.linkMethod(delegate.getReference());
                        method.addLocationListener(delegateDep::addLocation);
                        delegateDep.use();
                    }
                }
            }
        }
    }

    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        for (MethodHolder method : cls.getMethods()) {
            AnnotationReader delegateAnnot = method.getAnnotations().get(DelegateTo.class.getName());
            if (delegateAnnot != null) {
                method.setProgram(null);
                method.getModifiers().add(ElementModifier.NATIVE);
            }
        }
    }
}
