package org.jenkinsci.plugins.mesos;


import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.slaves.OfflineCause;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

public class MesosOneOffSlave extends BuildWrapper {
    private static final Logger LOGGER = Logger.getLogger(MesosOneOffSlave.class.getName());

    @DataBoundConstructor
    public MesosOneOffSlave() {
    }

    //
    // convert Jenkins staticy stuff into pojos; performing as little critical stuff here as
    // possible, as this method is very hard to test due to static usage, etc.
    //
    @Override
    @SuppressWarnings("rawtypes")
    public Environment setUp(AbstractBuild build, Launcher launcher, final BuildListener listener) {
        if (MesosComputer.class.isInstance(build.getExecutor().getOwner())) {
            final MesosComputer c = (MesosComputer) build.getExecutor().getOwner();
            return new Environment() {
                @Override
                public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {
                    LOGGER.warning("Single-use slave " + c.getName() + " getting torn down.");
                    c.setTemporarilyOffline(true, OfflineCause.create(Messages._OneOffCause()));
                    return true;
                }
            };
        } else {
            return new Environment() {
                @Override
                public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {
                    LOGGER.warning("Not a single use slave, this is a " + build.getExecutor().getOwner().getClass());
                    return true;
                }
            };
        }

    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return "JClouds Single-Use Slave";
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(AbstractProject item) {
            return true;
        }

    }
}

