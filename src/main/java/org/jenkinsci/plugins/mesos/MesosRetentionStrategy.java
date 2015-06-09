/*
 * Copyright 2013 Twitter, Inc. and other contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.mesos;

import static hudson.util.TimeUnit2.MINUTES;

import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.model.Descriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;

/**
 * This is inspired by {@link hudson.slaves.CloudRetentionStrategy}.
 */
public class MesosRetentionStrategy extends RetentionStrategy<MesosComputer> {

  /**
   * Number of minutes of idleness before an instance should be terminated. A
   * value of zero indicates that the instance should never be automatically
   * terminated.
   */
  public final int idleTerminationMinutes;
  private ReentrantLock checkLock = new ReentrantLock(false);

  private static final Logger LOGGER = Logger
      .getLogger(MesosRetentionStrategy.class.getName());

  public MesosRetentionStrategy(int idleTerminationMinutes) {
    this.idleTerminationMinutes = idleTerminationMinutes;
  }


  @Override
  public long check(MesosComputer c) {
    if (!checkLock.tryLock()) {
        LOGGER.info("There is a check lock" + checkLock.getHoldCount() + ", queue: " + checkLock.getQueueLength());
      return 1;
    } else {
      try {
          LOGGER.info("Attempting to check the interval");
        return checkInternal(c);
      } finally {
          LOGGER.info("Unlocking the checklock");
        checkLock.unlock();
      }
    }
  }

  private long checkInternal(MesosComputer c) {
    if (c.getNode() == null) {
      return 1;
    } else {
        LOGGER.info("Slave is null for computer " + c.getName());
    }

    // If we just launched this computer, check back after 1 min.
    // NOTE: 'c.getConnectTime()' refers to when the Jenkins slave was launched.
    if ((System.currentTimeMillis() - c.getConnectTime()) <
        MINUTES.toMillis(idleTerminationMinutes)) {
      return 1;
    } else {
        LOGGER.info("Mesos computer " + c.getName() + " is not idle. Time = " + System.currentTimeMillis() +
                ", Connect Time: " + c.getConnectTime() + ", idle term: " + idleTerminationMinutes);
    }

    // If the computer is offline, terminate it.
    if (c.isOffline()) {
      LOGGER.info("Disconnecting offline computer " + c.getName());
      c.getNode().terminate();
      return 1;
    } else {
        LOGGER.info("Mesos computer " + c.getName() + " is not offline");
    }

    // Terminate the computer if it is idle for longer than
    // 'idleTerminationMinutes'.
    if (c.isIdle()) {
      final long idleMilliseconds =
          System.currentTimeMillis() - c.getIdleStartMilliseconds();

      if (idleMilliseconds > MINUTES.toMillis(idleTerminationMinutes)) {
        LOGGER.info("Disconnecting idle computer " + c.getName());
          c.getNode().setPendingDelete(true);
        c.getNode().terminate();
      }
    } else {
        LOGGER.info("Mesos computer " + c.getName() + " is not idle. Time = " + System.currentTimeMillis() + ", idle start " + c.getIdleStartMilliseconds());
    }
    return 1;
  }

  /**
   * Try to connect to it ASAP to launch the slave agent.
   */
  @Override
  public void start(MesosComputer c) {
    c.connect(false);
  }

  /**
   * No registration since this retention strategy is used only for Mesos nodes
   * that we provision automatically.
   */
  public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
    @Override
    public String getDisplayName() {
      return "MESOS";
    }
  }
}
