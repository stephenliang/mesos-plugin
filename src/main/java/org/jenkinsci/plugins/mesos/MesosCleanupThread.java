package org.jenkinsci.plugins.mesos;

import java.io.IOException;
import java.util.logging.Level;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

@Extension
public class MesosCleanupThread extends AsyncPeriodicWork {

    public MesosCleanupThread() {
        super("JClouds slave cleanup");
    }

    @Override
    public long getRecurrencePeriod() {
        return MIN * 5;
    }

    public static void invoke() {
        getInstance().run();
    }

    private static MesosCleanupThread getInstance() {
        return Jenkins.getInstance().getExtensionList(AsyncPeriodicWork.class).get(MesosCleanupThread.class);
    }

    @Override
    protected void execute(TaskListener listener) {
        final ImmutableList.Builder<ListenableFuture<?>> deletedNodesBuilder = ImmutableList.<ListenableFuture<?>>builder();
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Computer.threadPoolForRemoting);
        final ImmutableList.Builder<MesosComputer> computersToDeleteBuilder = ImmutableList.<MesosComputer>builder();

        for (final Computer c : Jenkins.getInstance().getComputers()) {
            if (MesosComputer.class.isInstance(c)) {
                if (((MesosComputer) c).getNode().isPendingDelete()) {
                    final MesosComputer comp = (MesosComputer) c;
                    computersToDeleteBuilder.add(comp);
                    logger.log(Level.INFO, "Marked " + comp.getName() + " for deletion");
                    ListenableFuture<?> f = executor.submit(new Runnable() {
                        public void run() {
                            logger.log(Level.INFO, "Deleting pending node " + comp.getName());
                            try {
                                comp.getNode().terminate();
                            } catch (RuntimeException e) {
                                logger.log(Level.WARNING, "Failed to disconnect and delete " + c.getName() + ": " + e.getMessage());
                                throw e;
                            }
                        }
                    });
                    deletedNodesBuilder.add(f);
                } else {
                    logger.log(Level.INFO, c.getName() + " is not pending deletion");
                }
            } else {
                logger.log(Level.INFO, c.getName() + " is not a mesos computer, it is a " + c.getClass().getName());
            }
        }

        Futures.getUnchecked(Futures.successfulAsList(deletedNodesBuilder.build()));

        for (MesosComputer c : computersToDeleteBuilder.build()) {
            try {
                c.deleteSlave();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to disconnect and delete " + c.getName() + ": " + e.getMessage());
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, "Failed to disconnect and delete " + c.getName() + ": " + e.getMessage());
            }

        }
    }
}