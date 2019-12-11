/*******************************************************************************
 * Copyright (c) 2017, Battelle Memorial Institute All rights reserved.
 * Battelle Memorial Institute (hereinafter Battelle) hereby grants permission to any person or entity 
 * lawfully obtaining a copy of this software and associated documentation files (hereinafter the 
 * Software) to redistribute and use the Software in source and binary forms, with or without modification. 
 * Such person or entity may use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of 
 * the Software, and may permit others to do so, subject to the following conditions:
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the 
 * following disclaimers.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and 
 * the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Other than as used herein, neither the name Battelle Memorial Institute or Battelle may be used in any 
 * form whatsoever without the express written consent of Battelle.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL 
 * BATTELLE OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED 
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * General disclaimer for use with OSS licenses
 * 
 * This material was prepared as an account of work sponsored by an agency of the United States Government. 
 * Neither the United States Government nor the United States Department of Energy, nor Battelle, nor any 
 * of their employees, nor any jurisdiction or organization that has cooperated in the development of these 
 * materials, makes any warranty, express or implied, or assumes any legal liability or responsibility for 
 * the accuracy, completeness, or usefulness or any information, apparatus, product, software, or process 
 * disclosed, or represents that its use would not infringe privately owned rights.
 * 
 * Reference herein to any specific commercial product, process, or service by trade name, trademark, manufacturer, 
 * or otherwise does not necessarily constitute or imply its endorsement, recommendation, or favoring by the United 
 * States Government or any agency thereof, or Battelle Memorial Institute. The views and opinions of authors expressed 
 * herein do not necessarily state or reflect those of the United States Government or any agency thereof.
 * 
 * PACIFIC NORTHWEST NATIONAL LABORATORY operated by BATTELLE for the 
 * UNITED STATES DEPARTMENT OF ENERGY under Contract DE-AC05-76RL01830
 ******************************************************************************/
package gov.pnnl.proven.cluster.lib.module.component;

import static gov.pnnl.proven.cluster.lib.member.MemberUtils.exCause;
import static gov.pnnl.proven.cluster.lib.member.MemberUtils.exCauseName;
import static gov.pnnl.proven.cluster.lib.module.util.LoggerResource.currentThreadLog;

import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.inject.Inject;

import org.slf4j.Logger;

import gov.pnnl.proven.cluster.lib.module.component.annotation.Scheduler;

/**
 * Provides a fixed delay schedule for the application of a task of a registered
 * type. Schedule properties are defined by {@code Scheduler} annotation at
 * injection point. Scheduled tasks must register their supplier of T.
 * 
 * @param T
 *            the type of the registered task
 * 
 * @see Scheduler
 * 
 * @author d3j766
 *
 */
