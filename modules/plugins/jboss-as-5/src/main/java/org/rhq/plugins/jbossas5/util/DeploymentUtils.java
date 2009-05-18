/*
 * Jopr Management Platform
 * Copyright (C) 2005-2009 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.rhq.plugins.jbossas5.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.util.ZipUtil;
import org.rhq.plugins.jbossas5.AbstractManagedDeploymentComponent;

import org.jboss.deployers.spi.management.deploy.DeploymentManager;
import org.jboss.deployers.spi.management.deploy.DeploymentProgress;
import org.jboss.deployers.spi.management.deploy.DeploymentStatus;

/**
 * @author Ian Springer
 */
public class DeploymentUtils {
    private static final Log LOG = LogFactory.getLog(DeploymentUtils.class);

    public static boolean hasCorrectExtension(File archiveFile, ResourceType resourceType) {
        Configuration defaultPluginConfig = ResourceTypeUtils.getDefaultPluginConfiguration(resourceType);
        String expectedExtension = defaultPluginConfig.getSimple(AbstractManagedDeploymentComponent.EXTENSION_PROPERTY)
            .getStringValue();
        if (expectedExtension == null)
            throw new IllegalStateException("No value was defined for the required '"
                + AbstractManagedDeploymentComponent.EXTENSION_PROPERTY + "' plugin config prop for " + resourceType);
        String archiveFileName = archiveFile.getName();
        int lastPeriod = archiveFileName.lastIndexOf(".");
        String extension = (lastPeriod != -1) ? archiveFileName.substring(lastPeriod + 1) : null;
        // Use File.equals() to compare the extensions so case-sensitivity is correct for this platform.
        return (extension != null && new File(extension).equals(new File(expectedExtension)));
    }

    public static DeploymentStatus deployArchive(DeploymentManager deploymentManager, File archiveFile,
        File deployDirectory, boolean deployExploded) throws Exception {
        if (deployDirectory == null)
            throw new IllegalArgumentException("Deploy directory is null.");
        String archiveFileName = archiveFile.getName();
        DeploymentProgress progress;
        if (deployExploded) {
            LOG.debug("Deploying '" + archiveFileName + "' in exploded form...");
            File tempDir = new File(deployDirectory, archiveFile.getName() + ".rej");
            ZipUtil.unzipFile(archiveFile, tempDir);
            File archiveDir = new File(deployDirectory, archiveFileName);
            URL contentURL = archiveDir.toURI().toURL();
            if (!tempDir.renameTo(archiveDir))
                throw new IOException("Failed to rename '" + tempDir + "' to '" + archiveDir + "'.");
            progress = deploymentManager.distribute(archiveFileName, contentURL, false);
        } else {
            LOG.debug("Deploying '" + archiveFileName + "' in non-exploded form...");
            URL contentURL = archiveFile.toURI().toURL();
            File deployLocation = new File(deployDirectory, archiveFileName);
            boolean copyContent = !deployLocation.equals(archiveFile);
            progress = deploymentManager.distribute(archiveFileName, contentURL, copyContent);
        }
        run(progress);

        // Now that we've distributed the deployment, we need to start it!
        String[] repositoryNames = progress.getDeploymentID().getRepositoryNames();
        progress = deploymentManager.start(repositoryNames);
        return run(progress);
    }

    public static DeploymentStatus run(DeploymentProgress progress) throws Exception {
        progress.run();
        DeploymentStatus status = progress.getDeploymentStatus();
        if (status.isFailed())
            //noinspection ThrowableResultOfMethodCallIgnored
            throw new Exception(status.getMessage(), status.getFailure());
        return status;
    }

    private DeploymentUtils() {
    }
}
