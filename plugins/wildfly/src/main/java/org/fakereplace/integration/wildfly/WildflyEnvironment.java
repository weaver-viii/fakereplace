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
package org.fakereplace.integration.wildfly;

import org.fakereplace.api.environment.ChangedClasses;
import org.fakereplace.api.environment.Environment;
import org.fakereplace.core.DefaultEnvironment;
import org.fakereplace.logging.Logger;
import org.jboss.as.server.CurrentServiceContainer;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.Services;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.vfs.VirtualFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Stuart Douglas
 */
public class WildflyEnvironment implements Environment {

    private final Logger log = Logger.getLogger(WildflyEnvironment.class);

    /**
     * When classes are replaced we need to update their timestamps, otherwise they will be replaced on every subsequent
     * invocation.
     */
    private final Map<Class<?>, Long> replacedClassTimestamps = Collections.synchronizedMap(new WeakHashMap<Class<?>, Long>());

    @Override
    public boolean isClassReplaceable(final String className, final ClassLoader loader) {
        if (loader instanceof ModuleClassLoader) {
            if (((ModuleClassLoader) loader).getModule().getIdentifier().toString().startsWith("deployment.")) {
                return true;
            }
        }
        return DefaultEnvironment.INSTANCE.isClassReplaceable(className, loader);
    }

    public void recordTimestamp(String className, ClassLoader loader) {

    }

    public ChangedClasses getUpdatedClasses(final String deploymentName, Map<String, Long> updatedClasses) {
        log.info("Finding classes for " + deploymentName);
        ServiceController<DeploymentUnit> deploymentService = deploymentService(deploymentName);
        if (deploymentService == null) {
            log.error("Could not find deployment " + deploymentName);
            return ChangedClasses.EMPTY;
        }

        final ModuleIdentifier moduleId = getModuleIdentifier(deploymentService);
        final ModuleClassLoader loader = deploymentService.getValue().getAttachment(Attachments.MODULE).getClassLoader();
        if (loader == null) {
            log.error("Could not find module " + moduleId);
            return ChangedClasses.EMPTY;
        }

        final Set<Class<?>> ret = new HashSet<Class<?>>();
        final Set<String> newClasses = new HashSet<String>();
        for (Map.Entry<String, Long> entry : updatedClasses.entrySet()) {
            StringBuilder traceString = new StringBuilder();
            traceString.append("Comparing class ");
            traceString.append(entry.getKey());
            traceString.append(" TS: ");
            traceString.append(entry.getValue());

            final String resourceName = entry.getKey().replace(".", "/") + ".class";
            final URL resource = loader.getResource(resourceName);
            if (resource == null) {
                //new class
                newClasses.add(entry.getKey());
                traceString.append(" not found on server, adding as new class");
            } else {
                try {
                    final URLConnection urlConnection = resource.openConnection();
                    Long timeStamp = urlConnection.getLastModified();
                    final Class<?> clazz = loader.loadClass(entry.getKey());
                    Long replacedTs = replacedClassTimestamps.get(clazz);
                    if (replacedTs != null) {
                        timeStamp = replacedTs;
                    }
                    if (timeStamp < entry.getValue()) {
                        traceString.append(" replacing");
                        ret.add(clazz);
                        replacedClassTimestamps.put(clazz, entry.getValue());
                    } else {
                        traceString.append(" not replacing");
                    }
                } catch (IOException e) {
                    log.error("Could not open connection for " + resourceName, e);
                } catch (ClassNotFoundException e) {
                    log.debug("Could not load class " + entry.getKey(), e);
                }

                log.debug(traceString.toString());

            }
        }
        return new ChangedClasses(ret, newClasses, loader);
    }

    @Override
    public Set<String> getUpdatedResources(final String deploymentName, final Map<String, Long> updatedResources) {
        ServiceController<DeploymentUnit> deploymentService = deploymentService(deploymentName);
        if (deploymentService == null) {
            return Collections.emptySet();
        }

        final ModuleClassLoader loader = deploymentService.getValue().getAttachment(Attachments.MODULE).getClassLoader();
        if (loader == null) {
            return Collections.emptySet();
        }

        final DeploymentUnit deploymentUnit = deploymentService.getValue();
        final ResourceRoot root = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);

        final Set<String> resources = new HashSet<String>();
        for (final Map.Entry<String, Long> entry : updatedResources.entrySet()) {
            final VirtualFile file = root.getRoot().getChild(entry.getKey());
            if (file.exists()) {
                long last = file.getLastModified();
                if (entry.getValue() > last) {
                    resources.add(entry.getKey());
                }
            }
        }
        return resources;
    }

    private ServiceController<DeploymentUnit> deploymentService(final String deploymentName) {
        ServiceController<DeploymentUnit> deploymentService = (ServiceController<DeploymentUnit>) CurrentServiceContainer.getServiceContainer().getService(Services.deploymentUnitName(deploymentName));
        if (deploymentService == null) {
            //now try for a sub deployment
            for (final ServiceName serviceName : CurrentServiceContainer.getServiceContainer().getServiceNames()) {
                if (Services.JBOSS_DEPLOYMENT_SUB_UNIT.isParentOf(serviceName)) {
                    final String[] parts = serviceName.toArray();
                    if (parts[parts.length - 1].equals(deploymentName)) {
                        deploymentService = (ServiceController<DeploymentUnit>) CurrentServiceContainer.getServiceContainer().getService(serviceName);
                        break;
                    }
                }
            }
        }
        return deploymentService;
    }

    @Override
    public void updateResource(final String archiveName, final Map<String, byte[]> replacedResources) {
        ServiceController<DeploymentUnit> deploymentService = deploymentService(archiveName);
        if (deploymentService == null) {
            return;
        }
        final ModuleClassLoader loader = deploymentService.getValue().getAttachment(Attachments.MODULE).getClassLoader();
        if (loader == null) {
            return;
        }

        final DeploymentUnit deploymentUnit = deploymentService.getValue();
        final ResourceRoot root = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);

        for (final Map.Entry<String, byte[]> entry : replacedResources.entrySet()) {
            final VirtualFile file = root.getRoot().getChild(entry.getKey());
            try {
                final FileOutputStream stream = new FileOutputStream(file.getPhysicalFile(), false);
                try {
                    stream.write(entry.getValue());
                    stream.flush();
                } finally {
                    stream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private ModuleIdentifier getModuleIdentifier(final ServiceController<DeploymentUnit> deploymentArchive) {
        return deploymentArchive.getValue().getAttachment(Attachments.MODULE_IDENTIFIER);
    }

}