@Scheduler
public abstract class TaskSchedule<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	@Inject
	Logger log;

	public enum ScheduleStatus {

		/**
		 * Messenger is executing its scheduled tasks.
		 */
		STARTED,

		/**
		 * Messenger's scheduled tasks are no longer being executed due to
		 * cancellation or failure.
		 */
		STOPPED;
	}

	public static final String SM_EXECUTOR_SERVICE = "concurrent/ScheduledTasks";

	@Resource(lookup = SM_EXECUTOR_SERVICE)
	public ManagedScheduledExecutorService scheduler;

	protected ScheduledFuture<?> scheduledFuture;

	/**
	 * @see {@link Scheduler#delay()}
	 */
	protected long delay;

	/**
	 * @see {@link Scheduler#timeUnit()}
	 */
	protected TimeUnit timeUnit;

	/**
	 * @see {@link Scheduler#jitterPercent()}
	 */
	protected int jitterPercent;

	/**
	 * @see {@link Scheduler#activateOnStartup()}
	 */
	protected boolean activateOnStartup;

	/**
	 * Registered supplier.
	 */
	protected Supplier<Optional<T>> supplier = () -> {
		return Optional.empty();
	};

	/**
	 * Messenger status properties
	 */
	ScheduleStatus status = ScheduleStatus.STOPPED;
	boolean isCancelled = false;
	boolean isError = false;

	public TaskSchedule() {
	}

	@PostConstruct
	public void initScheduler() {
		if (isActivateOnStartup()) {
			start();
		}
	}

	@PreDestroy
	public void destroyScheduler() {
		stop();
	}

	/**
	 * Starts the fixed delay scheduler. Tasks will continue to be run as long
	 * as error conditions or cancel exceptions are encountered.
	 */
	public void start() {

		synchronized (status) {
			if (status == ScheduleStatus.STOPPED) {
				isCancelled = false;
				isError = false;
				applyJitter();
				scheduledFuture = scheduler.scheduleWithFixedDelay(() -> {
					log.debug(currentThreadLog("START SCHEDULER STARTED"));
					try {
						log.debug("Scheduled task started");
						if (hasRegisteredSupplier()) {
							apply(supplier.get());
						} else {
							log.debug("No registered supplier for task schedule");
						}
						log.debug("Scheduled task completed normally");
					} catch (Throwable e) {
						if (e instanceof CancellationException) {
							log.debug("Scheduled task execution has been cancelled.");
							isCancelled = true;
						} else if ((e instanceof RejectedExecutionException) || (e instanceof ExecutionException)
								|| (e instanceof InterruptedException)) {
							log.warn("Scheduled task execution had an exception: \n" + exCauseName(e));
							exCause(e).printStackTrace();
						} else if ((e instanceof Error) || (e instanceof Exception)) {
							log.warn("Scheduled task execution encountered an Error or unknown exception: "
									+ exCauseName(e));
							exCause(e).printStackTrace();
							isError = true;
						}
					} finally {
						if (isCancelled || isError) {
							stop();
							log.debug(currentThreadLog("TASK SCHEDULER STOPPED"));
						}
					}
				}, delay, delay, timeUnit);
			}
			status = ScheduleStatus.STARTED;
		}
	}

	private boolean hasRegisteredSupplier() {
		return (null != supplier);
	}

	/**
	 * Shutdown of task scheduler
	 */
	public void stop() {

		log.debug("Task Scheduler stopping...");
		synchronized (status) {

			if (null != scheduledFuture) {
				scheduledFuture.cancel(true);
				scheduledFuture = null;
			}
			status = ScheduleStatus.STOPPED;
		}
		log.debug("Task scheduler stopped");
	}

	/**
	 * Registers supplier.
	 */
	public void register(Supplier<Optional<T>> supplier) {
		this.supplier = supplier;
	}

	/**
	 * Applies task of type T
	 * 
	 * @param message
	 *            optional supplied type T
	 */
	protected abstract void apply(Optional<T> message);

	/**
	 * Will be applied at schedule start.
	 */
	synchronized private void applyJitter() {
		long duration = timeUnit.toMillis(delay);
		long min = Math.round(duration - ((jitterPercent / 100.0) * duration));
		long max = Math.round(duration + ((jitterPercent / 100.0) * duration));
		duration = ThreadLocalRandom.current().nextLong(min, max + 1);
		log.debug("MIN schedule delay: " + min);
		log.debug("MAX schedule delay: " + max);
		log.debug("New scheduled delay is: " + duration);
		this.delay = duration;
		this.timeUnit = TimeUnit.MILLISECONDS;
	}

	public long getDelay() {
		return delay;
	}

	public void setDelay(long delay) {
		this.delay = delay;
	}

	public TimeUnit getTimeUnit() {
		return timeUnit;
	}

	public void setTimeUnit(TimeUnit timeUnit) {
		this.timeUnit = timeUnit;
	}

	public int getJitterPercent() {
		return jitterPercent;
	}

	public void setJitterPercent(int jitterPercent) {
		this.jitterPercent = jitterPercent;
	}

	public boolean isActivateOnStartup() {
		return activateOnStartup;
	}

	public void setActivateOnStartup(boolean activateOnStartup) {
		this.activateOnStartup = activateOnStartup;
	}

}
