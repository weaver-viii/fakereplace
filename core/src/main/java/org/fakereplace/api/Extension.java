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

package org.fakereplace.api;

import java.util.List;
import java.util.Set;

import org.fakereplace.api.environment.Environment;
import org.fakereplace.transformation.FakereplaceTransformer;

/**
 * Integrations need to implement this service to tell the transformer
 * about what they need.
 * <p>
 * Note: all class names should be returned in java (not JVM) format
 *
 * @author stuart
 */
public interface Extension {



    /**
     * Integrations have a change to transform classes
     * They get to see the class before any manipulation is
     * done to it.
     * They do not get to transform reloaded classes.
     *
     */
    List<FakereplaceTransformer> getTransformers();

    /**
     * returns the name of the ClassChangeAware object
     * provided by this integration.
     * <p>
     * This object is installed into the same ClassLoader
     * that the integrations classes are in
     * <p>
     * Note that the ClassChangeAware object should register
     * itself with the ClassChangeNotifier
     *
     */
    String getClassChangeAwareName();

    /**
     * If a classloader loads one of these classes it enables
     * this integration module.
     * <p>
     * This also means that the classloader that loaded
     * the class will be intrumented to load classes from the integration.
     */
    Set<String> getIntegrationTriggerClassNames();

    /**
     * Gets the {@link Environment} this extension provides, or null if it does
     * not override the environment.
     *
     * Only one active extension can override the environment.
     */
    String getEnvironment();

    /**
     * get a list of classes that should be turned into tracked instances.
     *
     */
    Set<String> getTrackedInstanceClassNames();
}
