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
package gov.pnnl.proven.cluster.lib.module.component.interceptor;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.enterprise.inject.Intercepted;
import javax.enterprise.inject.spi.Bean;
import javax.inject.Inject;
import javax.interceptor.AroundConstruct;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.pnnl.proven.cluster.lib.module.component.HealthReporter;
import gov.pnnl.proven.cluster.lib.module.component.MetricsReporter;
import gov.pnnl.proven.cluster.lib.module.component.ModuleComponent;
import gov.pnnl.proven.cluster.lib.module.component.StatusReporter;
import gov.pnnl.proven.cluster.lib.module.component.event.HealthReport;
import gov.pnnl.proven.cluster.lib.module.component.event.MetricsReport;
import gov.pnnl.proven.cluster.lib.module.component.event.ScheduledEvent;
import gov.pnnl.proven.cluster.lib.module.component.event.StatusReport;
import gov.pnnl.proven.cluster.lib.module.registry.ScheduledEventRegistry;
import gov.pnnl.proven.cluster.lib.module.component.annotation.Component;
import gov.pnnl.proven.cluster.lib.module.component.annotation.ScheduledEventReporter;

/**
 * Registers defined {@code ScheduledEventReporter} events for a component with
 * the {@code ScheduledEventManager} at construction time. By default, schedules
 * are activated once successfully registered with the event manager.
 * 
 * @author d3j766
 * 
 */
@Component
@Interceptor
public class ScheduledEventReporterInterceptor implements Serializable {

	private static final long serialVersionUID = 1L;

	static Logger log = LoggerFactory.getLogger(ScheduledEventReporterInterceptor.class);

	@Inject
	@Intercepted
	private Bean<?> component;

	@Inject
	private ScheduledEventRegistry ser;

	@AroundConstruct
	public Object registerScheduledEventReporters(InvocationContext ctx) throws Exception {

		// OK to proceed
		Object result = ctx.proceed();

		ModuleComponent mc = (ModuleComponent) ctx.getTarget();

		Map<Class<? extends ScheduledEvent>, String> events = new HashMap<>();
		Class<?> componentType = component.getBeanClass();
		while (componentType != null) {
			for (Annotation annotation : componentType.getDeclaredAnnotations()) {
				if (annotation instanceof ScheduledEventReporter) {
					ScheduledEventReporter er = (ScheduledEventReporter) annotation;
					events.putIfAbsent(er.event(), er.schedule());
				}
			}
			componentType = componentType.getSuperclass();
		}

		// Register schedule event with the registry
		componentType = component.getBeanClass();
		for (Class<? extends ScheduledEvent> clazz : events.keySet()) {

			Supplier<ScheduledEvent> supplier = null;
			
			// Status report
			if ((clazz.equals(StatusReport.class)) && (StatusReporter.class.isAssignableFrom(componentType))) {
				StatusReporter sr = (StatusReporter) ctx.getTarget();
				supplier = sr::getStatusReport;
			}

			// Metrics Report
			if ((clazz.equals(MetricsReport.class)) && (MetricsReporter.class.isAssignableFrom(componentType))) {
				MetricsReporter mr = (MetricsReporter) ctx.getTarget();
				supplier = mr::getMetricsReport;
			}

			// Health Report
			if ((clazz.equals(HealthReport.class)) && (HealthReporter.class.isAssignableFrom(componentType))) {
				HealthReporter hr = (HealthReporter) ctx.getTarget();
				supplier = hr::getHealthReport;
			}

			// Register scheduled event if supplier was found, else report error
			if (null != supplier) {
				ser.register(mc, clazz, supplier, events.get(clazz));
			}
			log.error("Could not determine report supplier for ScheduledEventReporter");

		}

		return result;
	}

}