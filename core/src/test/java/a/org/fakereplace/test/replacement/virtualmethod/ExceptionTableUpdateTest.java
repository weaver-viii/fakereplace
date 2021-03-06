/*
 * Copyright 2016, Stuart Douglas, and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package a.org.fakereplace.test.replacement.virtualmethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import a.org.fakereplace.test.util.ClassReplacer;
import org.junit.Test;

public class ExceptionTableUpdateTest {
    @Test
    public void testExceptionTableCorrect() throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        ClassReplacer cr = new ClassReplacer();
        cr.queueClassForReplacement(VirtualMethodExceptionClass.class, VirtualMethodExceptionClass1.class);
        cr.replaceQueuedClasses();

        VirtualMethodExceptionClass i = new VirtualMethodExceptionClass();
        Method m = i.getClass().getMethod("doStuff1", int.class, int.class);
        m.invoke(i, 0, 0);
        m = i.getClass().getMethod("doStuff2", int.class, int.class);
        m.invoke(i, 0, 0);
    }

}
