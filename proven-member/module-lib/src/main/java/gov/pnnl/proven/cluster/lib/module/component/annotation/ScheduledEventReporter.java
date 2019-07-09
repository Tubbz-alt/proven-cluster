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
/**
 * 
 */
package gov.pnnl.proven.cluster.lib.module.component.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.ejb.ScheduleExpression;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.util.AnnotationLiteral;
import javax.interceptor.InterceptorBinding;
import gov.pnnl.proven.cluster.lib.module.component.event.ComponentEvent;
import gov.pnnl.proven.cluster.lib.module.component.event.ScheduledEvent;

/**
 * Identifies a scheduled event and its reporting schedule for a
 * {@code ModuleComponent}. If a superclass of the component includes
 * {@code ScheduledEventReporter} definitions, these will also be included, as
 * long as they do not conflict with any of the subclass definitions.
 * 
 * @author d3j766
 *
 * @see ComponentEvent
 * 
 */
@Inherited
@InterceptorBinding
@Retention(RUNTIME)
@Target({ TYPE })
public @interface ScheduledEventReporter {

	/**
	 * (Required) The {@code ScheduledEvent} reporting type.
	 * 
	 * see ScheduledEvent
	 * 
	 */
	Class<? extends ScheduledEvent> event();

	/**
	 * (Optional) Contains reporting schedule for the listed scheduled event.
	 * 
	 * The string represents a {@code ScheduleExpression}. All attributes should
	 * be delimited by a colon. All attributes should be included, even if
	 * empty. Attributes should be specified in the following order: <br>
	 * <ol>
	 * <li>second</li>
	 * <li>minute</li>
	 * <li>hour</li>
	 * <li>dayOfMonth</li>
	 * <li>month</li>
	 * <li>dayOfWeek</li>
	 * <li>year</li>
	 * </ol>
	 * <br>
	 * For example the following is a schedule for every 15 minutes for the
	 * hours of 1am and 2am, every Friday:<br>
	 * <b>:0/15:1,2:::Fri:<b>
	 * 
	 * @see ScheduleExpression
	 * 
	 */
	String schedule();
}