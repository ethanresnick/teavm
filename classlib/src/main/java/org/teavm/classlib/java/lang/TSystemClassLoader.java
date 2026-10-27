/*
 *  Copyright 2014 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import org.teavm.classlib.java.net.TURL;
import org.teavm.classlib.java.util.TCollections;
import org.teavm.classlib.java.util.TEnumeration;

class TSystemClassLoader extends TClassLoader {
    // The root of delegation, so an explicit null parent: TClassLoader() means the system loader.
    TSystemClassLoader() {
        super(null);
    }

    // The system loader sees every class that the compiled program kept.
    @Override
    protected TClass<?> loadClass(String name, boolean resolve) throws TClassNotFoundException {
        return TClass.forName((TString) (Object) name);
    }

    // The system loader's resources are the ones embedded in the program.
    @Override
    protected TURL findResource(String name) {
        return findEmbeddedResource(name);
    }

    @Override
    protected TEnumeration<TURL> findResources(String name) {
        TURL url = findEmbeddedResource(name);
        return url != null
                ? TCollections.enumeration(TCollections.singletonList(url))
                : TCollections.emptyEnumeration();
    }
}
